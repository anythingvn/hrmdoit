# syntax=docker/dockerfile:1
# ---- Giai đoạn build: Maven + npm (qua frontend-maven-plugin) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY core ./core
COPY app ./app
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl app -am -Pprod -DskipTests package \
    && cp app/target/hrm-*.jar /tmp/app.jar

# ---- Giai đoạn chạy: JRE, user không phải root ----
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 1001 hrm \
    && useradd --system --uid 1001 --gid hrm --home-dir /app --shell /usr/sbin/nologin hrm \
    && mkdir -p /app /data/files \
    && chown -R hrm:hrm /app /data/files
WORKDIR /app
COPY --from=build --chown=hrm:hrm /tmp/app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_ROOT=/data/files \
    BACKUP_STATUS_FILE=/data/backup-status/status.json \
    TZ=Asia/Ho_Chi_Minh
USER hrm
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Ho_Chi_Minh", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
