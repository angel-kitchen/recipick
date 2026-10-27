# 운영 배포 실행 안내

설계와 선택 근거는 `docs/deploy-strategy.md`, 구현 기록과 검증 결과는 `docs/deploy-implementation.md`에 있다. 이 문서는 서버와 파이프라인을 실제로 세우는 순서만 적는다.

## 1. 서버 배치

```
/opt/recipick/
├── compose.yml, compose.infra.yml, compose.monitoring.yml
├── .env                  .env.example을 복사해 채운다. 권한 600. 배포가 덮어쓰지 않는다
├── nginx/  monitoring/  scripts/
├── certbot/{www,conf}    인증서. 서버에서만 생긴다
└── backups/              배포 전 덤프. 스크립트가 만들고 최근 10개만 남긴다
```

워크플로가 저장소의 `deploy/`를 S3에 올리고 SSM 명령이 `/opt/recipick`에 펼친다. `.env`·`certbot/`·`backups/` 외의 파일은 저장소가 원본이므로, 서버에서 직접 고치면 다음 배포 때 덮어써진다. 설정을 바꾸려면 저장소에서 고치고 배포한다.

## 2. AWS 준비 (한 번)

콘솔에서 만드는 순서, 이름, 정책 JSON은 `deploy/aws-setup.md`에 있다. 아래 표는 요약이다.

| 항목 | 내용 |
| --- | --- |
| EC2 | Ubuntu 24.04, t3.medium, 루트 볼륨 30GB gp3, Elastic IP. 보안 그룹 인바운드는 80·443만(22 없음) |
| 인스턴스 프로파일 | `AmazonSSMManagedInstanceCore` + ECR pull(`ecr:GetAuthorizationToken`, `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchCheckLayerAvailability`) + `s3:GetObject`(버킷의 `deploy/*`) + 덤프 cron용 `s3:PutObject`(버킷의 `backups/*`) |
| ECR | 저장소 `recipick`. 수명 정책으로 태그 있는 이미지 최근 10개만 유지 |
| S3 | 버킷 하나. `deploy/`(설정 파일)와 `backups/`(덤프). 퍼블릭 접근 차단 |
| GitHub OIDC | IAM 자격 증명 공급자 `token.actions.githubusercontent.com`. 배포 역할의 신뢰 정책 `sub`는 `repo:angel-kitchen/recipick:ref:refs/heads/dev`(11/9에 main으로 바꾸거나 둘 다). 권한은 ECR push(해당 저장소), `s3:PutObject`(`deploy/*`), `ssm:SendCommand`(해당 인스턴스와 `AWS-RunShellScript` 문서), `ssm:GetCommandInvocation` |
| 팀원 IAM | 그룹에 `ssm:StartSession`(해당 인스턴스)과 `ssm:TerminateSession`. 사용자 5명. 서버 접속은 콘솔의 Session Manager |

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

## 4. 서버 초기화 (Session Manager로 접속, root)

```bash
apt-get update && apt-get install -y docker.io docker-compose-v2 awscli
systemctl enable --now docker
docker network create recipick
mkdir -p /opt/recipick && cd /opt/recipick

# 저장소의 deploy/ 가져오기. 워크플로를 한 번 돌리면(서버가 없어 배포 단계는 실패해도) S3에 올라온다
aws s3 cp s3://<버킷>/deploy/<커밋 SHA>.tar.gz /tmp/deploy.tar.gz && tar -xzf /tmp/deploy.tar.gz -C /opt/recipick

cp .env.example .env && chmod 600 .env
vi .env     # IMAGE=<계정ID>.dkr.ecr.ap-northeast-2.amazonaws.com/recipick, 비밀번호·JWT_SECRET·PORTONE_API_SECRET

docker compose -f compose.infra.yml up -d          # MySQL·Redis. 이후 배포에서 건드리지 않는다
./scripts/deploy.sh <커밋 SHA>                      # 첫 배포. 이후는 워크플로가 한다
```

확인은 `docs/deploy-strategy.md` 10-1 체크리스트대로 한다. 가장 먼저 `curl http://<Elastic IP>/api/categories`.

## 5. HTTPS 전환 (10/30 목표)

1. 도메인의 A 레코드를 Elastic IP로 연결한다.
2. 발급 시험. 실제 발급은 도메인당 주 5회 제한이 있으므로 먼저 `--dry-run`으로 설정을 확인한다.
   ```bash
   docker compose --profile certbot run --rm certbot certonly --webroot -w /var/www/certbot -d <도메인> --email <메일> --agree-tos --dry-run
   ```
3. 성공하면 `--dry-run`을 빼고 실제 발급한다.
4. 저장소에서 `nginx/conf.d/app-https.conf.disabled`의 `DOMAIN`을 실제 도메인으로 바꾸고 이름을 `app-https.conf`로, `app.conf`는 `app.conf.disabled`로 바꿔 배포한다. 서버에서 직접 바꾸면 다음 배포에 덮어써진다.
5. 배포 뒤 `docker compose exec nginx nginx -s reload`. 재시작 배포는 nginx를 재생성하지 않으므로 reload가 필요하다.
6. 갱신 cron(root): `0 4 * * 1 cd /opt/recipick && docker compose --profile certbot run --rm certbot renew -q && docker compose exec -T nginx nginx -s reload`

## 6. 덤프 cron (root)

배포 전 덤프는 스크립트가 한다. 매일 밤 덤프는 cron으로 S3에 보낸다.

```
30 4 * * * cd /opt/recipick && f=backups/nightly-$(date +\%F).sql.gz && docker exec recipick-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction "$MYSQL_DATABASE"' | gzip > $f && aws s3 cp $f s3://<버킷>/backups/
```

## 7. 자주 쓰는 명령

| 할 일 | 명령 |
| --- | --- |
| 앱 로그 | `docker logs -f recipick-app` |
| 수동 롤백 | `./scripts/deploy.sh <이전 커밋 SHA>` |
| 모니터링 올리기·내리기 | `docker compose -f compose.monitoring.yml up -d` / `down`. 부하 테스트 중에는 내린다 |
| 덤프 복원 | `gunzip -c backups/<파일> \| docker exec -i recipick-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql "$MYSQL_DATABASE"'` |
