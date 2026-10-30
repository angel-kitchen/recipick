# 모니터링

>  2-C 단계(Prometheus·Grafana 구성과 기본 대시보드)까지의 기록입니다. 부하 테스트·병목 개선의 Before → After 수치는 이 대시보드로 확인합니다.

## 1. 엔드포인트 공개 규칙

### 상황
- 서비스 상태(헬스 체크)와 지표(요청량·지연·에러율)를 수집해야 한다.
- 이 정보를 인터넷에 열면 내부 상태가 노출된다. 반면 배포 서버의 헬스 체크와 Prometheus는 접근할 수 있어야 한다.
- 이 프로젝트의 `SecurityConfig`는 `anyRequest().denyAll()`이라 `/actuator/**`는 기본적으로 막혀 있다.

### 후보
| 후보 | 장점 | 단점 |
| --- | --- | --- |
| 같은 포트에서 `/actuator/health`만 공개, 지표는 인증 | 포트가 하나 | Prometheus가 인증 토큰을 가져야 하고, 인증 구조가 복잡해짐 |
| **관리 포트 분리(선택)** | 앱 포트로는 지표가 절대 열리지 않음, Prometheus는 인증 없이 내부에서 수집 | 포트가 하나 늘고, 서버 보안 그룹에서 관리 포트를 내부에만 열어야 함 |

### 선택과 이유
- 관리 포트(기본 `8081`, 환경 변수 `MANAGEMENT_PORT`)에서만 `health`, `prometheus`를 연다.
- `SecurityConfig`에는 `EndpointRequest.to("health", "prometheus")`로 허용한다. 이 규칙은 관리 포트로 들어온 요청에만 적용되어, 앱 포트(8080)로는 열리지 않는다.
- 헬스 체크 응답은 `show-details: never`로 `{"status":"UP"}`만 보여 준다(DB·Redis 연결 정보 등 세부 정보 비노출).
- 노출하는 엔드포인트는 `health`, `prometheus` 두 개로 한정한다.

### 트레이드오프
- 관리 포트는 인증이 없으므로 **네트워크로만 보호된다.** 서버에서 8081을 인터넷에 열면 지표가 그대로 노출된다. 보안 그룹에서 로드밸런서·Prometheus가 있는 내부 대역에만 허용해야 한다.
- 배포 헬스 체크는 앱 포트가 아니라 관리 포트(`/actuator/health`)를 대상으로 설정해야 한다. 다만 관리 포트는 별도 서버라서 관리 포트가 `UP`이어도 앱 포트(8080)가 요청을 받을 수 있다는 보장은 없다(예: 앱 포트의 요청 처리 스레드가 모두 사용 중인 경우). 로드밸런서는 앱 포트로 트래픽을 보내므로, 관리 포트의 헬스 체크와 함께 앱 포트의 공개 경로(예: `GET /api/categories`)로도 요청 수신 여부를 확인한다.

### 결과
`ActuatorExposureTest`(실제 HTTP로 두 포트를 호출)로 확인했다.

| 포트 | `/actuator/health` | `/actuator/prometheus` |
| --- | --- | --- |
| 앱 포트 | 401 | 401 |
| 관리 포트 | 200 | 200 |

- 기존 공개 경로(`/api/categories`)는 앱 포트에서 그대로 200이다.
- 전체 테스트 통과.

## 2. 요청 로깅과 추적 ID

### 상황
- 장애나 느린 요청이 생겼을 때 "어떤 요청이, 얼마나 걸리고, 어떤 결과였는지"를 로그로 찾아야 한다.
- 요청이 동시에 들어오면 로그가 섞이므로 한 요청의 로그를 묶어 볼 방법이 필요하다.
- 인증 실패(401) 요청도 기록되어야 한다.

### 후보
| 후보 | 장점 | 단점 |
| --- | --- | --- |
| Spring 기본 `CommonsRequestLoggingFilter` | 코드가 적음 | 쿼리 문자열·본문이 로그에 남기 쉽고, 추적 ID·소요 시간·레벨 정책을 직접 얹기 어려움 |
| 컨트롤러·서비스에 로그 직접 작성 | 세밀한 제어 | 모든 API에 반복, 누락 가능, 보안 필터에서 거절된 요청은 기록 불가 |
| **서블릿 필터 직접 구현(선택)** | 모든 요청을 한 곳에서 기록, 보안 필터보다 앞에 두어 401도 기록, 남기는 항목을 코드로 통제 | 필터 코드를 직접 유지보수 |

