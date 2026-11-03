# 회원 잠금 동시성 테스트가 경합 없이도 통과할 수 있었음

| 항목 | 내용 |
| --- | --- |
| 제목·담당자·발생일 | 고정 대기 시간에 기대는 동시성 테스트 · 준희 · 2026-10-26 |
| 영향 범위 | 테스트 신뢰도. `OrderCreateMemberLockTest`가 "주문 생성은 회원 잠금을 기다린다"는 것을 실제로 검증하지 못할 수 있었다. 운영 코드·데이터 영향 없음 |
| 관련 이슈·PR·커밋 | #176(PR #177), 테스트 추가 `c3f107a`, 수정 `4021795` |
| 처리 상태 | 해결 |

## 1. 증상

배경: #176에서 주문 생성의 첫 DB 작업을 회원 행 잠금(`memberService.getMemberForUpdate`)으로 바꿨다. 탈퇴와 주문 생성이 겹쳐도 한쪽만 성공하게 하고, 같은 회원의 요청을 하나씩 처리하기 위해서다. 이를 검증하려고 "다른 트랜잭션이 회원 행을 잠근 동안 주문 생성이 들어오면 기다렸다가 그 결과를 본다"는 테스트 2건(회원 삭제, 같은 키 주문)을 추가했다. 탈퇴 상태 저장 테스트는 이후 #191에서 같은 헬퍼로 추가됐다.

- 기대한 동작과 실제 동작
  - 기대: 테스트가 통과하면 주문 생성이 회원 잠금에서 실제로 기다렸다는 뜻이어야 한다.
  - 실제: 처음 작성한 테스트는 잠금을 잡은 트랜잭션이 500ms만 기다린 뒤 작업하고 커밋했다. 테스트 스레드의 주문 생성이 500ms 안에 잠금 조회까지 가지 못하면, 앞 트랜잭션이 이미 커밋한 뒤라 기다림 없이 실행된다. 이 경우에도 결과는 같아서 테스트가 통과한다.
- 발생 환경·재현 조건
  - Testcontainers MySQL, `TransactionTemplate`과 `ExecutorService`로 두 트랜잭션을 겹치게 한다.
  - CI처럼 느린 환경, 첫 실행의 커넥션 풀 준비, GC 등으로 테스트 스레드가 500ms 넘게 늦으면 경합 없이 통과한다.
- 처음 작성한 코드(`c3f107a`)

```java
memberRepository.findByIdForUpdate(member.getId()).orElseThrow();
locked.countDown();   // 잠금을 잡았다는 신호
hold();               // Thread.sleep(500) 뒤 작업·커밋
```

- 핵심 관찰 결과
  - 실패로 드러난 적은 없다. PR #177 리뷰에서 CodeRabbit이 "`locked` 신호는 잠금을 잡았다는 사실만 알릴 뿐, 주문 생성이 그 잠금을 기다리는지는 보장하지 않는다"고 지적했다.
  - 테스트 결과만으로는 경합이 있었는지 없었는지 구분할 수 없다는 점이 문제였다.

## 2. 원인

| 가설 | 확인 방법 | 관찰 결과·판정 |
| --- | --- | --- |
| 500ms면 테스트 스레드가 항상 먼저 잠금에 걸린다 | 두 스레드의 실행 순서를 보장하는 장치가 있는지 코드 확인 | `locked` 신호 뒤 테스트 스레드가 주문 생성을 시작하는 시점은 스케줄러에 달려 있다. 보장이 없다. 기각 |
| 경합이 없으면 결과가 달라져 실패한다 | 경합이 없을 때의 실행 흐름 추적 | 앞 트랜잭션이 회원 삭제·탈퇴·주문 생성을 먼저 커밋하면, 늦게 온 주문 생성은 기다리지 않고도 같은 결과(`MEMBER-001`, 기존 주문 반환)를 낸다. 실패하지 않는다. 문제 확인 |

확인된 원인

- 테스트는 "잠금을 잡았다"는 사실만 동기화하고, "상대가 그 잠금을 기다린다"는 사실은 고정 시간으로 추측했다.
- 경합이 있든 없든 최종 결과가 같아서, 결과 검증만으로는 테스트가 의도한 상황이 만들어졌는지 알 수 없다.

## 3. 해결

- 바꾼 내용과 이유
  - 잠금을 잡은 트랜잭션이 고정 시간을 기다리는 대신, 다른 커넥션이 회원 행 `FOR UPDATE`를 실행하며 기다리는 상태인지 직접 확인한 뒤 진행한다.
  - 같은 계정의 연결은 `information_schema.PROCESSLIST`에 보인다. 회원 행은 이 트랜잭션이 잠그고 있으므로, 다른 연결에 회원 `FOR UPDATE` 문이 떠 있으면 그 요청이 잠금 대기 중이라는 뜻이다.
  - 5초 안에 대기 요청이 보이지 않으면 `IllegalStateException`으로 테스트를 실패시킨다. 경합이 만들어지지 않았는데 통과하는 경우를 없앴다.

