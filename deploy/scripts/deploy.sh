#!/usr/bin/env bash
# 재시작 방식 배포. 사용: deploy.sh <이미지 태그(커밋 SHA)>
#
# 서버 배치(DEPLOY_ROOT, 기본 /opt/recipick)
#   .env, certbot/, backups/      서버에만 있고 배포가 건드리지 않는다
#   releases/<태그>/              태그마다 받은 deploy/ 묶음. 롤백 때 그 태그의 설정을 다시 깐다.
#                                 묶음 없이 돌던 태그는 배포 직전 운영 설정을 그 이름으로 남긴다
#   compose*.yml, nginx/, ...     현재 태그의 묶음을 그대로 복사한 운영 설정
#
# 흐름: 잠금 → .env 검사 → MySQL·Redis 준비 대기 → 배포 전 덤프 → 이미지 pull
#       → 설정 교체 → 앱·nginx 기동 → nginx 설정 검사·reload → 앱 healthy → nginx 경유 응답
#       → (실패 시) 이전 태그의 이미지와 설정으로 같은 절차를 다시 밟는다
#
# 본문을 main 함수로 감싼다. bash는 함수를 끝까지 읽은 뒤 실행하므로, 실행 중에 scripts/가 교체되어도 안전하다.
set -euo pipefail
# cron·SSM의 PATH에는 /snap/bin(snap으로 깐 aws)과 /usr/local/bin(공식 설치 프로그램)이 없을 수 있다
export PATH="$PATH:/usr/local/bin:/snap/bin"

main() {
    local TAG="${1:?사용: deploy.sh <이미지 태그>}"
    ROOT="${DEPLOY_ROOT:-/opt/recipick}"
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"
    INFRA_TIMEOUT="${INFRA_TIMEOUT:-120}"
    RELEASE_KEEP="${RELEASE_KEEP:-5}"
    cd "$ROOT"

    acquire_lock
    check_env

    local PREV_TAG; PREV_TAG="$(env_get IMAGE_TAG)"
    local IMAGE; IMAGE="$(env_get IMAGE)"

    ensure_infra
    "$SCRIPT_DIR/backup.sh" pre-deploy "${TAG:0:12}"

    # 이미지가 없으면 여기서 끝난다. 아직 아무것도 바꾸지 않았으므로 서비스는 그대로다
    ecr_login "$IMAGE"
    docker pull "$IMAGE:$TAG"
    snapshot_config "$PREV_TAG" "$TAG"

    if activate "$TAG"; then
        log "배포 완료: $TAG"
        cleanup "$IMAGE" "$TAG" "$PREV_TAG"
        exit 0
    fi

    log "배포 실패: $TAG. 앱 로그 마지막 50줄:"
    docker logs --tail 50 recipick-app 2>&1 || true
    if [ -z "$PREV_TAG" ] || [ "$PREV_TAG" = "$TAG" ]; then
        log "되돌릴 이전 태그가 없다(첫 배포 또는 같은 태그 재배포). 수동 확인 필요"
        exit 1
    fi
    log "이전 태그로 복구: $PREV_TAG"
    if activate "$PREV_TAG"; then
        log "복구 완료: $PREV_TAG. DB 스키마는 되돌아가지 않았다(README '스키마 복구')"
    else
        log "복구도 실패. 수동 확인 필요"
    fi
    exit 1
}

log() { printf '[deploy] %s %s\n' "$(date '+%F %T')" "$*"; }
warn() { log "경고: $*"; }

# .env는 셸로 source하지 않는다(JDBC URL의 & 때문에 깨진다). 값의 바깥 큰따옴표만 벗긴다
env_get() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' || true; }

acquire_lock() {
    # 워크플로의 SSM 명령이 다운로드 전에 잡은 잠금(fd 9)을 물려받으면 다시 잡지 않는다
    if [ -n "${DEPLOY_LOCK_HELD:-}" ]; then return 0; fi
    if ! command -v flock >/dev/null 2>&1; then
        log "flock이 없어 잠금 없이 진행(개발 머신)"
        return 0
    fi
    exec 9>"$ROOT/.deploy.lock"
    flock -n 9 || { log "다른 배포가 진행 중이라 중단"; exit 1; }
}