### 선택과 이유
- `RequestLoggingFilter`(`global/filter`)를 최우선 순위로 등록한다. 요청마다 UUID 추적 ID를 만들어 MDC(`traceId`)에 넣고 응답 헤더 `X-Request-Id`로 돌려준다.
- 요청 처리가 끝나면 한 줄을 남긴다: `요청 처리 {method} {path} status={status} {ms}ms`. 구조화 필드는 `method`, `path`, `status`, `durationMs`다.
- 운영(`prod` 프로필)은 JSON(`logstash` 형식)으로 출력해 수집·검색하기 쉽게 한다. 로컬은 읽기 쉬운 기본 형식이다.

### 로그 정책
| 상태 코드 | 레벨 | 의미 |
| --- | --- | --- |
| 2xx·3xx | INFO | 정상 |
| 4xx | WARN | 요청자 쪽 원인일 수 있음 |
| 5xx(처리 중 예외 포함) | ERROR | 서버 문제 |

**남기지 않는 것**
- 쿼리 문자열, 요청·응답 헤더, 요청·응답 본문, 비밀번호·토큰·개인정보. 검색어나 토큰이 들어갈 수 있다.
- 로그인한 사용자 ID. 보안 컨텍스트가 필터 체인이 끝나면 비워지고, 이번 범위에서는 제외했다.

**기록하지 않는 경로**
- `/api/webhooks/**`: 서명 검증에 원문 본문이 필요해 필터가 요청에 관여하지 않도록 제외한다.

### 트레이드오프
- 요청마다 로그가 한 줄씩 늘어 로그량이 요청량에 비례한다. 부하 테스트에서는 로그 출력이 응답 시간에 영향을 줄 수 있으므로 측정 시 같은 조건을 유지한다.
- 5xx 중 예외가 컨트롤러 단에서 처리되어 응답 코드로 바뀌는 경우는 응답 상태 기준으로 기록한다. 필터까지 예외가 올라온 경우에만 500으로 기록한다.
- 관리 포트(8081) 요청은 별도 서버라 이 필터를 거치지 않는다(헬스 체크·지표 수집이 로그를 채우지 않는 장점).

### 결과
`RequestLoggingFilterTest`(단위 6개)와 `RequestLoggingFilterIntegrationTest`(통합 2개)로 확인했다.
- 응답마다 서로 다른 UUID 형식의 `X-Request-Id`가 담긴다(보안 필터가 거절한 401 응답 포함).
- 요청 처리 중 MDC에 추적 ID가 있고 끝나면 지워진다.
- 200은 INFO, 404는 WARN, 500은 ERROR로 기록된다.
- 쿼리 문자열은 로그에 남지 않는다.
- 웹훅 경로는 로그와 헤더가 없다.
- 운영 JSON 로그 예:
```json
{"message":"요청 처리 GET /api/products status=401 132ms","level":"WARN","traceId":"a5864564-...","method":"GET","path":"/api/products","status":401,"durationMs":132}
```

## 3. Prometheus·Grafana 구성과 대시보드

### 상황
- 2-A에서 지표를 열었지만 수집·저장·시각화 도구가 없어 수치를 볼 수 없다.
- 부하 테스트(필수 8)와 병목 개선(필수 9)은 Before → After를 그래프와 수치로 보여 주어야 한다.
- 팀원 누구나, 서버에서도 같은 화면을 재현할 수 있어야 한다.

### 후보
| 후보 | 장점 | 단점 |
| --- | --- | --- |
| Actuator 응답(JSON)만 직접 확인 | 도구 설치 없음 | 시간에 따른 변화·p95·비교가 불가능 |
| 클라우드 모니터링 서비스(CloudWatch 등) | 서버 구성과 연동 | 로컬 측정이 어렵고, 서버가 확정되기 전에는 쓸 수 없음 |
| **Prometheus + Grafana(선택)** | Micrometer·Spring Boot와 표준 조합, 로컬에서 같은 구성으로 재현, 대시보드를 파일로 관리 | 컨테이너 2개가 늘고 쿼리 언어(PromQL)를 알아야 함 |

### 선택과 이유
- **수집 방식**: Prometheus가 관리 포트(8081)의 `/actuator/prometheus`를 5초마다 가져간다(pull). 앱은 창구만 열어 두면 되고 별도 전송 코드가 필요 없다.
- **파일 구성**: `docker-compose.monitoring.yml`을 기존 `docker-compose.yml`과 분리했다. 모니터링이 필요 없는 팀원의 로컬 환경과 CI에 영향을 주지 않는다.
- **대시보드는 파일로 관리**(Grafana 프로비저닝): 데이터소스와 대시보드를 `monitoring/grafana/` 설정 파일로 등록한다. 화면에서 손으로 만든 대시보드는 컨테이너를 지우면 사라지지만, 파일은 레포에 남아 누구나 같은 화면을 얻고 변경 이력도 남는다.
- **응답 시간 분위수**: p95·p99를 계산하려면 응답 시간 구간별 건수(히스토그램)가 필요해서 `application.yml`에 `management.metrics.distribution.percentiles-histogram.http.server.requests: true`를 추가했다. 평균은 아주 느린 요청을 가리므로 p95·p99를 함께 본다.
- **접근 범위**: Prometheus(9090)와 Grafana(3000) 포트는 `127.0.0.1`에 묶어 내 컴퓨터에서만 접속한다.

