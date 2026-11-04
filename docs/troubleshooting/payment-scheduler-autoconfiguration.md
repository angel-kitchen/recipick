# 포인트 만료 스케줄러 등록 후 Boot 기본 실행기가 사라진 문제

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 스케줄러 빈 직접 등록에 따른 `applicationTaskExecutor` 누락 · **최정윤** · 2026-10-30(수정일) |
| 영향 범위 | 운영 프로필(`!test`)의 Spring 빈 구성. 기본 비동기 실행기(`applicationTaskExecutor`)를 쓰는 기능 |
| 관련 이슈·PR·커밋 | [#253](https://github.com/angel-kitchen/recipick/issues/253) · [PR #278](https://github.com/angel-kitchen/recipick/pull/278) · [`f71ea21`](https://github.com/angel-kitchen/recipick/commit/f71ea21) |
| 처리 상태 | 해결 |

## 1. 증상

- **기대한 동작과 실제 동작:** 포인트 만료 배치를 결제 지연 큐 폴링과 다른 스레드에서 돌리려고 `PointSchedulingConfig`에 스케줄러 빈 두 개(`taskScheduler`, `pointExpirationTaskScheduler`)를 직접 등록했다. 기대한 것은 스케줄러만 추가되고 나머지 Boot 자동 구성은 그대로인 상태였다. 실제로는 Spring Boot가 기본으로 만들던 비동기 실행기 `applicationTaskExecutor`가 운영 프로필에서 만들어지지 않았다.
- **발생 환경·버전·재현 조건:** Spring Boot 3.5, `@Profile("!test")`인 `PointSchedulingConfig`가 켜진 컨텍스트. test 프로필에서는 이 설정이 꺼져 Boot 기본 구성이 그대로라, 일반 통합 테스트로는 드러나지 않는다.
- **재현 명령·요청·순서:** 수정 직전 코드(`f71ea21^`)에 수정 커밋의 테스트를 적용해 실행한다. 이 테스트는 `ApplicationContextRunner`로 `TaskExecutionAutoConfiguration`, `TaskSchedulingAutoConfiguration` 위에 `PointSchedulingConfig`를 올린 작은 컨텍스트를 만들고 빈 구성을 확인한다.

  ```bash
  ./gradlew test --tests 'com.recipick.domain.payment.scheduler.PointExpirationSchedulerTest'
  ```

- **핵심 오류 로그·관찰 결과:** 7건 중 1건 실패. 2026-11-04 작성자 로컬 환경에서 수정 직전 커밋(`f71ea21^`)에 수정 커밋의 테스트 파일만 가져와 실행한 결과다. 수정 커밋(`f71ea21`)의 설정을 적용하면 같은 테스트 7건이 모두 통과한다.

  ```text
  PointExpirationSchedulerTest > 포인트 스케줄러를 직접 등록해도 Boot 기본 실행기 applicationTaskExecutor가 함께 있다 FAILED
  java.lang.AssertionError:
  Expecting:
   <Started application [AnnotationConfigApplicationContext@… beanDefinitionCount = 27]>
  to have bean named:
   <"applicationTaskExecutor">
  but found no such bean
  ```

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 스케줄러를 직접 등록해서 Boot 기본 스케줄러(`taskScheduler`)가 빠졌다 | 수정 전 설정 확인 | 기본 스케줄러는 같은 이름으로 함께 등록해 두었다. 이 문제의 원인이 아님 |
| 기본 실행기를 만드는 자동 구성이 다른 Executor 빈 때문에 물러났다 | 위 `ApplicationContextRunner` 테스트로 빈 존재 확인 | `ThreadPoolTaskScheduler`는 스케줄러이면서 `Executor`이기도 하다. Boot는 컨텍스트에 `Executor` 빈이 없을 때만 `applicationTaskExecutor`를 자동 구성하므로, 스케줄러 빈을 직접 등록하자 기본 실행기를 만들지 않았다. 채택 |

확인된 원인: 스케줄러를 직접 등록하면 Boot의 기본 스케줄러뿐 아니라 기본 실행기 자동 구성까지 함께 물러난다. 기본 스케줄러는 같은 이름으로 다시 등록해 두었지만, 실행기 쪽은 놓쳤다.

## 3. 해결

- **바꾼 내용과 이유:** `PointSchedulingConfig`에 Boot와 같은 이름(`TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME`)·같은 빌더(`ThreadPoolTaskExecutorBuilder`)로 `applicationTaskExecutor`를 함께 등록했다. `spring.task.execution.*` 설정값을 그대로 이어받는다. 쓰는 곳이 생기기 전까지 스레드 풀을 만들지 않도록 `@Lazy`로 둔다.

  ```java
  @Lazy
  @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
  public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
      return builder.build();
  }
  ```

- **다른 대안을 선택하지 않은 이유:**
  - **스케줄러를 하나만 두고 풀 크기를 늘리기:** 결제 지연 큐 폴링(1초 주기)과 하루 한 번인 포인트 만료 배치가 같은 풀을 나눠 쓴다. 전용 스케줄러로 분리한 원래 목적이 사라진다.
  - **`SchedulingConfigurer`로 등록하기:** 전용 스케줄러를 `@Scheduled(scheduler = …)`로 지정하는 현재 방식보다 설정이 흩어진다.
- **변경 코드·설정·문서:** [`PointSchedulingConfig`](../../src/main/java/com/recipick/domain/payment/scheduler/PointSchedulingConfig.java), [`PointExpirationSchedulerTest`](../../src/test/java/com/recipick/domain/payment/scheduler/PointExpirationSchedulerTest.java)
- **기존 데이터·운영에 필요한 조치:** 없음. 빈 구성만 바뀐다.

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 원래 문제 재현 | `PointExpirationSchedulerTest.schedulingConfigKeepsApplicationTaskExecutor` | 실패: `applicationTaskExecutor` 빈 없음 | 통과: `taskScheduler`, `pointExpirationTaskScheduler`, `applicationTaskExecutor`가 모두 있음 | [테스트](../../src/test/java/com/recipick/domain/payment/scheduler/PointExpirationSchedulerTest.java) |
| 영향받는 기능 회귀 검증 | `PointExpirationSchedulerTest` 전체 | 7건 중 1건 실패 | 7건 모두 통과 | 같은 테스트 |

## 5. 재발 방지와 남은 작업

- **추가한 테스트·알림·문서·규칙:** 운영 프로필 설정을 test 프로필이 아닌 작은 컨텍스트(`ApplicationContextRunner`)로 띄워 빈 구성을 확인하는 테스트를 추가했다. 설정 클래스 주석에 "스케줄러도 Executor라서 기본 실행기도 함께 만든다"는 이유를 남겼다.
- **남은 제한과 담당 이슈:** 2026-11-04 기준 메인 코드에는 `applicationTaskExecutor`를 직접 주입하거나 `@Async`로 쓰는 곳이 없어, 이 누락이 장애로 이어지지는 않았다. 운영과 테스트의 빈 구성이 달라지는 잠재 문제를 먼저 막은 것이다.
- **다른 팀원에게 공유할 적용 방법:** Boot가 자동으로 만드는 빈을 직접 등록할 때는 그 타입 때문에 물러나는 다른 자동 구성이 없는지 확인한다. `TaskScheduler`·`Executor`처럼 여러 인터페이스를 구현한 빈은 특히 그렇다. `@Profile("!test")` 설정은 일반 통합 테스트에서 켜지지 않으므로 `ApplicationContextRunner`로 따로 검증한다.