check_env() {
    [ -f .env ] || { log ".env가 없다. .env.example을 복사해 채운다"; exit 1; }
    local missing="" key
    for key in IMAGE SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
               JWT_SECRET TRIAL_BLOCK_HMAC_SECRET PORTONE_API_SECRET MYSQL_ROOT_PASSWORD MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD; do
        [ -n "$(env_get "$key")" ] || missing="$missing $key"
    done
    [ -z "$missing" ] || { log ".env에 비어 있는 필수 값:$missing"; exit 1; }
}

container_health() { docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null || echo missing; }

wait_healthy() {
    local name="$1" timeout="$2" deadline=$((SECONDS + $2))
    while [ "$SECONDS" -lt "$deadline" ]; do
        [ "$(container_health "$name")" = "healthy" ] && return 0
        sleep 3
    done
    log "$name 이(가) ${timeout}초 안에 healthy가 되지 않았다(현재 $(container_health "$name"))"
    return 1
}

ensure_infra() {
    # 처음에는 MySQL·Redis를 띄운다. 이미 있으면 재생성하지 않는다(infra 설정 변경은 README 절차로 수동 적용)
    local file="compose.infra.yml"
    [ -f "$file" ] || file="$SCRIPT_DIR/../compose.infra.yml"
    docker compose -f "$file" --project-directory "$ROOT" up -d --no-recreate
    # 첫 실행의 MySQL은 초기화에 수십 초가 걸린다. 준비되기 전에 덤프하거나 앱을 띄우지 않는다
    wait_healthy recipick-mysql "$INFRA_TIMEOUT" || exit 1
    wait_healthy recipick-redis "$INFRA_TIMEOUT" || exit 1
}

ecr_login() {
    local registry="${1%%/*}" region
    [[ "$registry" == *.amazonaws.com ]] || return 0
    region="$(printf '%s' "$registry" | sed -E 's/^[0-9]+\.dkr\.ecr\.([a-z0-9-]+)\.amazonaws\.com$/\1/')"
    aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry" >/dev/null
}

set_tag() {
    # 같은 디렉터리의 임시 파일(mktemp는 권한 600)에 다 쓴 뒤 mv로 바꾼다. rename은 원자적이라
    # 쓰기 도중 디스크가 차도 비밀값이 든 .env가 반만 남지 않는다. .env는 바인드 마운트가 아니라 inode가 바뀌어도 된다
    local tmp; tmp="$(mktemp "$ROOT/.env.tmp-XXXXXX")"
    if grep -qE '^IMAGE_TAG=' .env; then
        sed -E "s|^IMAGE_TAG=.*|IMAGE_TAG=$1|" .env > "$tmp" || { rm -f "$tmp"; return 1; }
    else
        { cat .env && printf 'IMAGE_TAG=%s\n' "$1"; } > "$tmp" || { rm -f "$tmp"; return 1; }
    fi
    grep -qxF "IMAGE_TAG=$1" "$tmp" || { rm -f "$tmp"; log ".env 갱신 실패"; return 1; }
    chmod 600 "$tmp"
    mv -f "$tmp" .env
}

# 운영 설정에서 서버에만 있는 것. 묶음과 맞추거나 스냅샷을 뜰 때 건드리지 않는다
CONFIG_EXCLUDES=(--exclude=/.env --exclude=/certbot/ --exclude=/backups/ --exclude=/releases/
                 --exclude=/.deploy.lock --exclude='/.env.tmp-*')

snapshot_config() {
    # 이미지만 바꾼 수동 롤백처럼 묶음 없이 돌던 태그로 복구하면 install_config가 방금 깐 실패 태그의 설정을 그대로 쓴다.
    # 설정을 바꾸기 전에 지금 그 태그와 함께 동작하던 운영 설정을 묶음 자리에 남겨 복구 때 이것을 깐다
    local prev="$1" tag="$2"
    [ -n "$prev" ] && [ "$prev" != "$tag" ] && [ ! -d "$ROOT/releases/$prev" ] || return 0
    local tmp; tmp="$(mktemp -d "$ROOT/releases/.snapshot-XXXXXX")"
    rsync -a "${CONFIG_EXCLUDES[@]}" "$ROOT/" "$tmp/"
    mv "$tmp" "$ROOT/releases/$prev"
    log "releases/$prev 묶음이 없어 현재 운영 설정을 그 이름으로 남겼다(복구용)"
}