### 실행 방법
1. 앱을 실행한다(앱 포트 8080, 관리 포트 8081).
2. `docker compose -f docker-compose.monitoring.yml up -d`
3. `http://localhost:9090/targets`에서 `recipick`이 UP인지 확인한다.
4. `http://localhost:3000`(계정 `admin`, 비밀번호는 환경 변수 `GRAFANA_ADMIN_PASSWORD`, 없으면 `admin`)에서 Recipick 폴더의 "Recipick 개요" 대시보드를 연다.
5. 종료는 `docker compose -f docker-compose.monitoring.yml down`.

### 대시보드 패널
| 패널 | 지표 | 용도 |
| --- | --- | --- |
| 초당 요청 수(경로별) | `http_server_requests_seconds_count`의 `rate` | 처리량, 부하 테스트 중 요청 수 확인 |
| 응답 시간 p95·p99 | `http_server_requests_seconds_bucket`의 `histogram_quantile` | 느린 요청, 개선 전후 비교의 핵심 지표 |
| 5xx 에러율 | 5xx 요청 수 / 전체 요청 수 | 부하 중 장애 발생 여부 |
| 평균 응답 시간(경로별) | `_sum` / `_count` | 느린 API 식별 |
| JVM 힙 사용량 | `jvm_memory_used_bytes`, `jvm_memory_max_bytes` | 메모리 부족·캐시 도입 영향 |
| DB 커넥션 풀 | `hikaricp_connections_active`, `pending`, `max` | 커넥션 대기·고갈 여부 |
| CPU 사용률 | `process_cpu_usage`, `system_cpu_usage` | 서버 자원 한계 |

보민님의 AI 지표는 같은 Prometheus에 수집되므로 이 대시보드에 패널로 추가한다.

### 트레이드오프
- 이 구성은 **로컬 측정용**이다. 수집 주소가 `host.docker.internal:8081`(호스트에서 실행 중인 앱)이라 서버(AWS) 배포 구성에서는 그대로 쓸 수 없다. 서버에서의 수집 구성은 운영 Compose(배포 기반 구성)와 맞춰 별도로 정한다.
- 관리 포트는 인증이 없으므로(1절) 서버에서 Prometheus가 있는 내부 대역에만 열어야 한다.
- Prometheus는 최근 지표를 로컬 볼륨에 저장한다. `down -v`로 지우면 이전 측정값이 사라지므로, Before/After 수치는 스크린샷과 `docs/performance/`의 문서에 별도로 남긴다.
- Grafana 기본 계정은 로컬 전용이다. 서버에 올릴 때는 반드시 비밀번호를 변경한다.
- 히스토그램 버킷이 켜지면 지표 수가 늘어 수집·저장량이 조금 증가한다.

### 결과
로컬에서 직접 띄워 확인했다.

| 확인 항목 | 결과 |
| --- | --- |
| Prometheus Targets(`recipick`) | UP |
| Grafana 데이터소스·대시보드 자동 등록 | 등록됨(수동 설정 없음) |
| 패널 7개 쿼리 | 모두 값 반환 |
| 요청을 보낼 때 그래프 변화 | 요청 수·응답 시간·커넥션 풀 그래프 갱신 |

- 5xx가 한 건도 없으면 에러율 쿼리가 "데이터 없음"이 되어 `or vector(0)`을 붙여 0%로 표시한다.

## 4. AI 지표

### 상황
- AI 요청은 외부 서비스(Claude) 호출이라 응답 지연·장애·요청 한도 초과가 생길 수 있고, 요청마다 토큰 비용이 든다.
- 요청별 기록은 `ai_request_log`에 남지만, 실패율이 오르는지·응답이 느려지는지·토큰이 얼마나 쓰이는지를 보려면 매번 DB를 집계해야 한다.
- HTTP 지표(`http_server_requests`)로는 AI 장애를 알 수 없다. AI가 실패해도 `FALLBACK` 상태의 200으로 응답하기 때문이다.

