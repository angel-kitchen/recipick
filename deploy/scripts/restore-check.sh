#!/usr/bin/env bash
# 덤프가 실제로 복원되는지 운영 DB와 분리된 임시 MySQL에서 확인한다. 사용: restore-check.sh <덤프 .sql.gz>
# 임시 컨테이너는 볼륨·포트 없이 띄우고 끝나면 지운다. 운영 MySQL에는 접속하지 않는다.
set -euo pipefail

DUMP="${1:?사용: restore-check.sh <덤프 .sql.gz>}"
NAME="recipick-restore-check-$$"
PW="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
log() { printf '[restore-check] %s %s\n' "$(date '+%F %T')" "$*"; }
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT

[ -s "$DUMP" ] || { log "파일이 없거나 비었다: $DUMP"; exit 1; }
gzip -t "$DUMP"

# 운영 서버에서 돌려도 앱과 메모리를 다투지 않게 작은 버퍼 풀로 띄운다
docker run -d --name "$NAME" -e MYSQL_ROOT_PASSWORD="$PW" -e MYSQL_DATABASE=restore_check \
    --memory 768m mysql:8.4 --innodb-buffer-pool-size=128M --skip-log-bin >/dev/null
for _ in $(seq 1 60); do
    docker exec -e MYSQL_PWD="$PW" "$NAME" mysql -uroot -e 'SELECT 1' >/dev/null 2>&1 && break
    sleep 2
done

log "복원 시작: $DUMP"
gzip -dc "$DUMP" | docker exec -i -e MYSQL_PWD="$PW" "$NAME" mysql -uroot restore_check
tables="$(docker exec -e MYSQL_PWD="$PW" "$NAME" mysql -uroot -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='restore_check'")"
[ "$tables" -gt 0 ] || { log "복원 후 테이블이 없다"; exit 1; }
log "복원 성공: 테이블 ${tables}개. 주요 테이블 행 수:"
docker exec -e MYSQL_PWD="$PW" "$NAME" mysql -uroot -t restore_check -e \
    "SELECT table_name, table_rows FROM information_schema.tables
     WHERE table_schema='restore_check' ORDER BY table_rows DESC LIMIT 10"