install_config() {
    # 그 태그의 묶음과 운영 설정을 정확히 같게 만든다. 저장소에서 지우거나 이름을 바꾼 파일도 서버에서 사라진다.
    # --inplace: nginx·prometheus는 파일·디렉터리를 바인드 마운트하므로 inode를 바꾸면 컨테이너가 옛 파일을 계속 본다
    local rel="$ROOT/releases/$1"
    if [ ! -d "$rel" ]; then
        warn "releases/$1 묶음이 없어 현재 설정을 그대로 쓴다"
        return 0
    fi
    rsync -a --delete --inplace "${CONFIG_EXCLUDES[@]}" "$rel/" "$ROOT/"
}

wait_nginx() {
    # 방금 생성된 nginx는 master가 PID 파일을 쓰기 전이라 reload가 "invalid PID"로 실패한다(첫 배포, nginx 재생성)
    local i
    for i in $(seq 1 20); do
        docker exec recipick-nginx test -s /run/nginx.pid 2>/dev/null && return 0
        sleep 1
    done
    log "nginx가 20초 안에 준비되지 않았다"
    return 1
}

proxy_check() {
    # 앱 컨테이너가 healthy여도 nginx 경유 응답이 실패할 수 있다(설정 오류, 업스트림 조회 실패, 인증서).
    # 리다이렉트를 따라가 최종 응답이 200일 때만 성공이다. HTTPS 전환 뒤 http 주소는 301을 주는데,
    # 301을 성공으로 치면 HTTPS 블록이나 인증서가 깨져도 통과한다
    local url; url="$(env_get PROXY_CHECK_URL)"; url="${url:-http://127.0.0.1/api/categories}"
    local i code=""
    for i in 1 2 3 4 5; do
        code="$(curl -sS -L --max-redirs 3 -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>&1)" || true
        [ "$code" = "200" ] && return 0
        sleep 2
    done
    log "nginx 경유 응답 실패: $url (마지막 결과 ${code:-없음})"
    return 1
}

activate() {
    # if 조건 안에서 불리는 함수는 set -e가 꺼지므로 단계마다 실패를 직접 돌려준다
    local tag="$1"
    install_config "$tag" || return 1
    set_tag "$tag" || return 1
    docker compose config -q || { log "compose 설정 검사 실패"; return 1; }
    docker compose up -d --no-deps app nginx || { log "컨테이너 기동 실패"; return 1; }
    # conf.d는 바인드 마운트라 파일이 바뀌어도 컨테이너가 재생성되지 않는다. 검사 후 reload해야 반영된다
    docker exec recipick-nginx nginx -t -q || { log "nginx 설정 검사 실패"; return 1; }
    wait_nginx || return 1
    docker exec recipick-nginx nginx -s reload || { log "nginx reload 실패"; return 1; }
    wait_healthy recipick-app "$HEALTH_TIMEOUT" || return 1
    proxy_check || return 1
}

cleanup() {
    # 정리는 배포 성공과 무관하다. 실패해도 경고만 남긴다
    local image="$1" tag="$2" prev="${3:-}" img rel
    docker images --format '{{.Repository}}:{{.Tag}}' "$image" 2>/dev/null \
        | { grep -v -e ":$tag\$" -e ":${prev:-__none__}\$" || true; } \
        | while read -r img; do
            docker rmi "$img" >/dev/null 2>&1 || warn "이미지 삭제 실패: $img"
        done || warn "이미지 정리 실패"
    # 묶음은 최근 RELEASE_KEEP개와 현재·이전 태그를 남긴다
    ls -1t "$ROOT/releases" 2>/dev/null | tail -n +$((RELEASE_KEEP + 1)) \
        | while read -r rel; do
            [ "$rel" = "$tag" ] || [ "$rel" = "$prev" ] || rm -rf "${ROOT:?}/releases/$rel"
        done || warn "묶음 정리 실패"
}

main "$@"