### 후보
| 후보 | 장점 | 단점 |
| --- | --- | --- |
| `ai_request_log`를 주기적으로 집계 | 이미 있는 데이터 | 관리자 조회 감사 기록이 남고, 실시간 추이·p95를 보기 어려움 |
| AI 호출마다 infra(`ClaudeAiClient`)에서 지표 기록 | 호출 단위로 정확함 | 조건 추천처럼 두 번 호출하는 기능의 최종 결과(`FALLBACK` 여부)를 알 수 없음, 가짜 클라이언트에서는 지표가 없음 |
| **이력 저장 시점에 지표 기록(선택)** | 이력과 같은 값(결과 상태·실패 사유·합산 토큰·응답 시간)이라 두 기록이 어긋나지 않음 | 조건 추천의 응답 시간은 두 호출을 합친 값 |

### 선택과 이유
- `AiRequestLogService.save`가 이력을 저장하고, 트랜잭션이 커밋된 뒤(`afterCommit`)에 `AiRequestMetrics.record`를 호출한다. 자연어 장보기는 미리보기 저장과 같은 트랜잭션에서 이력을 남겨 저장 뒤에 롤백될 수 있으므로, 롤백되면 지표도 남기지 않는다.
- 결과 상태·실패 사유 조합과 응답 시간은 앱이 뜰 때 0으로 미리 등록한다. 새 조합이 첫 요청 때 1로 처음 나타나면 Prometheus의 `increase`·`rate`가 그 요청을 0으로 계산해, 드물게 오는 첫 실패가 대시보드에서 빠지기 때문이다.
- 지표 3개를 기존 Prometheus(관리 포트 `/actuator/prometheus`)로 내보낸다. 수집 설정은 바꾸지 않는다.

| 지표(Prometheus 이름) | 태그 | 용도 |
| --- | --- | --- |
| `recipick_ai_requests_total` | `type`(AUTO_CART·RECOMMENDATION), `status`(결과 상태), `failure`(실패 사유, 성공이면 `NONE`) | 요청 수, 실패율, 실패 사유 |
| `recipick_ai_latency_seconds` | `type` | 응답 시간 p95(히스토그램) |
| `recipick_ai_tokens_total` | `type`, `model`, `kind`(input·output·cache_read) | 모델별 토큰 사용량(비용 추정) |

- 태그에는 enum 값과 모델명만 쓴다. 회원 ID·원문을 태그로 쓰면 값마다 지표가 새로 생겨 Prometheus 저장량이 계속 늘고, 개인정보가 지표로 나간다.
- 응답을 받지 못한 호출은 토큰을 0으로 세지 않고 건너뛴다. 이력에서 토큰을 비워 두는 기준과 같다.
- 회원별 한도 초과(`AI-001`)로 거절한 요청은 AI를 호출하지 않고 이력도 남기지 않으므로 이 지표에 잡히지 않는다.

### 대시보드 패널("Recipick 개요"에 추가)
AI 요청은 HTTP 요청보다 훨씬 드물어 1분 구간으로는 그래프가 거의 비므로 5분 구간을 쓴다.

| 패널 | 쿼리 요지 |
| --- | --- |
| AI 요청 수(5분, 결과별) | `sum by (type, status) (increase(recipick_ai_requests_total[5m]))` |
| AI 실패율(FALLBACK 비율) | `failure!="NONE"` 요청 비율. 실패가 없거나 5분 동안 요청이 없으면(0/0) 0 |
| AI 실패 사유(5분) | `sum by (failure) (increase(...{failure!="NONE"}[5m]))` |
| AI 응답 시간 p95 | `histogram_quantile(0.95, sum by (le, type) (rate(recipick_ai_latency_seconds_bucket[5m])))` |
| AI 토큰 사용량(5분) | `sum by (model, kind) (increase(recipick_ai_tokens_total[5m]))` |

### 트레이드오프
- 조건 추천의 응답 시간은 두 호출의 합이라, 호출 한 번의 지연과 직접 비교하지 않는다. 기능별(`type`)로 나눠 본다.
- 응답 시간 히스토그램을 켜서 지표 수가 조금 늘어난다. `type`이 2개라 늘어나는 양은 작다.
- 토큰 지표는 비용 추정용이다. 실제 청구 금액은 Anthropic 콘솔 기준이다.
- 토큰 지표의 모델명은 실행 중에 정해지는 값이라 미리 등록하지 않는다. 모델마다 첫 요청의 토큰은 증가량 그래프에서 빠질 수 있지만, 누적 합계에는 들어간다.

### 결과
- `AiRequestMetricsTest`(단위 4개): 결과 상태·실패 사유별 건수, 응답 시간 합계, 토큰 종류별 합계, 받지 못한 토큰 제외, Prometheus 이름(`_total`, `_bucket`), 요청 전 0 등록을 확인했다.
- `AiRequestLogServiceMetricsTest`(통합 2개): 커밋되면 지표가 한 번 기록되고, 바깥 트랜잭션이 롤백되면 이력과 지표가 모두 남지 않는 것을 확인했다.
