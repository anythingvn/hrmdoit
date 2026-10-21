# Dịch vụ chuyển .docx → .pdf (LibreOffice qua HTTP, cổng 3000, chỉ trong mạng compose).
# Thêm font Times New Roman (msttcorefonts) để PDF giữ đúng font của mẫu Word, không bị thay font khác.
# Ghim tag chính xác; cập nhật có chủ đích khi có bản vá.
FROM gotenberg/gotenberg:8.37.0
USER root
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
      sed -i 's/^Components: main$/Components: main contrib/' /etc/apt/sources.list.d/debian.sources; \
    else \
      sed -i 's/ main$/ main contrib/' /etc/apt/sources.list; \
    fi; \
    echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" | debconf-set-selections; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ttf-mscorefonts-installer; \
    fc-cache -f; \
    fc-list | grep -qi "Times New Roman"; \
    rm -rf /var/lib/apt/lists/*
USER gotenberg
