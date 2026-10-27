# AWS 콘솔 설정 순서

`deploy/README.md` 2절(AWS 준비)을 콘솔에서 그대로 누를 수 있게 순서·이름·정책 JSON으로 풀어 적었다. 브라우저 에이전트(Claude in Chrome)에게 이 문서를 그대로 주고 진행할 수 있다. 끝나면 9절의 기록표를 채운다.

## 0. 작업 규칙

- 리전은 항상 **서울(ap-northeast-2)**. 화면 오른쪽 위 리전이 다르면 먼저 바꾼다.
- 이름은 아래에 적힌 그대로 쓴다. 다른 이름을 쓰면 `README.md`·`.env.example`·레포 변수와 어긋난다.
- **만들지 않는 것**: IAM 액세스 키, EC2 키 페어, 22번 포트 인바운드 규칙. 배포는 OIDC, 접속은 Session Manager만 쓴다.
- 비용이 드는 리소스는 EC2(t3.medium)·EBS 30GB·Elastic IP 셋뿐이다. 다른 유료 옵션(상세 모니터링, 프로비저닝 IOPS, NAT 게이트웨이 등)은 켜지 않는다.
- 선택지가 문서와 다르게 보이거나 경고가 뜨면 멈추고 묻는다. 삭제 작업은 이 문서에 없다.
- `ACCOUNT_ID`, `BUCKET`, `INSTANCE_ID`는 각 단계에서 알게 되는 값으로 바꿔 넣는다.

## 1. 계정 확인 (5분)

1. 콘솔 오른쪽 위 계정 메뉴에서 **계정 ID(12자리)** 를 적어 둔다 → `ACCOUNT_ID`.
2. **Billing and Cost Management → Credits**에서 크레딧 잔액(약 100달러)과 만료일을 확인한다.
3. 같은 화면에서 계정이 **Free plan**인지 **Paid plan**인지 본다. Free plan에서 t3.medium이나 Elastic IP 생성이 막히면 Paid plan으로 전환한다. 전환해도 크레딧은 그대로 쓰인다. 전환은 비용이 바로 발생하는 일이 아니므로 막혔을 때만 한다.
4. 루트 사용자 MFA가 꺼져 있으면 켠다(IAM → 대시보드 보안 권장 사항).

예상 비용(서울 리전, 온디맨드): t3.medium 약 0.052달러/시간 + gp3 30GB 약 0.1달러/일 + Elastic IP 0.005달러/시간. 하루 약 1.5달러, 10/27~11/13 약 27달러. 크레딧 안에서 끝난다.

## 2. ECR 저장소 (5분)

1. **Amazon ECR → Private registry → Repositories → Create repository**
   - Repository name: `recipick`
   - Image tag mutability: **Mutable** (같은 커밋을 다시 배포할 때 같은 태그를 다시 올린다)
   - Encryption: AES-256(기본)
2. 만든 저장소의 **URI**를 적어 둔다 → `ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/recipick`. `.env`의 `IMAGE` 값이다.
3. 저장소 → **Lifecycle policy → Create rule** (또는 JSON으로 편집)
   ```json
   {
     "rules": [
       {
         "rulePriority": 1,
         "description": "최근 10개만 유지",
         "selection": { "tagStatus": "any", "countType": "imageCountMoreThan", "countNumber": 10 },
         "action": { "type": "expire" }
       }
     ]
   }
   ```

## 3. S3 버킷 (5분)

1. **S3 → Create bucket**
   - Bucket name: `recipick-deploy-ACCOUNT_ID` (버킷 이름은 전 세계에서 유일해야 해서 계정 ID를 붙인다) → `BUCKET`
   - Region: ap-northeast-2
   - Object Ownership: ACLs disabled(기본)
   - **Block all public access: 켬(기본)**
   - Bucket Versioning: Disable
   - Encryption: SSE-S3(기본)
2. 버킷 → **Management → Create lifecycle rule**
   - Rule name: `expire-30d`
   - Apply to all objects in the bucket
   - Lifecycle rule actions: **Expire current versions of objects** → 30 days
   (배포 설정 묶음과 덤프가 30일 뒤 지워진다. 더 오래 둘 덤프는 로컬로 받는다)

