#!/usr/bin/env bash
# 프론트 정적 파일 교체. 사용: deploy-web.sh <커밋 SHA> <빌드 묶음 tar.gz>
#
# 서버 배치(WEB_ROOT, 기본 /opt/recipick-web). nginx가 /srv/web으로 읽기 전용 마운트한다
#   releases/<SHA>/   커밋마다 푼 빌드 결과(frontend/dist)
#   current           nginx가 보는 링크. releases/<SHA>를 상대 경로로 가리킨다(컨테이너 안에서도 풀리게)
#
# /opt/recipick 밖에 두는 이유: 백엔드 배포가 rsync --delete로 /opt/recipick을 저장소 deploy/와 똑같이 맞춘다.
# 앱을 재시작하지 않고 링크만 바꾸므로 끊김이 없다. 되돌리기는 README 7절.
set -euo pipefail

main() {
    local SHA="${1:?사용: deploy-web.sh <커밋 SHA> <빌드 묶음>}"
    local BUNDLE="${2:?사용: deploy-web.sh <커밋 SHA> <빌드 묶음>}"
    ROOT="${DEPLOY_ROOT:-/opt/recipick}"
    WEB_ROOT="${WEB_ROOT:-/opt/recipick-web}"
    RELEASE_KEEP="${RELEASE_KEEP:-5}"

    acquire_lock
    mkdir -p "$WEB_ROOT/releases"
    local rel="$WEB_ROOT/releases/$SHA"

    if [ -d "$rel" ]; then
        # 같은 커밋을 다시 배포(수동 재실행)할 때다. 지금 nginx가 보는 폴더일 수 있어 지우고 다시 풀지 않는다.
        # 이 폴더는 검사를 통과한 묶음만 이름을 바꿔 만들므로 그대로 쓴다
        log "releases/$SHA가 이미 있어 다시 풀지 않는다"
    else
        # 다 풀고 검사한 뒤에만 이름을 바꾼다. 중간에 실패해도 current는 이전 버전 그대로다
        rm -rf "$rel.tmp" && mkdir -p "$rel.tmp"
        tar -xzf "$BUNDLE" -C "$rel.tmp"
        [ -f "$rel.tmp/index.html" ] || { log "빌드 묶음에 index.html이 없어 중단"; rm -rf "$rel.tmp"; exit 1; }
        mv "$rel.tmp" "$rel"
    fi

    local prev=""
    [ -L "$WEB_ROOT/current" ] && prev="$(basename "$(readlink "$WEB_ROOT/current")")"

    switch_to "$SHA"
    log "current: ${prev:-없음} → $SHA"

    # 마운트가 빠졌거나 nginx가 다른 경로를 보면 파일은 바뀌어도 화면은 그대로다. nginx 컨테이너 안에서 확인한다
    if ! docker exec recipick-nginx test -f /srv/web/current/index.html; then
        log "nginx 컨테이너에서 /srv/web/current/index.html이 보이지 않는다. compose의 /opt/recipick-web 마운트를 확인한다"
        # 실패한 배포가 current로 남으면 다음 배포가 이것을 직전 버전(되돌리기 대상)으로 잡는다
        if [ -n "$prev" ]; then switch_to "$prev"; log "current를 $prev로 되돌림"; fi
        exit 1
    fi

    cleanup "$SHA" "$prev"
}

# ln -sfn은 지우고 다시 만드는 사이에 링크가 없는 순간이 있다. 새 링크를 만든 뒤 rename으로 한 번에 바꾼다
switch_to() {
    ln -sfn "releases/$1" "$WEB_ROOT/current.tmp"
    mv -T "$WEB_ROOT/current.tmp" "$WEB_ROOT/current"
}

log() { printf '[deploy-web] %s %s\n' "$(date '+%F %T')" "$*"; }

acquire_lock() {
    # 워크플로의 SSM 명령이 다운로드 전에 잡은 잠금(fd 9)을 물려받으면 다시 잡지 않는다
    if [ -n "${DEPLOY_LOCK_HELD:-}" ]; then return 0; fi
    exec 9>"$ROOT/.deploy.lock"
    # 인증서 갱신 cron도 같은 잠금을 잡으므로 기다린다(갱신 cron과 같은 10분)
    flock -w 600 9 || { log "잠금을 10분 안에 얻지 못해 중단"; exit 1; }
}

# 최근 RELEASE_KEEP개만 남긴다. 지금 버전과 직전 버전(되돌리기 대상)은 개수와 상관없이 남긴다
cleanup() {
    local cur="$1" prev="$2" rel
    ls -1t "$WEB_ROOT/releases" | tail -n +"$((RELEASE_KEEP + 1))" | while read -r rel; do
        [ "$rel" = "$cur" ] || [ "$rel" = "$prev" ] || rm -rf "${WEB_ROOT:?}/releases/$rel"
    done || log "경고: 이전 빌드 정리 실패"
}

main "$@"
