#!/usr/bin/env bash
# MySQL 덤프. 사용: backup.sh <pre-deploy|nightly> [이름 꼬리표]
#   pre-deploy  배포 스크립트가 배포 전에 부른다. 로컬에만 두고 최근 PRE_DEPLOY_KEEP개(기본 10)를 남긴다
#   nightly     cron이 부른다. S3(BACKUP_BUCKET)에 올리고 업로드 크기를 확인한다. 로컬은 최근 NIGHTLY_KEEP개(기본 7)
# 실패하면 0이 아닌 코드로 끝나고, .env에 ALERT_WEBHOOK_URL이 있으면 그 주소로 알린다(Discord·Slack 웹훅 형식).
set -Eeuo pipefail
umask 077
# cron의 PATH는 /usr/bin:/bin뿐이라 snap으로 깐 aws를 찾지 못한다
export PATH="$PATH:/usr/local/bin:/snap/bin"

main() {
    KIND="${1:?사용: backup.sh <pre-deploy|nightly> [꼬리표]}"
    local LABEL="${2:-}"
    ROOT="${DEPLOY_ROOT:-/opt/recipick}"
    cd "$ROOT"
    case "$KIND" in
        pre-deploy) KEEP="${PRE_DEPLOY_KEEP:-10}" ;;
        nightly) KEEP="${NIGHTLY_KEEP:-7}" ;;
        *) echo "알 수 없는 종류: $KIND" >&2; exit 2 ;;
    esac
    trap 'on_error "$BASH_COMMAND"' ERR

    mkdir -p backups
    chmod 700 backups

    # 처음 배포하는 서버처럼 MySQL이 아직 없으면 배포 전 덤프는 할 것이 없다.
    # nightly는 운영 중에 도는 작업이라 컨테이너가 없다는 것 자체가 장애이므로 조용히 넘기지 않는다
    if ! docker inspect recipick-mysql >/dev/null 2>&1; then
        [ "$KIND" = nightly ] && fail "MySQL 컨테이너(recipick-mysql)가 없어 nightly 백업을 하지 못했다"
        log "MySQL 컨테이너가 없어 덤프를 건너뛴다"
        return 0
    fi

    local name="$KIND-$(date +%Y%m%d-%H%M%S)${LABEL:+-$LABEL}.sql.gz"
    TMP="$(mktemp backups/.tmp-XXXXXX)"
    # 비밀번호는 컨테이너 안의 환경 변수로만 쓴다(명령줄·로그에 남기지 않는다)
    docker exec recipick-mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
        | gzip > "$TMP"
    # mysqldump는 끝까지 쓴 경우에만 마지막 줄에 완료 표시를 남긴다. 중간에 끊긴 덤프를 성공으로 치지 않는다
    gzip -dc "$TMP" | tail -n 1 | grep -q '^-- Dump completed' || fail "덤프가 끝까지 기록되지 않았다"
    mv "$TMP" "backups/$name"
    TMP=""
    log "덤프 저장: backups/$name ($(du -h "backups/$name" | cut -f1))"

    if [ "$KIND" = nightly ]; then upload "backups/$name"; fi

    ls -1t backups/"$KIND"-*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do rm -f "$old"; done
}

log() { printf '[backup] %s %s\n' "$(date '+%F %T')" "$*"; }
env_get() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' || true; }

upload() {
    local file="$1" bucket; bucket="$(env_get BACKUP_BUCKET)"
    [ -n "$bucket" ] || fail ".env에 BACKUP_BUCKET이 없어 S3에 올리지 못했다"
    local key="backups/$(basename "$file")"
    aws s3 cp --only-show-errors "$file" "s3://$bucket/$key"
    # 올라간 크기가 로컬과 같은지 확인한다
    local remote local_size
    remote="$(aws s3api head-object --bucket "$bucket" --key "$key" --query ContentLength --output text)"
    local_size="$(wc -c < "$file" | tr -d ' ')"
    [ "$remote" = "$local_size" ] || fail "S3 크기 불일치(로컬 $local_size, S3 $remote)"
    log "S3 업로드 확인: s3://$bucket/$key"
}

fail() { log "실패: $*"; notify "$*"; exit 1; }

on_error() {
    trap - ERR
    fail "${KIND:-backup} 백업 중 명령 실패: ${1%% *}"
}

notify() {
    local url; url="$(env_get ALERT_WEBHOOK_URL)"
    [ -n "$url" ] || return 0
    local msg="[recipick] $(hostname) 백업 실패: $1"
    curl -fsS -m 10 -H 'Content-Type: application/json' \
        -d "$(printf '{"content":"%s","text":"%s"}' "$msg" "$msg")" "$url" >/dev/null || log "알림 전송 실패"
}

cleanup_tmp() { [ -n "${TMP:-}" ] && rm -f "$TMP"; return 0; }
trap cleanup_tmp EXIT

main "$@"
