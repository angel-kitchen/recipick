# 운영 배포 실행 안내

설계와 선택 근거는 `docs/deploy-strategy.md`, 구현 기록과 검증 결과는 `docs/deploy-implementation.md`에 있다. 이 문서는 서버와 파이프라인을 실제로 세우고 운영하는 순서만 적는다.

## 1. 서버 배치

```
/opt/recipick/
├── .env                  .env.example을 복사해 채운다. 권한 600. 배포가 건드리지 않는다
├── certbot/{www,conf}    인증서. 서버에서만 생긴다
├── backups/              덤프. 권한 700. pre-deploy 최근 10개, nightly 최근 7개
├── releases/<SHA>/       배포마다 받은 deploy/ 묶음. 최근 5개와 현재·이전 태그를 남긴다
└── compose*.yml, nginx/, monitoring/, scripts/   현재 태그의 묶음과 똑같이 맞춘 운영 설정
```

워크플로가 저장소의 `deploy/`를 S3에 올리고, SSM 명령이 잠금을 잡은 뒤 `releases/<SHA>`에 푼다. 그다음 `deploy.sh`가 운영 설정을 그 묶음과 **정확히 같게** 바꾼다. 저장소에서 지우거나 이름을 바꾼 파일은 서버에서도 사라진다. `.env`·`certbot/`·`backups/`·`releases/`만 보존한다. 서버에서 직접 고친 설정은 다음 배포에 덮어써지므로 설정은 저장소에서 고친다.

## 2. AWS 준비 (한 번)

콘솔에서 만드는 순서, 이름, 정책 JSON은 `deploy/aws-setup.md`에 있다. 예산 알림을 가장 먼저 만든다. 아래 표는 요약이다.

| 항목 | 내용 |
| --- | --- |
| EC2 | Ubuntu 24.04, t3.medium, 루트 볼륨 30GB gp3, Elastic IP. 보안 그룹 인바운드는 80·443만(22 없음) |
| 인스턴스 프로파일 | `AmazonSSMManagedInstanceCore` + ECR pull + `s3:GetObject`(`deploy/*`) + `s3:PutObject`(`backups/*`) |
| ECR | 저장소 `recipick`. 수명 정책으로 최근 10개만 유지 |
| S3 | 버킷 하나. `deploy/`(설정 묶음)와 `backups/`(nightly 덤프). 퍼블릭 접근 차단, 30일 뒤 만료 |
| GitHub OIDC | 배포 역할 `recipick-github-deploy`. 신뢰 정책은 dev·main 브랜치만. ECR push, `s3:PutObject`(`deploy/*`), `ssm:SendCommand`, `ssm:GetCommandInvocation` |
| 팀원 | 관리자 그룹(배포 담당 + 대리 1명)만 Session Manager와 백업 다운로드. 나머지는 Actions 로그로 본다(`aws-setup.md` 10절) |

## 3. 레포 변수 (Settings → Secrets and variables → Actions → Variables)

시크릿은 없다. 전부 변수다.

| 변수 | 값 |
| --- | --- |
| `DEPLOY_BRANCH` | `dev`. 11/9에 `main` |
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_DEPLOY_ROLE_ARN` | 배포 역할 ARN |
| `ECR_REPOSITORY` | `recipick` |
| `DEPLOY_BUCKET` | S3 버킷 이름 |
| `EC2_INSTANCE_ID` | `i-...` |

`DEPLOY_BRANCH`를 넣는 순간부터 그 브랜치에 머지될 때마다 배포가 돈다.

## 4. 서버 초기화와 첫 배포

1. Session Manager로 접속해 `sudo -i`.
   ```bash
   apt-get update && apt-get install -y docker.io docker-compose-v2 awscli rsync
   systemctl enable --now docker
   docker network create recipick
   mkdir -p /opt/recipick/releases
   ```
2. 레포 변수 6개를 넣고 dev에 아무 커밋이나 머지한다. 이미지와 묶음이 올라가고 서버의 `releases/<SHA>`에 풀린다. 이 첫 실행은 `.env`가 없어 **실패로 끝나는 것이 정상**이다.
3. 그 묶음으로 `.env`를 만든다.
   ```bash
   cd /opt/recipick
   cp releases/<SHA>/.env.example .env && chmod 600 .env
   vi .env    # IMAGE=<계정ID>.dkr.ecr.ap-northeast-2.amazonaws.com/recipick, 비밀번호, JWT_SECRET, PORTONE_*, BACKUP_BUCKET
   ```
4. 같은 묶음으로 배포한다. MySQL·Redis가 없으면 스크립트가 먼저 띄우고 healthy가 될 때까지 기다린다.
   ```bash
   bash releases/<SHA>/scripts/deploy.sh <SHA>
   ```
5. 운영 관리자 계정을 SQL로 한 번 만든다. 로컬 관리자 시드(`data-admin-local.sql`)의 비밀번호 해시는 쓰지 않는다. 비밀번호 해시는 로컬에서 앱의 인코더로 만들어 가져온다.
6. `docs/deploy-strategy.md` 10-1 체크리스트대로 확인한다. 가장 먼저 `curl http://<Elastic IP>/api/categories`.