## 4. EC2용 IAM 역할 (10분)

### 4-1. 정책
**IAM → Policies → Create policy → JSON** 탭에 붙인다. 이름 `recipick-ec2-policy`.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Sid": "EcrAuth", "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    {
      "Sid": "EcrPull",
      "Effect": "Allow",
      "Action": ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"],
      "Resource": "arn:aws:ecr:ap-northeast-2:ACCOUNT_ID:repository/recipick"
    },
    { "Sid": "S3DeployRead", "Effect": "Allow", "Action": "s3:GetObject", "Resource": "arn:aws:s3:::BUCKET/deploy/*" },
    { "Sid": "S3BackupWrite", "Effect": "Allow", "Action": "s3:PutObject", "Resource": "arn:aws:s3:::BUCKET/backups/*" }
  ]
}
```

### 4-2. 역할
**IAM → Roles → Create role**
- Trusted entity type: **AWS service**, Use case: **EC2**
- Permissions: `AmazonSSMManagedInstanceCore`(AWS 관리형)와 `recipick-ec2-policy` 두 개를 붙인다
- Role name: `recipick-ec2-role`

콘솔이 같은 이름의 인스턴스 프로파일을 함께 만든다.

## 5. EC2 인스턴스 (15분)

**EC2 → Instances → Launch instances**

| 항목 | 값 |
| --- | --- |
| Name | `recipick-server` |
| AMI | **Ubuntu Server 24.04 LTS (HVM), SSD Volume Type**, 64-bit (x86). SSM Agent가 들어 있다 |
| Instance type | **t3.medium** |
| Key pair | **Proceed without a key pair** (키 없이 진행. Session Manager로 접속한다) |
| Network settings → Edit | VPC 기본값, Subnet 기본값(아무 가용 영역), **Auto-assign public IP: Enable** |
| Firewall | **Create security group**, 이름 `recipick-web`, 설명 `recipick HTTP/HTTPS` |
| 인바운드 규칙 | 기본으로 들어 있는 **SSH(22) 규칙은 삭제**. 추가: HTTP 80 Anywhere-IPv4(0.0.0.0/0), HTTPS 443 Anywhere-IPv4(0.0.0.0/0) |
| Configure storage | **30 GiB, gp3** (기본 8 GiB를 30으로 바꾼다. IOPS·처리량은 기본값) |
| Advanced details → IAM instance profile | `recipick-ec2-role` |
| 그 외 | 전부 기본값. Detailed CloudWatch monitoring은 켜지 않는다 |

Launch 후 인스턴스 ID(`i-...`)를 적어 둔다 → `INSTANCE_ID`.

## 6. Elastic IP (5분)

1. **EC2 → Network & Security → Elastic IPs → Allocate Elastic IP address** → Amazon의 IPv4 주소 풀, 서울 리전 → Allocate
2. 만든 주소 선택 → **Actions → Associate Elastic IP address** → Instance: `recipick-server` → Associate
3. 주소를 적어 둔다 → `ELASTIC_IP`. 도메인 A 레코드와 `curl` 확인에 쓴다.

Elastic IP는 인스턴스에 붙어 있는 동안에도 시간당 요금이 붙는다. 인스턴스를 지울 때 함께 릴리스한다.

## 7. GitHub Actions용 OIDC와 배포 역할 (15분)

### 7-1. 자격 증명 공급자
**IAM → Identity providers → Add provider**
- Provider type: **OpenID Connect**
- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

### 7-2. 정책
**IAM → Policies → Create policy → JSON**. 이름 `recipick-github-deploy-policy`. `INSTANCE_ID`는 5절의 값.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Sid": "EcrAuth", "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    {
      "Sid": "EcrPush",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-2:ACCOUNT_ID:repository/recipick"
    },
    { "Sid": "S3DeployWrite", "Effect": "Allow", "Action": "s3:PutObject", "Resource": "arn:aws:s3:::BUCKET/deploy/*" },
    {
      "Sid": "SsmRun",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:ACCOUNT_ID:instance/INSTANCE_ID",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ]
    },
    { "Sid": "SsmRead", "Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*" }
  ]
}
```