```java
memberRepository.findByIdForUpdate(member.getId()).orElseThrow();
locked.countDown();
awaitMemberLockWaiter();  // 다른 연결의 회원 FOR UPDATE가 보일 때까지 20ms 간격 확인, 최대 5초
action.run();

private long countMemberLockWaiters() {
    Number count = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM information_schema.PROCESSLIST "
                + "WHERE ID <> CONNECTION_ID() AND INFO LIKE '%from member %for update%'")
        .getSingleResult();
    return count.longValue();
}
```

- 다른 대안을 선택하지 않은 이유

| 대안 | 선택하지 않은 이유 |
| --- | --- |
| 대기 시간을 늘림(예: 2초) | 경합이 생길 확률만 높일 뿐 보장하지 않는다. 테스트도 느려진다. |
| 테스트 스레드가 주문 생성 직전에 신호를 보냄 | 신호와 실제 잠금 조회 사이에도 트랜잭션 시작·커넥션 획득이 있어 같은 문제가 남는다. |
| `performance_schema.data_lock_waits` 조회 | InnoDB의 잠금 대기 목록을 직접 보므로 더 정확하다. 앱 계정은 `performance_schema`를 읽을 권한이 없어 관리자 계정 연결을 따로 열어야 하지만, `MembershipRenewalConcurrencyTest.awaitLockWait`가 같은 Testcontainers 환경에서 이 방식을 이미 쓰고 있다. 이 테스트는 앱 연결로 바로 볼 수 있는 `PROCESSLIST`로 먼저 고쳤고, 바꾸는 것은 남은 작업으로 둔다. |

- 변경 코드: `src/test/java/com/recipick/domain/order/facade/OrderCreateMemberLockTest.java`의 `lockMemberThen`, `awaitMemberLockWaiter`, `countMemberLockWaiters`
- 기존 데이터·운영에 필요한 조치: 없음(테스트 코드만 변경)

## 4. 검증 결과

| 확인 항목 | 실행 방법 | 수정 전 | 수정 후 | 근거 |
| --- | --- | --- | --- | --- |
| 경합이 없을 때의 동작 | 코드 흐름 분석 | 경합 없이도 통과 | 대기 요청이 5초 안에 없으면 실패 | `4021795` |
| 회원 잠금 테스트 | `OrderCreateMemberLockTest`: 잠금 중에 회원 삭제·같은 키 주문(수정 당시 2건), 탈퇴 상태 저장(#191 추가) | 2건 통과(경합 보장 없음) | 현재 3건 통과(대기 확인 후 진행) | 2026-11-03 `dev`(`69e7ba3`) 실행 |

실행 명령: `./gradlew test --tests '*OrderCreateMemberLockTest'` (Docker 필요, Testcontainers MySQL)

## 5. 재발 방지와 남은 작업

- 추가한 테스트·문서
  - 대기 확인 헬퍼와 "고정 시간만 기다리면 경합 없이 통과할 수 있다"는 이유 주석
- 남은 제한과 담당 이슈
  - `PROCESSLIST`의 `INFO`를 문자열로 찾으므로 두 가지 한계가 있다.
    - Hibernate가 만드는 SQL 형태(`from member ... for update`)가 바뀌면 대기를 못 찾아 테스트가 실패한다. 실패 메시지를 보고 조건을 고치면 된다.
    - 다른 연결이 같은 형태의 쿼리를 실행 중이면(이전 테스트가 남긴 스레드, 다른 회원의 잠금 등) 이 회원 행을 기다리는 요청이 아니어도 대기로 센다. 이 경우 경합 없이 통과할 수 있다. 지금은 테스트를 한 프로세스에서 순서대로 실행해 가능성이 낮지만 완전히 막지는 못한다.
    - 두 한계 모두 `performance_schema.data_lock_waits`에서 잠금 대상 테이블·행을 조건으로 걸어 확인하면 줄일 수 있다.
  - 결제 도메인의 동시성 테스트 일부에도 `Thread.sleep`이 있다. 같은 문제가 있는지는 확인하지 않았다(결제 담당 확인 필요).
- 다른 팀원에게 공유할 적용 방법
  - 동시성 테스트는 "결과가 맞다"와 "의도한 경합이 실제로 일어났다"를 따로 확인한다. 경합이 없어도 결과가 같다면, 경합을 직접 확인하는 장치가 필요하다.
  - `Thread.sleep`으로 순서를 맞추는 코드가 보이면, 그 시간이 지나도 상대가 준비되지 않았을 때 테스트가 실패하는지 통과하는지 따져 본다.
