#!/usr/bin/env bash
# 재시작 방식 배포. SSM Run Command가 root로 실행한다: /opt/recipick/scripts/deploy.sh <이미지 태그(커밋 SHA)>
# 흐름: 잠금 → 배포 전 DB 덤프 → 레지스트리 로그인 → 이미지 pull → .env의 IMAGE_TAG 갱신 → app 재기동 → healthy 대기.
# 실패하면 이전 태그로 되돌린다. nginx는 업스트림 DNS를 다시 조회하므로 reload가 필요 없다.
# 블루그린을 도입하면 이 스크립트가 upstream.conf 교체를 맡는다.
set -euo pipefail

TAG="${1:?usage: deploy.sh <image-tag>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"
BACKUP_KEEP="${BACKUP_KEEP:-10}"

log() { printf '[deploy] %s %s\n' "$(date '+%F %T')" "$*"; }
# .env는 URL에 &가 있어 source하지 않는다
env_get() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }
# 파일을 바꿔치기하지 않고 내용만 덮어써 .env의 권한(600)을 유지한다
set_tag() { local tmp; tmp="$(mktemp)"; sed -E "s|^IMAGE_TAG=.*|IMAGE_TAG=$1|" .env > "$tmp" && cat "$tmp" > .env; rm -f "$tmp"; }
wait_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT)) st
    while [ "$SECONDS" -lt "$deadline" ]; do
        st="$(docker inspect -f '{{.State.Health.Status}}' recipick-app 2>/dev/null || echo missing)"
        [ "$st" = "healthy" ] && return 0
        sleep 5
    done
    return 1
}

# 동시 배포 방지. 워크플로의 concurrency와 이중 안전장치. flock이 없는 개발 머신에서는 잠금 없이 진행한다
if command -v flock >/dev/null 2>&1; then
    exec 9>.deploy.lock
    flock -n 9 || { log "다른 배포가 진행 중이라 중단"; exit 1; }
else
    log "flock이 없어 잠금 없이 진행(개발 머신)"
fi

IMAGE="$(env_get IMAGE)"
PREV_TAG="$(env_get IMAGE_TAG)"
[ -n "$IMAGE" ] || { log ".env에 IMAGE가 없다"; exit 1; }
[ "$TAG" != "$PREV_TAG" ] || log "같은 태그를 다시 배포: $TAG"

# 1. 배포 전 덤프. ddl-auto 변경은 롤백되지 않으므로 이 덤프가 유일한 안전망이다.
#    MySQL 컨테이너가 없으면(RDS 전환 후) 스냅샷으로 대체한다
mkdir -p backups
if docker inspect recipick-mysql >/dev/null 2>&1; then
    dump="backups/pre-deploy-$(date +%Y%m%d-%H%M%S)-${TAG:0:12}.sql.gz"
    docker exec recipick-mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
        | gzip > "$dump"
    log "덤프 완료: $dump ($(du -h "$dump" | cut -f1))"
    ls -1t backups/pre-deploy-*.sql.gz 2>/dev/null | tail -n +$((BACKUP_KEEP + 1)) | while read -r old; do rm -f "$old"; done
else
    log "MySQL 컨테이너가 없어 덤프를 건너뜀"
fi

# 2. 레지스트리 로그인. ECR이면 인스턴스 프로파일로, 그 외(GHCR 등)는 미리 docker login 해 둔다
registry="${IMAGE%%/*}"
if [[ "$registry" == *.amazonaws.com ]]; then
    region="$(printf '%s' "$registry" | sed -E 's/^[0-9]+\.dkr\.ecr\.([a-z0-9-]+)\.amazonaws\.com$/\1/')"
    aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry" >/dev/null
fi

# 3. 이미지 확보. 없으면 여기서 끝나고 서비스 중인 컨테이너는 그대로다
docker pull "$IMAGE:$TAG"

# 4. app만 재기동(재시작 방식). nginx는 처음 한 번 뜨고 그 뒤에는 설정이 바뀔 때만 재생성된다. MySQL·Redis는 건드리지 않는다
set_tag "$TAG"
docker compose up -d --no-deps app nginx
if wait_healthy; then
    log "배포 완료: $TAG"
    # 현재·이전 태그만 남기고 같은 저장소의 옛 이미지를 지운다(루트 볼륨 보호)
    docker images --format '{{.Repository}}:{{.Tag}}' "$IMAGE" \
        | grep -v -e ":$TAG\$" -e ":${PREV_TAG:-__none__}\$" | while read -r img; do docker rmi "$img" >/dev/null 2>&1 || true; done
    exit 0
fi

# 5. 실패. 로그를 남기고 이전 태그로 되돌린다
log "헬스 체크 실패(${HEALTH_TIMEOUT}초). 앱 로그 마지막 50줄:"
docker logs --tail 50 recipick-app 2>&1 || true
if [ -n "$PREV_TAG" ] && [ "$PREV_TAG" != "$TAG" ]; then
    log "이전 태그로 복구: $PREV_TAG"
    set_tag "$PREV_TAG"
    docker compose up -d --no-deps app
    if wait_healthy; then log "복구 완료: $PREV_TAG"; else log "복구도 실패. 수동 확인 필요"; fi
else
    log "되돌릴 이전 태그가 없다(첫 배포). 실패한 컨테이너를 그대로 둔다"
fi
exit 1