이후 배포는 워크플로가 한다. 서버에서 수동으로 배포할 때도 같은 명령을 쓴다.

### 배포 스크립트가 하는 일

| 순서 | 내용 | 실패하면 |
| --- | --- | --- |
| 1 | 잠금(워크플로가 다운로드 전에 잡은 것을 물려받는다) | 다른 배포가 진행 중이면 중단 |
| 2 | `.env` 필수 값 검사 | 빈 값의 이름을 대고 중단. 아무것도 바꾸지 않는다 |
| 3 | MySQL·Redis 준비 대기 | 중단 |
| 4 | 배포 전 덤프(`scripts/backup.sh pre-deploy`) | 중단 |
| 5 | ECR 로그인, 이미지 pull | 중단 |
| 6 | 운영 설정 교체, 태그 갱신, compose 검사, app·nginx 기동, `nginx -t`, reload, 앱 healthy, nginx 경유 응답(`PROXY_CHECK_URL`) | 이전 태그의 이미지와 설정으로 6을 다시 밟고 exit 1 |
| 7 | 옛 이미지·묶음 정리 | 경고만 남긴다. 배포 결과에 영향 없음 |

재시작 방식이라 새 앱이 뜨는 동안(보통 15~30초)은 502가 날 수 있다. 헬스 체크가 끝내 실패하면 그 시간(최대 3분)과 복구 시간 동안 응답이 없다.

## 5. HTTPS 전환 (10/30 목표)

1. 도메인의 A 레코드를 Elastic IP로 연결한다.
2. 발급 시험. 실제 발급은 도메인당 주 5회 제한이 있으므로 먼저 `--dry-run`으로 확인한다.
   ```bash
   cd /opt/recipick
   docker compose --profile certbot run --rm certbot certonly --webroot -w /var/www/certbot -d <도메인> --email <메일> --agree-tos --dry-run
   ```
3. 성공하면 `--dry-run`을 빼고 실제 발급한다.
4. 저장소에서 `nginx/conf.d/app-https.conf.disabled`의 `DOMAIN`을 실제 도메인으로 바꾸고, 이름을 `app-https.conf`로, `app.conf`는 `app.conf.disabled`로 바꿔 머지한다. 배포가 옛 `app.conf`를 지우고 `nginx -t` 후 reload한다. 설정이 틀리면 이전 설정으로 돌아간다.
5. 서버 `.env`의 `PROXY_CHECK_URL`을 `https://<도메인>/api/categories`로, `CORS_ALLOWED_ORIGINS`를 프론트 도메인으로 바꾼다. 이후 배포는 인증서까지 확인한다.
6. 확인한다.
   - `curl -I http://<도메인>/api/categories`가 301로 https를 가리킨다
   - `curl -vI https://<도메인>/api/categories`의 인증서 발급자가 Let's Encrypt이고 만료일이 90일 뒤다
   - 로그인 응답의 리프레시 토큰 쿠키에 `Secure`가 붙는다
   - 그 쿠키로 토큰 재발급이 200이다
7. 갱신 cron(root): `0 4 * * 1 cd /opt/recipick && docker compose --profile certbot run --rm certbot renew -q && docker compose exec -T nginx nginx -s reload`
8. PortOne 웹훅 URL 등록은 결제 담당에게 도메인을 알린다.

## 6. 백업

