# Prometheus 수집이 `down`: `/actuator/prometheus` 응답이 23초

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | Prometheus 수집 시간 초과 · 태헌 · 2026-11-04 |
| 영향 범위 | 로컬 부하 측정 환경. 지표(풀 대기, 응답 시간)가 수집되지 않아 측정 결과를 확인할 수 없었다. 운영 코드·API 동작에는 영향 없음 |
| 관련 이슈·PR·커밋 | #453, PR #456 |
| 처리 상태 | 임시 대응(로컬에서 CPU 지표 끔). 이 PC에서 느린 근본 원인은 확인하지 못했다 |

## 1. 증상

- 기대한 동작과 실제 동작: Prometheus 대상(`host.docker.internal:8081`)이 `up`이어야 하는데 `down`이었다.
- 발생 환경·재현 조건: Windows 11 단일 PC, `./gradlew bootRun`(`local` 프로필, JDK 17 툴체인), `docker-compose.monitoring.yml`의 Prometheus(수집 간격 5초, 제한 시간 5초).
- 재현 명령: `curl -m 60 -w "%{time_total}" http://localhost:8081/actuator/prometheus` 를 연달아 호출한다.
- 관찰 결과
  - `/actuator/prometheus`: 25.4초, 24.6초, 22.0초, 23.4초 (모두 200)
  - 같은 포트의 `/actuator/health`: 0.02초
  - Prometheus 대상 오류: `context deadline exceeded` (`lastScrapeDuration` 5.0초)

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 첫 호출만 느린 지연 초기화 | 같은 호출을 연속 3회 | 매번 22~25초라서 아님 |
| 우리가 만든 지표가 DB를 조회한다 | `MeterBinder`, `Gauge`, `MeterRegistry` 사용처 검색 | 직접 만든 지표는 `AiRequestMetrics` 하나이고 DB를 쓰지 않아 아님 |
| 스크랩 중인 스레드가 어디서 시간을 쓰는지 | 호출 중 `jcmd <pid> Thread.print` | `ProcessorMetrics`가 `OperatingSystemImpl.getProcessCpuLoad0`(네이티브)에서 실행 중이었다 |

확인된 원인: Micrometer 기본 지표 `process.cpu.usage`가 읽는 JDK의 `getProcessCpuLoad` 네이티브 호출이 이 PC에서 매번 20초 넘게 걸렸다. 지표를 모아 내보낼 때마다 호출되므로 전체 응답이 23초가 됐고, 5초 제한의 Prometheus가 실패 처리했다. 이 호출이 이 PC에서 왜 느린지는 확인하지 못했다.

## 3. 해결

- 바꾼 내용과 이유: 앱을 띄울 때 환경변수로 CPU 지표를 껐다. 코드와 `application*.yml`은 바꾸지 않았다.

```powershell
$env:MANAGEMENT_METRICS_ENABLE_PROCESS_CPU = "false"
$env:MANAGEMENT_METRICS_ENABLE_SYSTEM_CPU  = "false"
```

- `system.cpu`는 원인 스레드 덤프에 나오지 않았지만 같은 계열이라 함께 껐다. 하나씩 끄고 비교하지는 않았다.
- 다른 대안을 선택하지 않은 이유: Prometheus 제한 시간을 늘리는 방법은 수집 간격(5초)보다 길 수 없고, 23초짜리 호출이 겹쳐 쌓일 수 있어서 택하지 않았다.
- 부작용: Grafana 대시보드(`monitoring/grafana/dashboards/recipick-overview.json`)의 CPU 패널이 비어 보인다. 이번 측정에서 보는 지표(응답 시간, 풀 대기, 429)에는 영향이 없다.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 지표 응답 시간 | `curl -m 60 …/actuator/prometheus` 3회 | 22~25초 | 0.02~0.05초 | 같은 PC, 같은 앱 |
| Prometheus 대상 상태 | `/api/v1/targets` | `down` | `up` | 수집 간격 5초 |
| CPU 지표 제거 | `/actuator/prometheus`에서 `process_cpu_usage`, `system_cpu_usage` 검색 | 있음 | 0줄 | 의도한 부작용 |

## 5. 재발 방지와 남은 작업

- 추가한 문서: 이 문서. 코드·설정 변경은 없다.
- 남은 제한: 팀원의 PC나 서버(AWS)에서도 같은 지연이 있는지는 확인하지 못했다. 다른 환경에서 `/actuator/prometheus`가 느리면 같은 방법을 쓸 수 있다.
- 공유할 적용 방법: 부하 측정 때 Prometheus 대상이 `down`이면 먼저 `curl`로 `/actuator/prometheus` 응답 시간을 잰다. 느리면 위 환경변수 두 개를 주고 앱을 다시 띄운다.
