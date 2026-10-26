# Cùng bản PostgreSQL với service db (pg_dump phải >= bản server). age: mã hóa bản đẩy ra ngoài VPS. rclone: đẩy lên
# Google Drive (BACKUP_OFFSITE_RCLONE); ca-certificates cho kết nối HTTPS của rclone.
FROM postgres:16.15-bookworm
RUN apt-get update \
    && apt-get install -y --no-install-recommends rsync openssh-client age rclone ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY backup.sh backup-loop.sh /ops/backup/
RUN chmod 0755 /ops/backup/backup.sh /ops/backup/backup-loop.sh
ENTRYPOINT []
CMD ["/ops/backup/backup-loop.sh"]