| 종류 | 언제 | 어디에 | 보관 |
| --- | --- | --- | --- |
| pre-deploy | 배포마다 스크립트가 | 서버 `backups/`만 | 최근 10개 |
| nightly | 매일 04:30 cron | 서버 `backups/` + S3 `backups/` | 서버 최근 7개, S3 30일 |

nightly cron(root, `crontab -e`):
```
30 4 * * * /opt/recipick/scripts/backup.sh nightly >> /var/log/recipick-backup.log 2>&1
```

`backup.sh`는 덤프가 끝까지 기록됐는지(마지막 줄의 완료 표시), S3에 올라간 크기가 로컬과 같은지 확인한다. 실패하면 0이 아닌 코드로 끝나고 `.env`의 `ALERT_WEBHOOK_URL`(Discord·Slack 웹훅)로 알린다. 덤프 파일은 권한 600이다.

복원 점검(주 1회, 그리고 시연 전): 운영 DB와 분리된 임시 MySQL에 실제로 복원해 본다.
```bash
/opt/recipick/scripts/restore-check.sh /opt/recipick/backups/<파일>.sql.gz
```

S3의 백업은 관리자 그룹(`aws-setup.md` 10절)만 내려받을 수 있다. 서버를 잃었을 때는 관리자가 콘솔에서 최신 nightly를 받아 새 서버에서 7절의 전체 복원을 한다.

## 7. 롤백과 복구

| 상황 | 방법 |
| --- | --- |
| 자동 배포가 실패 | 스크립트가 이전 태그의 이미지와 설정으로 이미 되돌렸다. Actions 로그의 서버 출력에서 원인을 본다 |
| 배포는 성공했지만 기능이 잘못됨 | `bash /opt/recipick/releases/<이전 SHA>/scripts/deploy.sh <이전 SHA>`. 묶음이 정리되어 없으면 `/opt/recipick/scripts/deploy.sh <이전 SHA>`(현재 설정으로 이미지만 바꾼다) |
| 스키마가 문제(아래) | 수동 |

**스키마 복구.** `ddl-auto: update`는 컬럼을 추가만 하므로 이미지를 되돌려도 스키마는 남는다. 대부분은 남아도 문제가 없다. 이전 버전이 모르는 NOT NULL 컬럼이 생겨 INSERT가 실패할 때만 손댄다.

1. 먼저 그 컬럼만 고치는 것을 택한다. 덤프 이후의 주문·결제 데이터를 잃지 않는다.
   ```bash
   docker exec -it recipick-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
   # ALTER TABLE <테이블> MODIFY <컬럼> <타입> NULL;  또는 DEFAULT 지정
   ```
2. 전체 복원은 데이터가 깨졌을 때만 한다. 덤프 이후의 데이터는 사라진다.
   ```bash
   cd /opt/recipick
   ./scripts/restore-check.sh backups/pre-deploy-<시각>-<문제 배포 SHA>.sql.gz   # 먼저 덤프가 복원되는지 확인
   docker compose stop app
   docker exec recipick-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -e "DROP DATABASE \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\`"'
   gunzip -c backups/<파일>.sql.gz | docker exec -i recipick-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql "$MYSQL_DATABASE"'
   bash releases/<이전 SHA>/scripts/deploy.sh <이전 SHA>
   ```
   pre-deploy 덤프의 이름 끝은 **그 덤프 직후에 배포한 SHA**다. 문제 배포의 SHA가 붙은 덤프가 문제 배포 직전 상태다.

## 8. 자주 쓰는 명령

| 할 일 | 명령 |
| --- | --- |
| 앱 로그 | `docker logs -f recipick-app` |
| 현재 태그 | `grep ^IMAGE_TAG= /opt/recipick/.env` |
| 모니터링 올리기 | `docker compose -f compose.monitoring.yml up -d`. 부하 테스트 중에도 켜 둔다(Before·After 같은 조건) |
| Grafana 접속 | 로컬에서 `aws ssm start-session --target <INSTANCE_ID> --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters host=<grafana 컨테이너 IP>,portNumber=3000,localPortNumber=3000` 후 `http://localhost:3000`. 컨테이너 IP는 `docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' recipick-grafana` |
| infra 설정 변경 적용 | `docker compose -f compose.infra.yml up -d`. 해당 서비스가 잠깐 재시작되므로 시연 시간을 피한다 |
| 메모리·디스크 확인 | `docker stats --no-stream`, `df -h /`, `docker system df` |
