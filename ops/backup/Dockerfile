# Cùng bản PostgreSQL với service db (pg_dump phải >= bản server). age: mã hóa bản đẩy ra ngoài VPS. rclone: đẩy lên
# Google Drive (BACKUP_OFFSITE_RCLONE); ca-certificates cho kết nối HTTPS của rclone.
FROM postgres:16.15-bookworm
RUN apt-get update \
    && apt-get install -y --no-install-recommends rsync openssh-client age rclone ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# Volume backup-config dùng chung với app (user hrm, uid/gid 1001, ghi được). Container nào chạy trước sẽ tạo volume và
# Docker chép quyền sở hữu của thư mục này vào volume rỗng, nên phải thuộc 1001:1001 để app luôn ghi được.
RUN mkdir -p /data/backup-config && chown 1001:1001 /data/backup-config
COPY backup.sh backup-loop.sh /ops/backup/
RUN chmod 0755 /ops/backup/backup.sh /ops/backup/backup-loop.sh
ENTRYPOINT []
CMD ["/ops/backup/backup-loop.sh"]
