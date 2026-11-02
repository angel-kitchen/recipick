# 배포 구현 기록 — 무엇을 만들었고 왜 그렇게 했는가

작성일 2026-10-26 · 검토 반영 2026-10-27 · 브랜치 `feature/global-deploy` · 선택 근거는 `docs/deploy-strategy.md`, 실행 절차는 `deploy/README.md`

> 목적: 배포·CI/CD(필수 5)를 구현하면서 내린 결정과 그 이유, 로컬에서 검증한 결과를 남긴다. 평가가 요구하는 "기술 선택 근거"와 "트러블슈팅 기록"의 배포 부분이다.

## 1. 배경과 결정 경위

10/26 시점의 저장소에는 배포와 관련된 것이 `./gradlew test`만 실행하는 CI 워크플로뿐이었다. Dockerfile, 운영 프로필, 서버 구성, 배포 파이프라인이 모두 없었고, 기본 프로필이 local이라 운영 설정도 따로 만들어야 했다. 제출은 11/12이고 부하 테스트·캐싱·모니터링이 모두 배포 환경을 전제하므로, 배포는 가장 먼저 끝내야 하는 항목이었다.

구성은 서버 1대에 Docker Compose로 올리고 dev 병합마다 자동 배포하다가 11/9에 main으로 승격하는 방식으로 정했다. 분리 구성(RDS, ALB, ECS)이 주는 가용성과 자동 확장은 평가 항목이 아니고, 그 설정에 쓰는 시간이 평가 항목인 부하 테스트·캐싱·모니터링에서 빠지기 때문이다. 파이프라인 인증은 GitHub OIDC로 AWS 역할을 가정하고, 서버 접속은 SSH 대신 SSM을 쓰기로 했다. 장기 비밀을 어디에도 두지 않고 22번 포트를 열지 않기 위해서다. 무중단 배포(nginx 블루그린)는 초안 검토에서 "선택한 도전 항목은 429이고 배포 고도화는 후순위"라는 지적을 받아, 재시작 방식 파이프라인을 먼저 완성하고 블루그린은 필수 항목 착수 후 여유가 있을 때 올리는 사다리 1단계로 미뤘다.

업무 전달 문서와 대조해 담당 경계도 정리했다. 배포는 앱 코드와 인프라 모두 한 사람이 맡고, Actuator·지표·대시보드는 모니터링 담당이 맡는다. 그래서 헬스 체크는 Actuator가 병합되기 전까지 공개 GET인 `/api/categories`를 쓰고, 경로를 설정값으로 빼 두었다. 10/26 #190이 병합되어 관리 포트 8081의 `/actuator/health`로 바꿨고, 관리 포트가 UP이어도 앱 포트 수신을 보장하지 않는다는 모니터링 문서의 지적대로 `/api/categories`도 함께 본다. 설정값은 더 필요 없어 없앴다.

## 2. 앱 이미지 (`Dockerfile`, `.dockerignore`)

이미지는 CI가 만든 실행 jar를 담는 얇은 이미지로 했다. 서버에서 빌드하지 않아야 같은 이미지가 나중에 ECS에서도 돌고, Gradle 캐시가 있는 CI가 서버보다 빠르다. 업무 전달의 멀티스테이지 초안은 로컬 검증용으로는 좋지만 CI에서 매번 의존성을 다시 받는다.

베이스는 Ubuntu 기반 Temurin 17 JRE다. 헬스 체크용 curl과 시간대 데이터가 들어 있어 추가 설치가 없다. 453MB로 가볍지는 않지만 서버에서는 베이스 레이어가 캐시되어 배포마다 jar 레이어 70MB만 받는다. 컨테이너는 비root 사용자(uid 1001)로 돌린다.

JVM 옵션으로 `-Duser.timezone=Asia/Seoul`을 명시했다. 컨벤션이 시각을 한국 시간 `LocalDateTime`으로 저장하는데, `TZ` 환경 변수만으로는 JVM 기본 시간대가 보장되지 않기 때문이다. 힙 상한은 `-XX:MaxRAMPercentage=75`로 두어 compose의 메모리 제한을 따라가게 했다. `.dockerignore`는 jar 외 전부를 제외해 소스와 비밀 파일이 빌드 컨텍스트에 들어가지 않게 한다.

## 3. 운영 프로필 (`application-prod.yml`)

접속 정보와 비밀은 전부 환경 변수로 받는다. Spring의 완화 바인딩 덕에 `SPRING_DATASOURCE_URL` 같은 이름은 설정 파일에 적지 않아도 바인딩되므로, 파일에는 환경에 따라 바뀌지 않는 값만 남겼다.

- **락 대기 시간.** `SET SESSION innodb_lock_wait_timeout = 5`를 Hikari 초기화 SQL로 넣었다. 로컬 예시에만 있던 값이라 빠지면 락 실패 시 `COMMON-010` 5초 규칙이 깨진다. 운영 MySQL 컨테이너에도 같은 값을 옵션으로 두었다.
- **`ddl-auto: update`.** DDL·마이그레이션 파일이 없어 첫 배포부터 앱이 스키마를 만들어야 한다. 지정하지 않으면 테이블이 없어 시드 SQL이 실패하고 기동이 멈춘다. 환경 변수가 아니라 파일에 박은 이유는, 빠뜨리면 기동이 실패하는 값이기 때문이다.
- **`forward-headers-strategy: native`.** 리버스 프록시 뒤에서 앱이 보는 접속 IP는 도커 내부 주소다. Tomcat의 기본 내부 프록시 범위가 도커 대역을 포함하므로, 프록시가 `X-Forwarded-For`를 자기 접속 주소로 덮어쓰면 감사 IP와 나중의 429 요청 제한이 실제 클라이언트 주소를 받는다.
- **graceful shutdown.** 재시작 배포 때 진행 중 요청을 끝내고 내린다. 상한은 20초다.

프로필 전용 파일에 `spring.config.activate.on-profile`을 적으면 기동 오류가 나므로 적지 않았다. PortOne의 API Secret은 `@NotBlank`라 값이 없으면 기동 자체를 거부하는데, 이는 로컬에서 재현해 확인했다. 결제 검증 전까지는 임시 값으로 띄운다.

## 4. 서버 구성 (`deploy/compose*.yml`)

compose 파일은 역할별로 셋이다. 앱과 프록시(`compose.yml`), MySQL과 Redis(`compose.infra.yml`), 모니터링 스켈레톤(`compose.monitoring.yml`). 파일마다 compose 프로젝트 이름이 다르고, 미리 만든 외부 네트워크 `recipick`을 공유한다. 배포 명령이 읽는 파일은 `compose.yml`뿐이라 어떤 실수로도 DB 컨테이너를 재생성하거나 지울 수 없고, RDS로 옮길 때는 인프라 파일을 지우고 접속 정보만 바꾸면 된다.

MySQL·Redis·앱은 호스트에 포트를 노출하지 않는다. 밖으로 열린 것은 nginx의 80·443뿐이다. 앱 컨테이너에는 `.env`의 값 중 앱이 쓰는 것만 명시적으로 넘겨서 MySQL 루트 비밀번호 같은 값이 앱 프로세스 환경에 들어가지 않게 했다. 메모리 상한은 앱 1280m, MySQL 1g(버퍼 풀 512MB), Redis 256m으로, 나중에 블루그린 교체 순간 앱이 둘 떠도 4GB 인스턴스 안에 들어오는 크기다. 모든 서비스에 `restart: unless-stopped`와 json-file 로그 로테이션(10MB × 3)을 두어 루트 볼륨이 로그로 차지 않게 했다. MySQL은 복제도 시점 복구도 쓰지 않으므로 binlog를 꺼서 디스크와 쓰기 비용을 줄였다. 백업은 덤프로 한다.

## 5. 프록시 (`deploy/nginx/`)

nginx를 앞에 두는 이유는 TLS 종단과, 나중에 블루그린의 전환 장치로 쓰기 위해서다. 설정은 세 조각이다.

