# 정적 분석(SonarQube·JaCoCo)

백엔드 코드를 로컬 SonarQube로 분석하고, JaCoCo 테스트 커버리지를 함께 확인하는 방법과 첫 분석 결과를 정리한다. 진행 현황은 #424에서 추적한다.

## 1. 구성

| 도구 | 역할 | 설정 위치 |
| --- | --- | --- |
| SonarQube Community | 버그·취약점·코드 스멜·중복 분석, 규칙은 기본 Sonar way(Java 274개) | 로컬 Docker 컨테이너 |
| `org.sonarqube` Gradle 플러그인 | 분석 결과를 SonarQube 서버로 업로드 | `scripts/sonar.init.gradle` |
| JaCoCo | 테스트 커버리지 측정. `sonar` 실행 전에 `jacocoTestReport`가 XML·HTML 리포트를 만든다 | `scripts/sonar.init.gradle` |

- 분석 대상은 `src/main/java`이고 `frontend/`는 제외한다.
- 커버리지는 리팩토링 대상을 고를 때 참고하는 값이다. 목표 수치나 품질 게이트로 쓰지 않는다.
- 분석 서버는 로컬에만 띄운다. 코드는 외부로 나가지 않는다.
- 플러그인은 `build.gradle`에 넣지 않고 init 스크립트로 필요할 때만 붙인다. 평소 빌드와 CI는 Sonar·JaCoCo 없이 돈다.

## 2. 실행 방법

```bash
docker run -d --name sonarqube -p 127.0.0.1:9000:9000 sonarqube:community   # SonarQube 서버 실행(처음 한 번). 같은 네트워크에서 접속하지 못하게 로컬에만 연다
docker start sonarqube                                                       # 이미 만든 컨테이너를 다시 켤 때
docker rm -f sonarqube                                                       # 확인이 끝나면 컨테이너와 분석 기록을 지운다
```

1. http://localhost:9000 이 열리면 `admin` / `admin`으로 로그인하고, 안내에 따라 비밀번호를 바꾼다.
2. 오른쪽 위 프로필 → My Account → Security에서 토큰을 만든다. 종류는 Global Analysis Token으로 한다.
3. 아래 명령으로 테스트·커버리지·분석을 한 번에 실행한다. `./gradlew test`는 Testcontainers를 쓰므로 Docker가 켜져 있어야 한다.

```bash
export SONAR_TOKEN=<발급한 토큰>                              # 토큰은 커밋하지 않고 환경변수로만 넘긴다
./gradlew --init-script scripts/sonar.init.gradle sonar     # 테스트 → JaCoCo 리포트 → Sonar 업로드를 차례로 실행
```

전체 테스트는 약 5분, 분석은 약 1분 걸린다.

## 3. 결과 보는 곳

| 결과 | 위치 |
| --- | --- |
| Sonar 대시보드 | http://localhost:9000/dashboard?id=recipick |
| Sonar 이슈 목록 | 대시보드 → Issues. 왼쪽 필터에서 Software Quality·Severity로 거른다 |
| 중복 코드 | 대시보드 → Measures → Duplications |
| JaCoCo HTML 리포트 | `build/reports/jacoco/test/html/index.html`을 브라우저로 연다 |
| JaCoCo XML(Sonar가 읽는 파일) | `build/reports/jacoco/test/jacocoTestReport.xml` |

`build/`는 커밋하지 않는다. 분석 없이 커버리지만 보려면 `./gradlew --init-script scripts/sonar.init.gradle jacocoTestReport`를 실행한다.

## 4. 이슈 처리 기준

| 분류 | 처리 |
| --- | --- |
| Bug, Vulnerability | 전부 수정하거나, 오탐·안전하다는 근거를 남긴다 |
| Code Smell High 이상 | 수정 |
| Code Smell Medium 이하 | 미룬다(#424) |
| Security Hotspot | 한 건씩 검토 |
| 컨벤션과 충돌하는 규칙 | Won't Fix로 처리하고 근거를 남긴다 |

수정은 동작이 바뀌지 않는 범위로 한정한다. API 응답 형식과 에러 코드는 바꾸지 않는다.

## 5. 첫 분석 결과 (2026-11-03, `dev` `bbd0943`)

| 파일 | 코드 줄 | Bug | Vulnerability | Code Smell | Hotspot | 커버리지 | 중복률 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 664 | 30,113 | 8 | 2 | 5 | 0 | 88.6% | 0.8% |

15건 중 4건을 수정했고(#425), 11건은 근거를 남기고 수정하지 않았다.

| 규칙 | 위치 | 처리 | 근거 |
| --- | --- | --- | --- |
| S8696 값 타입 `==` 비교 | `MembershipChargeResultProcessor` | 수정 | `Objects.equals`로 변경, 동작 동일 |
| S3655 Optional 확인 없이 `get()` | `MembershipRefundExecutionFacade` | 수정 | 지역 변수로 받아 확인 후 사용 |
| S1845 상수·필드 이름 충돌 | `PortOneBillingPaymentRequest` | 수정 | `CURRENCY` → `CURRENCY_KRW` |
| S2699 단언 없는 테스트 | `CategoryServiceTest` | 수정 | 결과 검증 추가 |
| S4502 CSRF 비활성화 | `SecurityConfig` | Safe | Bearer 헤더 인증, 쿠키는 리프레시 전용 `SameSite=Strict`·`Path=/api/auth` ([인증 ADR 3-5](adr/authentication.md#3-5-쿠키cors와-브라우저-경계)) |
| S2068 비밀번호 하드코딩 의심 | `Member` | Safe | `"!withdrawn"`은 탈퇴 회원 로그인 차단 표식 |
| S8700 시간대 없는 `Duration` ×3 | 멤버십 결제·환불 Facade | Won't Fix | 한국 시간 `LocalDateTime` 저장 컨벤션, 서머타임 없음 |
| S2789 Optional null 검사 | `PointPolicyService` | Won't Fix | 의도된 방어 코드이고 `PointPolicyServiceTest`가 검증 |
| S2259 NPE 가능 | `AutoCartPreviewResponse` | 오탐 | `purchasable()`이 `selected != null`을 보장 |
| S2259 NPE 가능 | `RecipeBookmarkCreateResponse` | 오탐 | `@EmbeddedId`를 생성자에서 할당 |
| S2699 단언 없는 테스트 | `RecipickApplicationTests` | Won't Fix | 컨텍스트 로딩 확인용 |
| S8786 정규식 백트래킹 ×2 | `FakeAiClient` | 미룸 | Medium, 테스트·시연용 가짜 클라이언트 |

S4502(CSRF)·S2068(하드코딩 비밀번호)은 예전 버전에서는 Security Hotspot 규칙이지만, 이번에 쓴 `sonarqube:community`(Sonar way core)에서는 Vulnerability로 보고되어 표에도 그대로 옮겼다(Hotspot 0건). Safe 처리는 분류와 관계없이 같다.

로컬 SonarQube는 사람마다 따로 띄우므로 Won't Fix·Safe 표시가 공유되지 않는다. 다시 분석하면 위 11건이 계속 보이는 것이 정상이며, 판단 근거는 이 표를 기준으로 한다.