### 7-3. 역할
**IAM → Roles → Create role**
- Trusted entity type: **Web identity**
- Identity provider: `token.actions.githubusercontent.com`, Audience: `sts.amazonaws.com`
- GitHub organization: `angel-kitchen`, GitHub repository: `recipick`, GitHub branch: `dev`
- Permissions: `recipick-github-deploy-policy`
- Role name: `recipick-github-deploy`

만든 뒤 **Trust relationships → Edit trust policy**를 아래로 바꿔 `main`도 허용한다(11/9 승격 뒤 main에서 배포하기 위해). 다른 브랜치·다른 저장소·PR 이벤트는 역할을 가정할 수 없다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": [
            "repo:angel-kitchen/recipick:ref:refs/heads/dev",
            "repo:angel-kitchen/recipick:ref:refs/heads/main"
          ]
        }
      }
    }
  ]
}
```

역할의 **ARN**(`arn:aws:iam::ACCOUNT_ID:role/recipick-github-deploy`)을 적어 둔다 → `AWS_DEPLOY_ROLE_ARN`.

## 8. 예산 알림 (5분)

**Billing and Cost Management → Budgets → Create budget → Use a template → Monthly cost budget**
- Budget name: `recipick-monthly`
- Budgeted amount: `60`(USD)
- Email recipients: 본인 메일
- 기본 템플릿이 실제 85%·100%, 예측 100%에서 알린다.

## 9. 기록표 (끝나면 채운다)

| 이름 | 값 | 쓰는 곳 |
| --- | --- | --- |
| `ACCOUNT_ID` | | 정책 JSON, `.env`의 `IMAGE` |
| ECR URI | `ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/recipick` | 서버 `.env`의 `IMAGE` |
| `BUCKET` | `recipick-deploy-ACCOUNT_ID` | 레포 변수 `DEPLOY_BUCKET` |
| `INSTANCE_ID` | `i-...` | 레포 변수 `EC2_INSTANCE_ID` |
| `ELASTIC_IP` | | DNS A 레코드, `curl http://ELASTIC_IP/api/categories` |
| 배포 역할 ARN | `arn:aws:iam::ACCOUNT_ID:role/recipick-github-deploy` | 레포 변수 `AWS_DEPLOY_ROLE_ARN` |

레포 변수 6개의 값은 이 표에서 나온다(`README.md` 3절). `DEPLOY_BRANCH=dev`, `AWS_REGION=ap-northeast-2`, `ECR_REPOSITORY=recipick`은 고정값이다.

## 10. 접속 확인

1. **Systems Manager → Fleet Manager**(또는 Session Manager → Start session)에서 `recipick-server`가 **Online**으로 보여야 한다. 인스턴스 기동 후 몇 분 걸릴 수 있다. 보이지 않으면 5절의 IAM instance profile이 붙어 있는지 확인하고 인스턴스를 재부팅한다.
2. **Start session**으로 셸이 열리면 `sudo -i` 후 `README.md` 4절(서버 초기화)로 넘어간다.

## 11. 나중에: 팀원 접속 (팀 결정 후)

팀원 5명이 서버 로그를 보려면 IAM 사용자가 필요하다. 그룹 `recipick-team`에 아래 정책을 붙이고 사용자를 추가한다. 콘솔 로그인만 주고 액세스 키는 만들지 않는다. MFA를 켜게 한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:StartSession",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:ACCOUNT_ID:instance/INSTANCE_ID",
        "arn:aws:ssm:ap-northeast-2::document/SSM-SessionManagerRunShell"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["ssm:TerminateSession", "ssm:ResumeSession"],
      "Resource": "arn:aws:ssm:*:*:session/${aws:username}-*"
    },
    {
      "Effect": "Allow",
      "Action": ["ssm:DescribeSessions", "ssm:GetConnectionStatus", "ssm:DescribeInstanceInformation", "ec2:DescribeInstances"],
      "Resource": "*"
    }
  ]
}
```