- `upstream.conf`는 업스트림 하나만 정의한다. `server app:8080 resolve;`로 앱 컨테이너가 재생성되어 IP가 바뀌어도 nginx가 도커 DNS를 다시 조회한다. 이 한 줄 덕에 앱 IP가 바뀌어도 nginx가 따라간다. 다만 conf.d의 파일 변경은 reload해야 반영되므로 배포 스크립트가 `nginx -t` 후 reload한다(6절). 블루그린을 도입하면 이 파일의 server 줄만 바꾼다.
- `app.conf`는 HTTP 단계의 서버 블록이고, `app-https.conf.disabled`는 HTTPS 단계의 블록이다. 10/30에 도메인을 넣고 확장자만 서로 바꾸면 된다. ACME 검증 경로는 둘 다 있어 certbot webroot 발급이 어느 단계에서든 된다.
- `proxy_params`는 전달 헤더를 모아 둔 것이다. 클라이언트가 보낸 `X-Forwarded-For`는 버리고 nginx가 본 주소로 덮어써서 위조가 불가능하다.

conf.d 디렉터리를 통째로 마운트해 이미지의 기본 설정(`default.conf`)을 가렸다. 그렇지 않으면 IP로 접근할 때 nginx 환영 페이지가 앱을 가로챈다. certbot은 compose 프로필로 두어 평소에는 뜨지 않고 발급·갱신 때만 실행한다.

## 6. 배포 스크립트와 워크플로

파이프라인은 기존 `ci.yml`의 `test` 작업 뒤에 `deploy` 작업을 붙였다. 룰셋이 요구하는 상태 검사 `test`는 그대로다. `deploy`는 push 이벤트이면서 브랜치 이름이 레포 변수 `DEPLOY_BRANCH`와 같을 때만 돈다. 11/9에 변수를 dev에서 main으로 바꾸면 그날부터 dev 병합은 배포되지 않는다. 동결을 약속이 아니라 설정으로 만든 것이다.

작업 순서는 jar 빌드, OIDC로 역할 가정, ECR 로그인, 이미지 빌드와 push(태그는 커밋 SHA), `deploy/` 디렉터리를 S3에 업로드, SSM Run Command로 서버 잠금을 잡고 묶음을 `releases/<SHA>`에 푼 뒤 그 묶음의 `scripts/deploy.sh <SHA>` 실행, 완료 대기와 출력 수집이다. 서버 설정 파일을 S3로 보내는 이유는 SSH가 없어 scp를 쓸 수 없고, 서버 설정도 git이 원본이어야 "코드 변경 자동 반영"에 설정 변경이 포함되기 때문이다. `.env`·인증서·덤프·묶음은 업로드에서 제외하고 서버에서도 보존한다. 마지막 단계가 SSM 명령의 표준 출력과 오류를 Actions 로그에 붙이므로 AWS 권한이 없는 팀원도 실패 원인을 GitHub에서 본다. 서버가 한 대라 `concurrency` 그룹으로 배포가 한 번에 하나만 돌게 했다. 시크릿은 하나도 없고 변수 여섯 개만 있다.

`deploy.sh`는 재시작 방식이다. 10/27 검토를 반영해 다음 순서로 다시 썼다.

1. 잠금. 워크플로의 SSM 명령이 **다운로드 전에** flock을 잡고 배포 스크립트에 물려준다. 설정 교체·기동·검증·복구가 끝날 때까지 유지된다. 서버에서 수동으로 돌리면 스크립트가 직접 잡는다. 다운로드 임시 파일은 배포마다 따로 만들고 끝나면 지운다.
2. `.env` 필수 값 검사. 비어 있으면 이름을 대고 아무것도 바꾸지 않은 채 멈춘다. compose 파일에도 `${VAR:?}`를 두어 직접 실행할 때도 같은 오류가 난다.
3. MySQL·Redis 준비 대기. 없으면 띄우고(`--no-recreate`), healthy가 될 때까지 기다린다. 첫 서버의 MySQL은 초기화에 수십 초가 걸려 그전에 덤프하면 실패했다.
4. 배포 전 덤프. `scripts/backup.sh`로 분리했고 nightly 백업도 같은 스크립트를 쓴다.
5. 이미지 pull. 여기까지는 운영 상태를 하나도 바꾸지 않는다.
6. 활성화. 설정 교체 → 태그 갱신 → compose 검사 → app·nginx 기동 → `nginx -t` → reload → 앱 healthy → nginx 경유 응답. 이 중 하나라도 실패하면 **이전 태그의 이미지와 이전 태그의 설정 묶음**으로 6을 그대로 다시 밟는다. 복구도 같은 검사를 통과해야 "복구 완료"다. 이전 태그의 묶음이 없으면(이미지만 바꾼 수동 롤백) 설정을 바꾸기 전에 지금 운영 설정을 그 태그의 묶음으로 남긴다. 그러지 않으면 복구가 방금 깐 실패 태그의 설정을 그대로 썼다.
7. 정리. 옛 이미지와 묶음을 지운다. 실패해도 경고만 남기고 배포 결과와 분리한다.

설정 교체는 묶음을 운영 디렉터리에 덮어 풀지 않고 rsync `--delete`로 정확히 맞춘다. 덮어 풀기는 저장소에서 지운 파일을 남기는데, HTTPS 전환 때 옛 `app.conf`가 남으면 `default_server`가 둘이 되어 nginx가 뜨지 않는다. `.env`·`certbot/`·`backups/`·`releases/`만 제외한다.

배포 전 덤프를 스크립트에 넣은 이유는 `ddl-auto: update`로 바뀐 스키마는 이미지를 되돌려도 남기 때문이다. 스키마 복구는 자동으로 하지 않고 README 7절의 수동 절차로 둔다. 컬럼 하나를 고치는 것이 대부분 낫고, 전체 복원은 덤프 이후 데이터를 잃기 때문이다.

백업 스크립트는 덤프가 끝까지 기록됐는지(mysqldump가 마지막 줄에 남기는 완료 표시)와 S3 업로드 크기를 확인하고, 실패하면 웹훅으로 알린다. 이전 README의 cron 한 줄은 `sh`로 돌아 `pipefail`이 없었다. mysqldump가 실패해도 빈 gzip이 만들어져 성공으로 기록될 수 있었다. 복원 점검 스크립트는 운영 DB와 분리된 임시 MySQL에 덤프를 실제로 넣어 본다.

## 7. 검증 결과 (로컬, 2026-10-26)

