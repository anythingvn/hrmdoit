FROM postgres:16.4
RUN apt-get update \
    && apt-get install -y --no-install-recommends rsync openssh-client \
    && rm -rf /var/lib/apt/lists/*
COPY backup.sh backup-loop.sh /ops/backup/
RUN chmod 0755 /ops/backup/backup.sh /ops/backup/backup-loop.sh
ENTRYPOINT []
CMD ["/ops/backup/backup-loop.sh"]