| 확인 | 결과 |
| --- | --- |
| prod 프로필 기동(로컬 MySQL·Redis 상대) | 약 7초에 기동, `GET /api/categories` 200, uid 1001, 시간대 KST |
| 관리 포트 헬스 체크(#190 병합 코드, 10/26) | 기동 약 15초 뒤 헬스 명령 통과. 관리 포트 `/actuator/health` 200(`{"status":"UP"}`), `/actuator/prometheus` 200, 앱 포트 `/actuator/health` 401, `/api/categories` 200. 운영 로그는 JSON에 `traceId` 포함 |
| `PORTONE_API_SECRET` 없이 기동 | 거부. `portone.apiSecret must not be blank` |
| nginx 설정, compose 세 파일, 이미지 태그 네 개 | 전부 유효 |
| 빈 DB 첫 기동(운영 compose) | 스키마 생성과 시드 후 16초에 healthy. nginx 경유 200. 시드는 region 2·mart 2·admin 0(관리자 시드가 prod에서 빠짐을 확인) |
| 락 대기·binlog·버퍼 풀 | 전역 5초, 앱 세션 5초, binlog 꺼짐, 버퍼 풀 512MB |
| 앱 IP 변경 후 재배포 | 다른 컨테이너로 자리를 차지해 .4 → .6으로 바꿈. nginx reload 없이 200, nginx 재시작 0회 |
| 비밀 범위·노출 포트 | 앱 env에 MySQL 루트 비밀번호 없음. 호스트 노출은 80·443뿐 |
| `deploy.sh` 정상 배포(로컬 레지스트리) | 덤프 → pull → app·nginx 기동 → 13초에 healthy → exit 0. **10/27 검토에서 이 검증이 평상시 경우를 놓쳤음이 드러났다(8절 첫 항목)** |
| `deploy.sh` 깨진 이미지 | 재시작 반복 → 40초 뒤 실패 판정 → 이전 태그로 복구 → healthy → exit 1. `.env`도 이전 태그로 복구, 서비스는 내내 응답 |

### 7-1. 검토 반영 후 재검증 (로컬, 2026-10-27)

운영과 같은 디렉터리 구조(`releases/<태그>`)를 만들고, 시나리오마다 묶음을 하나씩 준비해 묶음 안의 스크립트로 배포했다.

| 시나리오 | 결과 |
| --- | --- |
| 1. 빈 서버 첫 배포(MySQL 없음) | MySQL·Redis를 띄우고 healthy까지 대기 → 덤프 → 배포 완료. exit 0, 32초, nginx 경유 200 |
| 2. 정상 재배포, 지울 옛 이미지 없음 | exit 0, 16초. 새 nginx 파일이 reload로 반영, 바인드 마운트 파일의 inode 유지 |
| 3. 저장소에서 지운 파일 | 서버와 nginx 컨테이너 양쪽에서 사라짐. exit 0 |
| 4. 컨테이너 기동 실패(앱이 80번 포트 충돌) | "컨테이너 기동 실패" → 이전 태그의 이미지·설정으로 복구 → healthy·경유 200 확인. exit 1, 25초. 앱의 포트 매핑도 이전 설정으로 돌아옴 |
| 5. nginx 설정 문법 오류 | `nginx -t` 실패 → reload 전에 멈춤 → 이전 설정으로 복구. exit 1, 18초. 잘못된 파일이 서버에서 사라짐 |
| 6. 헬스 체크 실패(앱이 뜨지 않는 이미지, 제한 60초) | 60초 뒤 실패 판정 → 이전 태그로 복구. exit 1, 113초. 그동안 nginx는 502였을 것이다(재시작 방식의 한계) |
| 7. 수동 롤백과 이미지 정리 | 이전 버전을 수동 배포해 exit 0. 이미지가 현재·이전 두 개만 남음 |
| 8. 중복 배포(Ubuntu 24.04 컨테이너에서 워크플로의 SSM 명령 그대로) | 첫 실행이 도는 동안 두 번째는 "다른 배포가 진행 중"으로 exit 1. 첫 실행 뒤에는 다시 실행됨. 임시 디렉터리 0개 |
| 9. 백업 | nightly 버킷 미설정과 MySQL 정지 중 덤프가 모두 exit 1과 웹훅 알림. 임시 파일 0개, 디렉터리 700·파일 600. pre-deploy 10개 유지. 임시 MySQL에 복원해 테이블 32개 확인 |
| 그 밖 | 필수 값이 빈 `.env`는 이름을 대고 멈추고 실행 중인 앱은 그대로. 이미지에 curl 있음, 종료 대기 30초, Redis AOF·200mb·noeviction 적용 |

### 7-2. HTTPS 전환과 확인 강화 (로컬, 2026-10-27)

완료 조건 다섯 개(지울 이미지 없는 성공, 기동·헬스 실패 복구, HTTPS 전환 시 옛 `app.conf` 제거, 겹친 배포의 비간섭, nginx가 잘못되면 실패)를 다시 대조했다. 그 결과 HTTPS 전환 뒤 응답 확인이 무력해지는 빈틈을 찾아 고쳤다. 자체 CA로 `recipick.localhost` 인증서를 만들어 전환을 그대로 재현했다. curl은 `.localhost`를 127.0.0.1로 해석한다.

| 시나리오 | 결과 |
| --- | --- |
| A. 첫 배포, HTTP 단계, 확인 주소는 도메인 | exit 0. nginx를 새로 만든 직후의 reload도 통과(8절의 PID 항목 수정 후) |
| B. HTTPS 전환(`app.conf` → `.disabled`, `app-https.conf` 추가) | exit 0. 서버와 nginx에서 옛 `app.conf`가 사라짐. http 301 → https 200, 인증서 이름 일치 |
| 이전 확인 방식으로 본 깨진 HTTPS | https가 502인데 http 주소의 301만 보고 exit 0. 이 빈틈을 재현했다 |
| C. HTTPS 블록 오류(`nginx -t` 통과, https 502) | 리다이렉트를 따라간 최종 응답 502로 실패 판정 → B로 복구. exit 1 |
| D. 인증서 없는 HTTPS 설정 | `nginx -t`가 인증서 로드 실패로 막음 → B로 복구. exit 1 |
| E. HTTPS → HTTP 수동 롤백 | exit 0. `app-https.conf`가 사라지고 `app.conf`가 돌아옴 |
| F. 갱신 cron과 배포 잠금(Ubuntu 24.04) | 배포가 잠금을 쥔 동안 갱신은 기다리다 포기하고, 놓은 뒤에는 실행됨 |

실서버 검증(워크플로 첫 실행, 실제 인증서 발급, S3 업로드, 10-1 체크리스트)은 AWS 리소스를 만든 뒤 한다.

### 7-3. 실서버 검증 (2026-10-30)

AWS 서버(EC2 t3.medium 1대, `recipick.shop`)에서 `docs/deploy-strategy.md` 10-1을 확인했다. 결과 표는 그 절에 있고, 여기에는 방법을 적는다.

| 확인 | 방법 | 결과 |
| --- | --- | --- |
| 실패 배포 복구 | 현재 태그의 묶음을 이전 이미지 SHA 이름으로 복사하고 `compose.yml`의 앱 헬스 체크만 `false && ...`로 바꿔 서버에서 수동 배포. 원래 그 이름의 묶음은 옮겨 두었다가 되돌렸다 | 헬스 실패 → 이전 태그의 이미지·설정 묶음으로 복구 → nginx 검사·reload·healthy·경유 응답 통과, 종료 코드 1. 복구 뒤 `IMAGE_TAG` 원래 값, 외부 200. 헬스 체크만 깨뜨렸으므로 실패 구간에도 nginx는 새 앱으로 응답했고, 끊김은 앱을 다시 만드는 순간뿐이었다 |
| 중복 배포 | 다른 셸에서 `.deploy.lock`을 20초 쥔 채 수동 배포 | 첫 단계에서 "다른 배포가 진행 중이라 중단", 종료 코드 1, 아무것도 바뀌지 않음 |
| nightly 백업 | cron 첫 자동 실행 뒤 로그·로컬 파일·S3 `head-object` 크기 비교 | 덤프 완료 표시 확인, 로컬 5528 B = S3 5528 B |
| 복원 | 그 덤프로 `restore-check.sh`(볼륨 없는 임시 MySQL) | 테이블 33개, 종료 코드 0, 임시 컨테이너 정리 |
| 프론트 되돌리기 | `.deploy.lock`을 잡고 `/opt/recipick-web/current`를 직전 SHA로 바꿨다가 복구 | 외부에서 화면 번들 이름이 바뀌었다가 돌아옴, 앱 재시작 없음 |
| 인증 | 테스트 회원으로 curl 로그인 → 쿠키만으로 재발급 → 로그아웃 → 재발급, 브라우저에서 새로고침 | 쿠키 `Secure; HttpOnly; SameSite=Strict; Path=/api/auth`, 재발급 200, 로그아웃 뒤 401 |
| 서버 내부 | MySQL 전역 변수, 시드 행 수, 앱 컨테이너 안에서 8080·8081 헬스, `docker port`, 로그 설정, `df -h /`, 앱 로그 비밀 패턴 | 10-1 표와 같음 |

실패 배포 테스트는 프로덕션에 일부러 실패를 넣는 일이라, 깨뜨릴 곳을 헬스 체크 하나로 좁히고 테스트용 묶음을 반드시 지웠다. 남겨 두면 나중에 그 SHA로 수동 롤백할 때 깨진 설정이 깔린다.

## 8. 트러블슈팅 메모

- **정상 배포가 실패로 끝났다(10/27 검토에서 발견).** 성공 후 옛 이미지를 지우는 파이프라인에서, 지울 이미지가 없으면 `grep -v`가 종료 코드 1을 낸다. `set -euo pipefail` 때문에 앱이 정상인데도 스크립트가 1로 끝났다. 서버에 현재·이전 태그만 남은 평상시가 바로 이 경우라 두 번째 배포부터 매번 실패로 표시될 뻔했다. 10/26 검증은 지울 다른 태그가 남아 있던 상태라 통과했다. grep의 "없음"을 정상으로 처리하고 정리 단계 전체를 배포 결과와 분리했다. 교훈은 성공 경로도 "정리할 것이 없는" 상태로 한 번 더 돌려야 한다는 것이다.
- **HTTPS 전환 뒤 응답 확인이 301에 속는다.** `curl -f`는 400 이상만 실패로 보므로, http 주소가 https로 301을 주면 HTTPS 블록이나 인증서가 깨져도 통과했다. 리다이렉트를 따라가 최종 200만 성공으로 바꿨다. 확인 주소는 `127.0.0.1`이 아니라 도메인으로 둔다. 그래야 따라간 https 주소의 인증서 이름이 맞는다. 덕분에 HTTPS 전환 때 확인 주소를 바꿀 필요가 없어졌다.
- **방금 만든 nginx는 reload를 거부한다.** 컨테이너가 생성된 직후에는 master가 PID 파일을 쓰기 전이라 `nginx -s reload`가 "invalid PID"로 실패한다. 첫 배포에서만 드러나고 앞선 재검증은 타이밍 운으로 통과했다. PID 파일이 생길 때까지 기다린 뒤 reload한다.
- **Ubuntu 24.04에는 apt `awscli`가 없다(CodeRabbit 지적).** 서버 초기화 첫 명령이 실패할 뻔했다. snap으로 설치하면 `/snap/bin`에 깔리는데 cron과 SSM 명령의 PATH에는 이 경로가 없어, 스크립트와 SSM 명령 첫머리에서 PATH에 더했다.
- **`head-object`에는 `s3:GetObject`가 필요하다(CodeRabbit 지적).** 업로드 크기 확인을 넣으면서 EC2 정책은 PutObject만 줬다. 문서대로 만들면 nightly 백업이 매번 실패했을 것이다. 로컬에는 S3가 없어 검증으로 잡을 수 없던 종류라, 정책을 바꿀 때는 스크립트가 부르는 API 목록과 대조한다.
- **`.env`를 비우고 다시 쓰면 반만 남을 수 있다.** 권한 600을 지키려고 내용만 덮어썼는데, 쓰기 도중 디스크가 차면 비밀값이 든 파일이 깨진다. 권한 600 임시 파일에 다 쓰고 확인한 뒤 `mv`로 바꾼다. `.env`는 바인드 마운트가 아니라 inode가 바뀌어도 된다.
- **바인드 마운트한 파일을 교체하면 컨테이너가 옛 파일을 본다.** 파일을 지우고 새로 쓰거나 rename으로 바꾸면 inode가 바뀌는데, 컨테이너는 마운트 시점의 inode를 계속 본다. 디렉터리도 같다. 그래서 설정 교체는 rsync `--inplace`로 내용만 덮어쓰고 디렉터리는 지우지 않는다. 시나리오 2에서 inode가 유지되는 것을 확인했다.
- **if 조건 안의 함수에서는 `set -e`가 꺼진다.** `if activate; then`으로 부르면 함수 안의 실패가 함수를 멈추지 않는다. 단계마다 `|| return 1`을 직접 붙였다.
- **동기화 제외 패턴이 `.env.example`까지 막았다.** `.env` 임시 파일을 보호하려고 `/.env.*`를 제외했더니 예시 파일도 서버에 깔리지 않았다. 임시 파일 이름을 `.env.tmp-*`로 바꾸고 그것만 제외했다.
- **SSM 명령은 `sh`로 돌 수 있다.** Ubuntu의 `sh`는 dash라 `pipefail`을 믿을 수 없다. SSM 명령에는 POSIX 문법만 쓰고, 나머지는 `bash`로 실행하는 배포 스크립트에 둔다.
- **실행 중인 스크립트가 교체된다.** 서버의 `scripts/deploy.sh`로 수동 배포하면 설정 교체 단계가 실행 중인 자기 자신을 덮어쓴다. bash는 스크립트를 조금씩 읽으므로 뒷부분이 어긋날 수 있다. 본문 전체를 `main` 함수로 감싸 미리 다 읽게 했다.
- **`.env`의 DB URL은 셸에서 source하면 깨진다.** `&`가 백그라운드 실행으로 해석된다. 예시 파일에서는 큰따옴표로 감쌌고 compose는 따옴표를 벗겨 읽는 것을 확인했다. 스크립트는 source 대신 grep으로 읽는다.
- **프로필 전용 파일의 `on-profile`은 기동 오류다.** `application-prod.yml`에 `spring.config.activate.on-profile: prod`를 적으면 Spring Boot가 거부한다. 파일 이름이 이미 프로필을 뜻한다.
- **재배포 테스트가 같은 IP를 다시 받아 무효였다.** 컨테이너를 지우고 다시 만들면 도커가 같은 IP를 주므로 DNS 재조회가 검증되지 않았다. 앱을 멈춘 사이 다른 컨테이너로 그 IP를 차지하게 해서 다른 IP를 받게 한 뒤 확인했다.
- **nginx 기본 설정이 앱을 가로챈다.** 이미지의 `default.conf`가 80번의 default_server라 IP 접근이 환영 페이지로 갔다. conf.d를 디렉터리째 마운트해 가렸다.
- **스크립트의 GNU 전용 옵션.** `flock`, `sed -i`, `xargs -r`은 macOS에 없거나 동작이 다르다. flock은 없으면 잠금 없이 진행하고, `.env` 갱신은 임시 파일로 내용만 덮어써 권한 600을 유지하며, xargs 대신 read 루프를 썼다. 서버(Ubuntu)에서는 전부 있다.
- **macOS의 5000번 포트.** 로컬 레지스트리를 5000에 띄우면 AirPlay와 충돌하므로 5050을 썼다.

실서버에서 겪은 것(10/27~10/30)

- **OIDC 역할 가정이 `AccessDenied`로 실패했다.** 신뢰 정책의 `sub`를 `repo:angel-kitchen/recipick:ref:refs/heads/dev`로 적었는데, 저장소 OIDC 설정이 고유 ID 방식이라 실제 `sub`는 `repo:angel-kitchen@<조직 ID>/recipick@<저장소 ID>:ref:refs/heads/dev`였다. 공급자·Audience·ARN은 CloudTrail의 `AssumeRoleWithWebIdentity` 이벤트로 배제하고 `sub` 문자열 차이를 찾았다. 신뢰 정책을 그 형식으로 바꿔 해결했다. 로그만 보면 원인 후보가 너무 많아, 실패한 요청의 실제 클레임을 CloudTrail에서 보는 것이 가장 빨랐다.
- **새 커밋이 필수 환경 변수를 추가하면 배포가 멈춘다.** `TRIAL_BLOCK_HMAC_SECRET`이 추가된 머지에서 배포가 `.env` 검사 단계에서 멈췄다. 배포는 `.env`를 건드리지 않으므로 의도한 동작이고, 아무것도 바꾸기 전에 멈춰 서비스는 이전 버전으로 계속 응답했다. 서버 `.env`에 값을 넣고 다시 실행했다. 이후 새 환경 변수를 추가하는 PR은 머지 전에 서버 `.env`부터 채운다.
- **선택 환경 변수는 compose에 적지 않으면 앱에 가지 않는다.** PortOne 웹훅 시크릿(`portone.webhook.secret`)이 추가됐지만 운영 compose의 app `environment`에 없어 `.env`에 넣어도 전달되지 않았다. 선택 값이라 기동은 정상이고 웹훅만 모두 거절돼 눈에 띄지 않았다. compose는 `.env`를 통째로 넘기지 않고 적은 값만 넘기므로(MySQL 루트 비밀번호 등을 앱에 주지 않기 위해), 설정 속성을 추가하는 PR에서는 compose와 `.env.example`도 같이 본다.
- **서버 시계가 UTC다.** README의 cron 시각을 그대로 넣으면 한국 시간 낮에 돈다. 백업은 `30 19 * * *`(KST 04:30), 인증서 갱신은 `0 19 * * 0`(KST 월 04:00)으로 바꿔 넣었다. 백업 파일 이름의 시각도 UTC다.
- **묶음 없는 이전 태그로 복구하면 실패한 태그의 설정을 썼다(PR #294).** README 7절의 "이미지만 바꾸는 수동 롤백"을 하면 그 태그는 `releases/<태그>`가 없다. 그 뒤 배포가 실패하면 복구가 설정 묶음을 찾지 못하고 방금 깐 실패 태그의 설정을 그대로 썼다. 배포 시작 시 설정을 바꾸기 전에 지금 운영 설정을 그 태그 이름으로 남기도록 고쳤다. 같은 PR에서 nightly 백업이 MySQL 컨테이너가 없을 때 조용히 0으로 끝나던 것도 실패·알림으로 바꿨다(배포 전 백업은 첫 배포 때문에 건너뛰기 유지).
- **프론트·백엔드 배포가 서로의 대기 작업을 지웠다(PR #282 리뷰).** GitHub `concurrency`는 같은 그룹에서 대기 작업을 하나만 남기고 나머지를 취소한다. 그룹을 `deploy-backend`·`deploy-frontend`로 나누고, 프론트는 `queue: max`로 대기 작업을 모두 남겼다. 서버 작업 겹침은 `.deploy.lock`이 막는다.
- **`curl -I`로 보면 API가 401이다.** `HEAD /api/categories`는 공개 경로로 등록되지 않아 401이 나온다. GET은 200이다. 점검은 GET으로 한다.
- **복원 점검의 행 수가 0으로 보인다.** `restore-check.sh`는 `information_schema.tables.table_rows`로 행 수를 보여 주는데, InnoDB에서는 추정값이라 복원 직후 실제보다 적게 나온다. 복원 성공 판단은 테이블 수와 종료 코드로 한다.
- **도메인 등록이 몇 시간째 "진행 중"이었다.** Route 53에서 `.click` 도메인을 신청했는데 3시간 넘게 진행 중이라 일정상 포기하고 국내 등록 기관에서 `recipick.shop`을 샀다. HTTPS 날짜가 걸린 작업은 도메인 등록을 가장 먼저 시작하고, 막히면 바로 다른 곳으로 옮긴다.

## 9. 남은 일

- ~~`deploy/aws-setup.md`대로 AWS 리소스를 만들고(예산 알림 먼저) `deploy/README.md` 4절대로 서버를 세운다.~~ 10/27 완료.
- ~~10/30 HTTPS 전환과 자동 배포 확인, 실서버에서 일부러 실패 배포 1회, nightly 백업과 복원 1회, 10-1 체크리스트 통과~~ 10/30 완료(7-3, 10-1은 웹훅 제외).
- PortOne 웹훅 시크릿: 서버 `.env`와 앱 컨테이너에 없다(10/30 확인). 결제 담당에게 받아 넣고 앱을 다시 만든 뒤 웹훅 주소를 등록한다. 그 전에는 운영 앱이 모든 웹훅을 거절한다.
- `ALERT_WEBHOOK_URL`: 비어 있다. 이 값이 없으면 `backup.sh`는 실패해도 알림을 보내지 않고 로그(`/var/log/recipick-backup.log`)에만 남긴다. 디스코드나 슬랙 웹훅 주소를 넣는다.
- 관리자 IAM 그룹(배포 담당 + 대리 1명): 만들지 않았다. 지금은 배포 담당만 Session Manager·백업 다운로드가 가능하다. 대리 1명을 정한 뒤 `deploy/aws-setup.md` 10절대로 만든다.
- 블루그린은 운영 서버에서 부하 없이 앱 1개와 모니터링의 실제 메모리를 재고(`docker stats`, `free -m`), 앱 두 개가 들어갈 여유가 있을 때만 검토한다. 부하 테스트는 로컬 측정으로 옮겨 이 판단에 쓰지 않는다. 스케줄 작업 단일 실행 규칙이 컨벤션에 들어간 것도 선행 조건이다.

## 10. 3차 2단계 스키마 변경 (Expand → Contract)

이 절의 소절 번호는 `10-A`~`10-D`다(위 7-3·9절이 가리키는 `docs/deploy-strategy.md`의 10-1 체크리스트와 섞이지 않게).

운영은 `ddl-auto: update`지만 스키마 변경은 아래 명시 SQL로 먼저 적용하고, 앱 기동 뒤 Hibernate가 추가로 바꾼 것이 없는지
`information_schema` 스냅숏 diff로 확인한다. 제약 이름은 엔티티와 DDL에서 같다 — Hibernate `update`는 UNIQUE를 매 기동마다
"drop 시도 → create"(`hibernate.schema_update.unique_constraint_strategy` 기본 `DROP_RECREATE_QUIETLY`)하므로 이름이 다르면 같은
컬럼에 중복 UNIQUE 인덱스가 생긴다. 자료형·NULL 여부·`DATETIME(6)`은 `SHOW CREATE TABLE`로 기존 컬럼과 같은 형식인지 확인한다.

**MySQL DDL은 문장마다 즉시 커밋된다.** MySQL 8의 원자적 DDL은 "한 문장이 반쯤 적용된 채 남지 않는다"는 뜻이지 여러 `ALTER`를 한
트랜잭션으로 묶어 되돌린다는 뜻이 아니다. 그래서 문장마다 실행 기록표에 적고, 실패하면 기록표의 되돌리기 문장을 역순으로 실행한다.
이전 앱의 `ddl-auto`가 컬럼을 되살린다는 가정은 롤백 근거로 쓰지 않는다 — Expand는 아무것도 지우지 않으므로 이전 이미지를 다시
배포해도 이전 앱이 보던 컬럼이 그대로 있고, 이전 앱은 새 컬럼·테이블을 모른 채 지나간다(이 테이블을 쓰는 이전 코드 없음).

실행자는 배포 담당. **실행 전 필수 조건:** `SHOW CREATE TABLE`로 실제 스키마를 확인하고, 각 절의 사전 확인 쿼리로 기존 행 유무를
본 뒤 멈춤 조건에 해당하면 진행하지 않는다.

### 10-A. PR-2a — billing_key 확장, billing_key_issue_request 생성 (추가만)

```sql
-- A-0 백업
--   mysqldump --single-transaction --no-create-db <db> billing_key > backup_billing_key_YYYYMMDD.sql
-- A-1 형식 확인 (id·created_at·updated_at 정의를 A-3에 그대로 쓴다)
SHOW CREATE TABLE billing_key;

-- A-2 billing_key 컬럼·인덱스
ALTER TABLE billing_key                                               -- A-2-1
  ADD COLUMN delete_requested_at DATETIME(6) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE billing_key                                               -- A-2-2
  ADD INDEX idx_billing_key_member_id_status (member_id, status);
-- A-2-3 기존 UNIQUE 인덱스(Hibernate 생성 이름 UK_…)를 엔티티가 명시한 이름으로 바꾼다. 내용은 그대로이고 메타데이터만 바뀐다.
--       엔티티와 이름이 같아야 기동 때 Hibernate가 같은 인덱스로 인식하고, 서비스가 UNIQUE 충돌을 제약 이름으로 식별한다
SELECT INDEX_NAME FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_key'
   AND COLUMN_NAME = 'portone_billing_key' AND NON_UNIQUE = 0;         -- 결과 1개(예: UK_abc123…)
ALTER TABLE billing_key RENAME INDEX <위에서 조회한 이름> TO uk_billing_key_portone_billing_key;

-- A-3 발급 요청 테이블
CREATE TABLE billing_key_issue_request (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  member_id      BIGINT       NOT NULL,
  issue_id       VARCHAR(64)  NOT NULL,
  status         VARCHAR(20)  NOT NULL,
  expires_at     DATETIME(6)  NOT NULL,
  used_at        DATETIME(6)  NULL,
  billing_key_id BIGINT       NULL,
  created_at     DATETIME(6)  NULL,
  updated_at     DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_billing_key_issue_request_issue_id (issue_id),
  CONSTRAINT fk_billing_key_issue_request_billing_key
    FOREIGN KEY (billing_key_id) REFERENCES billing_key (id)
) ENGINE = InnoDB;
```

| 순번 | 문장 | 실행 시각 | 결과 | 되돌리기 |
| --- | --- | --- | --- | --- |
| A-2-1 | `ADD COLUMN` 2개 | 2026-11-01 17:57:10 | OK (0.05s) | `ALTER TABLE billing_key DROP COLUMN delete_requested_at, DROP COLUMN deleted_at;` |
| A-2-2 | `ADD INDEX` | 2026-11-01 17:57:10 | OK (0.02s) | `ALTER TABLE billing_key DROP INDEX idx_billing_key_member_id_status;` |
| A-2-3 | `RENAME INDEX` | 2026-11-01 17:57:10 | OK (0.02s) | `ALTER TABLE billing_key RENAME INDEX uk_billing_key_portone_billing_key TO UKcwtq7gi4jlxbgbv2cx1agj5vj;` |
| A-3 | `CREATE TABLE` | 2026-11-01 17:57:10 | OK (0.04s) | `SELECT COUNT(*) FROM billing_key_issue_request;`가 0일 때만 `DROP TABLE billing_key_issue_request;`. 0이 아니면 사전 백업에 없는 행이므로 먼저 `mysqldump --single-transaction <db> billing_key_issue_request > billing_key_issue_request_YYYYMMDD.sql`로 보관한다(복원: `mysql <db> < billing_key_issue_request_YYYYMMDD.sql`) |

적용 뒤 10-B의 스냅숏 쿼리를 앱 기동 전·후에 떠서 diff가 없어야 한다. 스모크: `GET /api/billing-keys/me`(200).

**운영 실행 기록 (2026-11-01, 배포 담당).** 시각은 KST다(서버 출력은 UTC 08:57:10).

- 사전: `backup.sh pre-deploy ddl-318`로 덤프. 적용 전 `billing_key`는 0행, UNIQUE는 Hibernate 생성 이름 `UKcwtq7gi4jlxbgbv2cx1agj5vj`
  1개, 새 컬럼·테이블 없음.
- 적용: 네 문장을 한 번에 실행했다(클라이언트는 첫 오류에서 멈추고 `-vvv`로 문장별 결과를 남긴다). 모두 성공.
- 확인: 적용 직후 10-B 스냅숏 ①을 떴다. PR #318을 머지해 `279a7eb` 이미지가 자동 배포되어 `healthy`가 된 뒤 스냅숏 ②를 떴고,
  ①과 diff가 없었다. `portone_billing_key`의 UNIQUE는 `uk_billing_key_portone_billing_key` 1개다.
  스모크: `GET /api/billing-keys/me`가 토큰 없이 401, 회원 토큰으로 200(`registered: false`).
- 사전 로컬 검증: 운영 스키마 덤프(`--no-data`)를 MySQL 8.4에 넣고 prod 프로필(`ddl-auto: update`, Hibernate 6.6.53)로
  이전 앱(dev `cf8c340`)과 신버전(dev + #318)을 번갈아 기동해 기동 전후 스키마 덤프를 비교했다.
  - 추가 DDL(A-2-1·A-2-2·A-3)만 적용 후 이전 앱 기동: 변화 없음.
  - RENAME 후 신버전 기동(2회): 변화 없음, UNIQUE 1개.
  - RENAME 후 이전 앱 기동(자동 복구·수동 롤백 상황): 변화 없음, UNIQUE 1개. 이전 엔티티의 이름 없는 `@Column(unique = true)`는
    같은 컬럼의 UNIQUE를 다시 만들지 않았다. 그래서 롤백 때 A-2-3을 되돌릴 필요가 없다.
  - DDL 없이 신버전만 기동: UNIQUE 2개(`uk_…` + `UK…`). A-2-3을 머지 전에 해야 하는 이유다.
- 롤백 주의: 이전 이미지로 돌아가도 인덱스를 지우지 않는다. 이전 앱은 UNIQUE가 없어도 새로 만들지 않으므로(로컬 검증),
  `uk_billing_key_portone_billing_key`를 지우면 이 컬럼의 UNIQUE가 사라진다.
- 엔티티 이름을 운영 이름(`UK…`)에 맞추는 대안(PR #318 리뷰 스레드)은 위 결과로 채택하지 않았다. 앞으로 엔티티의 제약 이름을
  바꾸면 운영에 UNIQUE가 하나 더 생긴다.

### 10-B. 스냅숏·확인 SQL (앱 기동 전 1회, 기동 뒤 1회 → diff 없음)

```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('billing_key', 'billing_key_issue_request',
                      'membership_payment', 'membership_payment_attempt', 'membership_refund')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
  FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('billing_key_issue_request', 'membership_payment',
                      'membership_payment_attempt', 'membership_refund')
 ORDER BY TABLE_NAME, CONSTRAINT_NAME;
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('billing_key', 'billing_key_issue_request',
                      'membership_payment', 'membership_payment_attempt', 'membership_refund')
 GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
 ORDER BY TABLE_NAME, INDEX_NAME;
-- 기대: 같은 컬럼 조합의 인덱스가 두 개인 테이블 없음(중복 UK 없음), 새 컬럼·FK·인덱스 이름이 설계와 같음,
--       기동 로그에 alter table·create table 없음
```

### 10-C. PR-2b Expand — membership_payment 확장, membership_payment_attempt 생성, membership_refund 확장 (기본 배포 절차, 지우는 문장 없음)

기존 컬럼 `billing_key_id`·`portone_payment_id`·`attempt_count`·`last_attempt_at`는 새 엔티티가 매핑하지 않지만 **지우지 않는다**(10-D Contract).
새 코드의 INSERT를 막는 것은 `attempt_count`(NOT NULL, DEFAULT 없음)뿐이라 NULL 허용으로 바꾼다 — 안 하면 새 회차 INSERT가
"Field 'attempt_count' doesn't have a default value"로 실패한다. 나머지 세 컬럼은 NULL 허용이다(`SHOW CREATE TABLE`로 확인).

```sql
-- B-0 백업 (세 테이블 + 스키마 전체 DDL)
--   mysqldump --single-transaction <db> membership_payment membership_refund billing_key > backup_membership_YYYYMMDD.sql
--   mysqldump --no-data <db> > schema_before_YYYYMMDD.sql

-- B-1 사전 확인 (멈춤 조건은 아래 표)
SELECT (SELECT COUNT(*) FROM membership_payment) AS payments,
       (SELECT COUNT(*) FROM membership_refund)  AS refunds;        -- 0건 기대
SHOW CREATE TABLE membership_payment;                                -- attempt_count의 NOT NULL·DEFAULT, 기존 FK 이름
SHOW CREATE TABLE membership_refund;
SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME            -- 두 테이블을 참조하는 FK (기대: refund → payment 하나)
  FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND REFERENCED_TABLE_NAME IN ('membership_payment', 'membership_refund');

-- B-2 membership_payment 확장
ALTER TABLE membership_payment MODIFY attempt_count INT NULL;        -- B-2-1 신규 INSERT를 막는 NOT NULL 완화
ALTER TABLE membership_payment                                       -- B-2-2
  ADD COLUMN idempotency_key       VARCHAR(100) NULL,
  ADD COLUMN request_hash          VARCHAR(64)  NULL,
  ADD COLUMN grace_deadline_at     DATETIME(6)  NULL,
  ADD COLUMN membership_applied_at DATETIME(6)  NULL;
ALTER TABLE membership_payment                                       -- B-2-3
  ADD UNIQUE KEY uk_membership_payment_idempotency_key (idempotency_key),
  ADD INDEX idx_membership_payment_membership_id_status (membership_id, status);

-- B-3 시도 테이블 (id·created_at·updated_at 정의는 SHOW CREATE TABLE에 맞춘다)
CREATE TABLE membership_payment_attempt (
  id                     BIGINT       NOT NULL AUTO_INCREMENT,
  membership_payment_id  BIGINT       NOT NULL,
  attempt_no             INT          NOT NULL,
  billing_key_id         BIGINT       NOT NULL,
  portone_payment_id     VARCHAR(255) NOT NULL,
  status                 VARCHAR(20)  NOT NULL,
  requested_at           DATETIME(6)  NOT NULL,
  lease_expires_at       DATETIME(6)  NOT NULL,
  dispatched_at          DATETIME(6)  NULL,
  completed_at           DATETIME(6)  NULL,
  pg_paid_at             DATETIME(6)  NULL,
  failure_reason         VARCHAR(500) NULL,
  ops_review_required_at DATETIME(6)  NULL,
  ops_review_reason      VARCHAR(40)  NULL,
  refund_required_at     DATETIME(6)  NULL,
  created_at             DATETIME(6)  NULL,
  updated_at             DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_membership_payment_attempt_portone_payment_id (portone_payment_id),
  UNIQUE KEY uk_membership_payment_attempt_payment_id_attempt_no (membership_payment_id, attempt_no),
  INDEX idx_membership_payment_attempt_payment_id_status (membership_payment_id, status),
  INDEX idx_membership_payment_attempt_billing_key_id_status (billing_key_id, status),
  CONSTRAINT fk_membership_payment_attempt_payment
    FOREIGN KEY (membership_payment_id) REFERENCES membership_payment (id),
  CONSTRAINT fk_membership_payment_attempt_billing_key
    FOREIGN KEY (billing_key_id) REFERENCES billing_key (id)
) ENGINE = InnoDB;

-- B-4 membership_refund 확장 (인덱스 먼저, FK는 그 인덱스를 쓴다)
ALTER TABLE membership_refund                                        -- B-4-1
  ADD COLUMN membership_payment_attempt_id BIGINT NOT NULL;          --   refunds = 0 일 때. 행이 있으면 NULL로 추가(아래 표)
ALTER TABLE membership_refund                                        -- B-4-2
  ADD INDEX idx_membership_refund_payment_id_status (membership_payment_id, status),
  ADD INDEX idx_membership_refund_attempt_requester_status (membership_payment_attempt_id, requested_by, status);
ALTER TABLE membership_refund                                        -- B-4-3
  ADD CONSTRAINT fk_membership_refund_attempt
    FOREIGN KEY (membership_payment_attempt_id) REFERENCES membership_payment_attempt (id);
```

| B-1 결과 | 조치 |
| --- | --- |
| `payments` > 0 | **중단.** 기존 회차를 새 시도 테이블로 옮기는 백필 절차가 없다 — 그대로 배포하면 기존 회차의 시도 단위 상태가 C-17 조회에서 빠지고, 새 enum에 없는 `status` 행은 새 엔티티가 읽지 못한다. 행이 있으면 백필·상태 검증 절차를 따로 설계·승인한 뒤에만 진행(2026-11-01 운영 실행 때는 0건) |
| `refunds` > 0 | **멈춤.** 기존 환불 행을 연결할 시도가 없다. 신규 시도 FK의 연결 방법을 확정하기 전에는 배포하지 않는다. 확정되면 B-4-1을 `NULL` 허용으로 추가하고 NOT NULL 전환은 10-D로 넘긴다 |
| `attempt_count`가 이미 NULL 허용 | B-2-1 생략 |
| 참조 FK가 기대와 다름 | 멈춤, 참조처 확인 |

실행 기록표(문장마다 즉시 커밋되므로 한 줄씩 적는다). 되돌릴 때는 역순(B-4-3 → B-4-2 → B-4-1 → B-3 → B-2-3 → B-2-2 → B-2-1).

| 순번 | 문장 | 실행 시각 | 결과 | 되돌리기 |
| --- | --- | --- | --- | --- |
| B-2-1 | `MODIFY attempt_count INT NULL` | 2026-11-02 00:19 | OK | `ALTER TABLE membership_payment MODIFY attempt_count INT NOT NULL;`(0건일 때만. 행이 있으면 그대로 둔다 — NULL 허용이 남아도 이전 앱에 해가 없다) |
| B-2-2 | `ADD COLUMN` 4개 | 2026-11-02 00:19 | OK | `ALTER TABLE membership_payment DROP COLUMN idempotency_key, DROP COLUMN request_hash, DROP COLUMN grace_deadline_at, DROP COLUMN membership_applied_at;` |
| B-2-3 | `ADD UNIQUE KEY`·`ADD INDEX` | 2026-11-02 00:19 | OK | `ALTER TABLE membership_payment DROP INDEX uk_membership_payment_idempotency_key, DROP INDEX idx_membership_payment_membership_id_status;` |
| B-3 | `CREATE TABLE membership_payment_attempt` | 2026-11-02 00:19 | OK (0.05s) | `DROP TABLE membership_payment_attempt;`(0건 확인 뒤, B-4-3 FK를 먼저 지운다) |
| B-4-1 | refund `ADD COLUMN` | 2026-11-02 00:19 | OK (0.07s) | `ALTER TABLE membership_refund DROP COLUMN membership_payment_attempt_id;`(B-4-3·B-4-2를 먼저 되돌린 뒤) |
| B-4-2 | refund `ADD INDEX` 2개 | 2026-11-02 00:19 | OK (0.06s) | `ALTER TABLE membership_refund DROP INDEX idx_membership_refund_payment_id_status, DROP INDEX idx_membership_refund_attempt_requester_status;` |
| B-4-3 | refund `ADD CONSTRAINT` FK | 2026-11-02 00:19 | OK (0.06s) | `ALTER TABLE membership_refund DROP FOREIGN KEY fk_membership_refund_attempt;` |

적용 순서: B-0 → B-1 → B-2~B-4 한 문장씩(기록표) → 10-B 첫 스냅숏 → 2b 이미지 배포(기동 로그에 `alter table`·`create table` 없음) →
10-B 둘째 스냅숏 diff 없음 → 스모크(`GET /api/billing-keys/me` 200, 기존 결제·탈퇴 API 1건씩). 10-B 기대에 "기존 컬럼 4개 그대로 있음,
`attempt_count IS_NULLABLE = YES`"가 더해진다. 이 테이블을 읽는 운영 코드가 없으므로 DDL 동안 구 버전 앱이 떠 있어도 영향이 없다.

| 롤백 상황 | 조치 |
| --- | --- |
| Expand 중 한 문장 실패 | 그 문장은 적용되지 않았다(문장 단위 원자성). 원인을 고쳐 그 문장부터 재개하거나 기록표의 성공한 문장을 역순으로 되돌린다 |
| 2b 배포 뒤 앱 문제 | 이전 이미지 재배포. Expand가 지운 것이 없어 스키마는 그대로 둬도 된다. 스키마까지 되돌리려면 기록표 역순 |
| 데이터 복구 | B-0 덤프 복원 |

**운영 실행 기록 (2026-11-02, 배포 담당).** 시각은 KST다(서버 출력은 UTC 2026-11-01 15:19).

- 사전: `backup.sh pre-deploy ddl-337`로 덤프(`pre-deploy-20261101-151912-ddl-337.sql.gz`). B-1 결과 `payments` 0, `refunds` 0,
  `membership_payment_attempt` 없음, `attempt_count` NOT NULL, 두 테이블을 참조하는 FK는 `FK9j8qq6ag089ni2ixnxo59pqoj`
  (refund → payment) 하나.
- 적용: 절차("한 문장씩")와 달리 B-2-1 → B-2-2 → B-2-3 → B-3 → B-4-1 → B-4-2 → B-4-3 일곱 문장을 이 순서로 한 번의
  `mysql -vvv` 실행에 넣었다. 클라이언트는 첫 오류에서 멈추므로 `exit=0`은 일곱 문장이 순서대로 모두 실행됐다는 뜻이다.
  - B-3·B-4-1~B-4-3은 문장별 `Query OK`와 소요 시간을 출력으로 확인했다.
  - B-2-1~B-2-3은 출력을 보관하지 못했다. 성공은 `exit=0`과 스냅숏 ①로 판단했고, 소요 시간은 적지 않는다. 스냅숏 ①에서
    `attempt_count IS_NULLABLE = YES`(B-2-1), B-2-2가 추가한 `idempotency_key` 위의 `uk_membership_payment_idempotency_key`와
    `idx_membership_payment_membership_id_status`(B-2-3)를 확인했다. B-2-2는 컬럼 4개를 한 문장으로 추가하므로 함께 적용됐다.
- 확인: 적용 직후 10-B 스냅숏 ①에서 제약·인덱스가 기대대로 하나씩만 있고 `attempt_count IS_NULLABLE = YES`. PR #337이 머지된 뒤
  #341·#340까지 포함한 `c8ebda1` 이미지가 `healthy`(09:25 기동)가 된 다음 스냅숏 ②를 떴고 ①과 diff가 없었다. #341·#340은 엔티티
  컬럼·제약을 바꾸지 않는다(10-E). 운영 `.env`에 `MEMBERSHIP_BILLING_ENABLED` 없음.
  - 기동 뒤 앱 로그에 SQL 오류와 `alter table`·`create table`이 없다. 5분 주기 멤버십 결제 스케줄러가 새 테이블을 조회한 뒤다.
  - 스모크: `GET /api/billing-keys/me`·`/api/memberships/me`·`/api/payments` 모두 토큰 없이 401, 회원 토큰으로 200. 탈퇴 API는
    실제 계정이 탈퇴되므로 호출하지 않고 위 스케줄러 로그로 대신했다.
- 사전 로컬 검증: 운영 스키마 덤프(`--no-data`)에 10-C를 적용하고 prod 프로필로 이전 앱(dev `33b8a3f`)과 #337(`a7b79ab`)을 번갈아
  기동해 기동 전후 스키마 덤프를 비교했다.
  - 10-C 적용 후 이전 앱 기동(선적용 뒤 머지 전 재시작 상황): 멤버십 테이블 변화 없음.
  - #337 기동(2회), 그 뒤 이전 앱으로 롤백 기동: 변화 없음. 기존 자동 이름 제약 5개(`UK9ssq…`·`FKbs1…`·`UKki2…`·`UKk0ask…`·
    `FK9j8q…`)와 새 이름 제약이 각각 하나씩만 있다.
  - DDL 없이 #337만 기동: Hibernate가 시도 테이블과 FK는 만들지만 `attempt_count`는 NOT NULL로 남아 새 회차 INSERT가 실패한다.
    10-C를 머지 전에 해야 하는 이유다.

### 10-D. Contract — 기존 컬럼 4개 제거 (별도 승인 절차. 2b 배포 절차가 아니다)

| 조건(모두 충족해야 실행) | 확인 |
| --- | --- |
| C-조건1 | 2b 배포 뒤 앱 정상 기동·스모크 통과·회귀 없음(기간은 배포 담당이 정함) |
| C-조건2 | `membership_payment`·`membership_refund` **0건**. 행이 있으면 컬럼 삭제 금지 — 보관 테이블로 복사한 뒤 별도 결정 |
| C-조건3 | 두 테이블을 참조하는 외부 FK가 기대(refund → payment)뿐 |
| C-조건4 | B-0과 같은 방식의 새 백업 |
| C-조건5 | 배포 담당 승인 기록 — "빈 테이블에 대한 승인된 스키마 정리"로 명시 |
| C-조건6 | 실행 기록표(아래) 작성 |

```sql
-- C-1 제거할 FK 이름 조회 (Hibernate 생성 이름은 환경마다 다르다)
SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME
  FROM information_schema.KEY_COLUMN_USAGE
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'membership_payment'
   AND REFERENCED_TABLE_NAME IS NOT NULL;                             -- billing_key_id의 FK 이름

-- C-2 FK 제거 (컬럼을 지우기 전에)
ALTER TABLE membership_payment DROP FOREIGN KEY <C-1에서 찾은 이름>;

-- C-3 기존 컬럼 4개 제거 (컬럼만의 인덱스·UK는 함께 사라진다)
ALTER TABLE membership_payment
  DROP COLUMN billing_key_id,
  DROP COLUMN portone_payment_id,
  DROP COLUMN attempt_count,
  DROP COLUMN last_attempt_at;

-- C-4 (B-4-1을 NULL로 넣었던 경우에만) 기존 행 처리 결정 뒤
ALTER TABLE membership_refund MODIFY membership_payment_attempt_id BIGINT NOT NULL;

-- C-5 10-B 스냅숏 diff (제거 컬럼 4개 없음, 나머지 동일)
```

| 순번 | 문장 | 실행 시각 | 결과 | 되돌리기 |
| --- | --- | --- | --- | --- |
| C-2 | `DROP FOREIGN KEY` | | | `ALTER TABLE membership_payment ADD CONSTRAINT FKbs1bco2goim5r57oh0dadmxkx FOREIGN KEY (billing_key_id) REFERENCES billing_key (id);`(C-3 되돌린 뒤. **운영의 원래 이름 그대로** — 다른 이름으로 만들면 이전 앱 기동 때 FK가 하나 더 생길 수 있다) |
| C-3 | `DROP COLUMN` 4개 | | | 컬럼 재추가(`billing_key_id BIGINT NULL`, `portone_payment_id VARCHAR(255) NULL` + `UNIQUE KEY UK9ssqjft7pefm6m8uiec775bw8 (portone_payment_id)`, `attempt_count INT NULL`, `last_attempt_at DATETIME(6) NULL`). 제약 이름은 운영 원래 이름을 쓴다(2026-11-01 배포 담당 `SHOW CREATE TABLE` 기준). 데이터는 돌아오지 않는다 — C-조건2(0건)가 전제인 이유 |
| C-4 | `MODIFY … NOT NULL` | | | `MODIFY … BIGINT NULL` |

Contract 뒤 앱 롤백: 이전 앱(2b 이전)은 이 테이블을 읽거나 쓰는 코드가 없어 동작에 영향이 없다. 다만 이전 앱의 `ddl-auto: update`가
지운 컬럼을 다시 추가할 수 있다 — 롤백 보장이 아니라 부작용으로 기록한다.



### 10-E. 결제 3단계(#325) — DDL 없음, 신규 청구 플래그 확인

3단계는 2b(10-C)의 테이블·컬럼만 쓰고 **새 DDL이 없다.** 배포 전 확인은 플래그 하나다.

| 확인 | 기대 |
| --- | --- |
| 운영 `.env`의 `MEMBERSHIP_BILLING_ENABLED` | 없음 또는 `false`(기본 false) |
| 기동 뒤 10-B 스냅숏 diff | 3단계로 인한 변경 없음 |
| 기동 로그 | `membership_payment`·`membership_payment_attempt` 관련 `alter table`·`create table` 없음 |

플래그를 켜는 조건은 `docs/api/membership-first-charge.md`의 운영 활성화 게이트를 따른다. 현재 `deploy/compose.yml`의
`app.environment`는 `MEMBERSHIP_BILLING_ENABLED`를 전달하지 않아 운영 `.env`만 바꿔서는 켜지지 않는다(기본 false 유지).
켤 때 `MEMBERSHIP_BILLING_ENABLED: ${MEMBERSHIP_BILLING_ENABLED:-false}` 전달을 함께 추가한다. 플래그가 꺼져 있어도 이미 생긴 결제의
재조회·결과 반영·복구는 계속 돈다.
