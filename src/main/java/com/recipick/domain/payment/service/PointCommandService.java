package com.recipick.domain.payment.service;

import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.PointHistory;
import com.recipick.domain.payment.entity.PointUsage;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointUsageStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointHistoryRepository;
import com.recipick.domain.payment.repository.PointUsageRepository;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointCommandService {

    private static final Comparator<PointEarning> FIRST_EXPIRED_FIRST =
        Comparator.comparing(PointEarning::getExpiresOn).thenComparing(PointEarning::getId);

    private final MemberPointRepository memberPointRepository;
    private final PointEarningRepository pointEarningRepository;
    private final PointUsageRepository pointUsageRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final Clock clock;

    // 주문 생성에서 주문 INSERT 뒤·재고 차감 전에, 이 트랜잭션이 만든 주문 ID로만 부른다.
    // 같은 주문의 중복 예약은 이 메서드가 아니라 OrderFacade의 선행 계약(회원 행 잠금 → 같은 키면 기존 주문 반환 → 새 주문
    // INSERT 뒤 호출)이 막는다. 아래 같은 주문 확인은 잠그지 않는 조회라 다른 트랜잭션의 커밋을 못 볼 수 있어서,
    // 그 계약 없이 다른 Facade가 직접 부르면 같은 주문에 두 번 예약될 수 있다
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveUsage(Long memberId, Long orderId, long usedPoint) {
        requireId(memberId, "회원 ID");
        requireId(orderId, "주문 ID");
        if (usedPoint < 0) {
            throw new IllegalArgumentException("사용 포인트는 음수일 수 없습니다: " + usedPoint);
        }
        if (usedPoint == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        MemberPoint memberPoint = lockMemberPoint(memberId, now);

        // 같은 트랜잭션이 다시 부르면 앞서 만든 사용 행이 있지만 첫 예약에는 행이 없다. 없는 행을 잠그며 찾으면
        // order_id 인덱스의 빈 구간이 잠겨 다른 회원 주문의 INSERT와 교착하므로 잠그지 않고 읽는다.
        // 같은 회원의 재호출은 바로 위 MEMBER_POINT 잠금에서 이미 줄을 선다
        List<PointUsage> existing = pointUsageRepository.findAllByOrderIdOrderByIdAsc(orderId);
        if (!existing.isEmpty()) {
            requireSameReservation(orderId, existing, usedPoint);
            return;
        }

        expireStaleLots(memberPoint, memberId, today);

        List<PointEarning> usable = new ArrayList<>(pointEarningRepository.findUsableForUpdate(memberId, today));
        long usableTotal = usable.stream().mapToLong(PointEarning::getRemainingAmount).sum();
        // 잔액은 적립 건 잔량 합을 미리 더해 둔 값이다. 지연 만료까지 끝낸 뒤에도 다르면 어딘가에서 한쪽만 바뀐 것이다
        if (memberPoint.getBalance() != usableTotal) {
            throw new IllegalStateException("포인트 잔액과 사용 가능 적립 건 합계가 다릅니다. memberId=" + memberId
                + ", balance=" + memberPoint.getBalance() + ", usableTotal=" + usableTotal);
        }
        if (usableTotal < usedPoint) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_POINT_BALANCE);
        }

        usable.sort(FIRST_EXPIRED_FIRST);
        long left = usedPoint;
        for (PointEarning lot : usable) {
            if (left == 0) {
                break;
            }
            long amount = Math.min(lot.getRemainingAmount(), left);
            lot.use(amount);
            PointUsage usage = pointUsageRepository.save(PointUsage.reserve(lot, orderId, amount, now));
            pointHistoryRepository.save(PointHistory.useReserved(usage));
            left -= amount;
        }
        // 앞에서 합계를 확인했으니 남을 수 없다. 남았다면 적립 건에서 뺀 금액과 잔액에서 뺄 금액이 달라지므로 차감 전에 막는다
        if (left != 0) {
            throw new IllegalStateException("적립 건에 나눠 쓴 뒤에도 사용 금액이 남았습니다. memberId=" + memberId
                + ", orderId=" + orderId + ", remaining=" + left);
        }
        memberPoint.decrease(usedPoint);
    }

    // 주문 생성에서 재고 차감으로 상품금액이 정해진 뒤 부른다. 사용 가능한 자산(기존 적립 건·잔액)을 바꾸지 않고
    // 이 주문 전용 PENDING 행만 새로 만들므로 MEMBER_POINT 잠금을 잡지 않는다. 없는 행을 FOR UPDATE로 찾으면
    // 빈 구간 잠금이 걸려 동시 주문끼리 막힐 수 있어 일반 조회로 보고, 중복 생성은 order_id UNIQUE가 최종으로 막는다
    @Transactional(propagation = Propagation.MANDATORY)
    public void createPendingEarning(
        Long memberId, Long orderId, long productAmount, long usedPoint, BigDecimal pointRate
    ) {
        requireId(memberId, "회원 ID");
        requireId(orderId, "주문 ID");
        if (productAmount < 0 || usedPoint < 0 || usedPoint > productAmount) {
            throw new IllegalArgumentException("적립 기준 금액이 올바르지 않습니다. productAmount=" + productAmount
                + ", usedPoint=" + usedPoint);
        }
        if (pointRate == null || pointRate.signum() < 0) {
            throw new IllegalArgumentException("적립률은 0 이상이어야 합니다: " + pointRate);
        }
        // 배송비는 적립 대상이 아니고 원 미만은 버린다
        long amount = BigDecimal.valueOf(productAmount - usedPoint)
            .multiply(pointRate)
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();

        Optional<PointEarning> existing = pointEarningRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            requireSamePending(existing.get(), memberId, amount);
            return;
        }
        PointEarning earning = pointEarningRepository.save(PointEarning.pending(memberId, orderId, amount));
        if (amount > 0) {
            pointHistoryRepository.save(PointHistory.earnPending(earning));
        }
    }

    // 결제 성공. 예약 때 잔액과 적립 건 잔량이 이미 줄었으므로 상태만 확정한다. 예약 때 유효했던 포인트는
    // 결제 대기 중 만료일이 지나도 이 주문에서는 사용을 인정하므로 만료일을 다시 보지 않는다
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmUsage(Long orderId) {
        requireId(orderId, "주문 ID");
        Optional<Long> memberId = findPointMemberId(orderId);
        if (memberId.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        lockMemberPoint(memberId.get(), now);
        for (PointUsage usage : pointUsageRepository.findAllByOrderIdForUpdate(orderId)) {
            if (usage.confirm(now)) {
                pointHistoryRepository.save(PointHistory.useConfirmed(usage));
            }
        }
    }

    // 주문이 끝났을 때(결제 실패·만료·결제 전 취소, 환불 성공) 사용한 포인트를 원래 적립 건으로 되돌리고 적립 예정을 취소한다.
    // 배송완료로 적립이 확정된 주문은 거래가 끝난 것이라 사용 포인트도 적립도 자동으로 되돌리지 않는다(회수 없음).
    // 이 경로에서 적립 확정 주문을 만나면 호출 순서가 어긋난 것이라 예외로 막는다
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreUsageAndCancelEarning(Long orderId) {
        requireId(orderId, "주문 ID");
        Optional<RestoreTarget> target = lockRestoreTarget(orderId);
        if (target.isEmpty()) {
            return;
        }
        if (isEarningFinalized(target.get().orderEarning())) {
            throw new IllegalStateException("적립이 확정된 주문의 포인트는 자동으로 되돌리지 않습니다. orderId=" + orderId
                + ", status=" + target.get().orderEarning().getStatus());
        }
        restoreAndCancel(target.get());
    }

    // PortOne 콘솔 외부 취소. PG에서는 이미 돈이 돌아갔으므로 결제·주문 정합화를 롤백시키지 않도록 예외 대신 결과로 답한다.
    // 적립 상태는 잠근 뒤에 본다 — 앞선 일반 조회의 스냅숏으로 판단하면 그사이 커밋된 배송완료를 놓쳐 복원 중 예외가 나고
    // 외부 취소 전체가 롤백된다. CANCELED는 이미 복원·취소가 끝난 안전한 상태라 아무것도 바꾸지 않고 RESTORED로 답한다
    @Transactional(propagation = Propagation.MANDATORY)
    public PointRestoreOutcome restoreForExternalCancel(Long orderId) {
        requireId(orderId, "주문 ID");
        Optional<RestoreTarget> target = lockRestoreTarget(orderId);
        if (target.isEmpty()) {
            return PointRestoreOutcome.NO_POINT;
        }
        if (isEarningFinalized(target.get().orderEarning())) {
            return PointRestoreOutcome.FINALIZED_EARNING_KEPT;
        }
        restoreAndCancel(target.get());
        return PointRestoreOutcome.RESTORED;
    }

    // 배송완료. 적립 예정이 사용 가능한 포인트가 되고 이때부터 1년 뒤가 만료일이다
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmEarning(Long orderId) {
        requireId(orderId, "주문 ID");
        Optional<Long> memberId = findPointMemberId(orderId);
        if (memberId.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        MemberPoint memberPoint = lockMemberPoint(memberId.get(), now);
        PointEarning earning = pointEarningRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalStateException("주문의 적립 건을 찾을 수 없습니다. orderId=" + orderId));
        if (earning.complete(now) && earning.getAmount() > 0) {
            memberPoint.increase(earning.getAmount());
            pointHistoryRepository.save(PointHistory.earnCompleted(earning));
        }
    }

    // 만료 배치가 회원 한 명씩 부른다. 바깥 트랜잭션 없이 불려 회원마다 새 트랜잭션을 연다 — 한 회원이 실패해도
    // 다른 회원의 소멸은 그대로 커밋된다. 잠금 순서와 소멸 규칙은 포인트 사용 직전의 지연 만료와 같은 코드를 쓴다.
    // 여러 서버가 같은 회원을 동시에 처리해도 늦게 온 쪽은 MEMBER_POINT에서 기다린 뒤 대상이 없어 0을 돌려준다
    @Transactional
    public long expireMember(Long memberId, LocalDate today) {
        requireId(memberId, "회원 ID");
        Objects.requireNonNull(today, "기준 날짜는 필수입니다.");
        MemberPoint memberPoint = lockMemberPoint(memberId, LocalDateTime.now(clock));
        return expireStaleLots(memberPoint, memberId, today);
    }

    // 회원 탈퇴 정산. 회원 잠금 트랜잭션 안에서 PointSettlementChecker가 부른다.
    // 적립 예정(PENDING)·사용 예약(RESERVED)이 남아 있으면 정산이 끝나지 않은 것이라 막고, 지연 만료까지 끝낸 실제 잔액이
    // 회원이 안내받고 확인한 잔액과 같을 때만 남은 포인트를 전부 소멸시킨다. 다르면 최신 잔액을 다시 확인하도록 거절한다.
    // 포인트를 가진 적이 없는 회원(MEMBER_POINT 없음)에게는 행을 만들지 않는다 — 탈퇴하는 회원에게 새 행과 빈 구간 잠금을 남기지 않는다.
    // 잔액과 적립 건 합계가 다르면 회원이 고칠 수 없는 원장 오류라 업무 오류(409)가 아닌 내부 오류로 던져 운영이 보게 한다.
    // 거절은 모두 예외라 호출자의 탈퇴 트랜잭션과 함께 지연 만료까지 롤백된다
    @Transactional(propagation = Propagation.MANDATORY)
    public void forfeitForWithdrawal(Long memberId, long acknowledgedBalance) {
        requireId(memberId, "회원 ID");
        if (acknowledgedBalance < 0) {
            throw new IllegalArgumentException("확인한 포인트 잔액은 음수일 수 없습니다: " + acknowledgedBalance);
        }

        if (!memberPointRepository.existsByMemberId(memberId)) {
            requireNoUnsettled(memberId);
            // 확정 적립은 MEMBER_POINT 증가와 함께만 생긴다. 행이 없는데 잔량 있는 확정 적립이 있으면 원장이 깨진 것이다
            if (pointEarningRepository.existsByMemberIdAndStatusAndRemainingAmountGreaterThan(
                memberId, PointEarningStatus.COMPLETED, 0L)) {
                throw new IllegalStateException("회원 포인트 행이 없는데 잔량이 남은 확정 적립 건이 있습니다. memberId="
                    + memberId);
            }
            requireAcknowledged(0L, acknowledgedBalance);
            return;
        }

        LocalDate today = LocalDate.now(clock);
        MemberPoint memberPoint = memberPointRepository.findByMemberIdForUpdate(memberId)
            .orElseThrow(() -> new IllegalStateException("회원 포인트 행이 있다고 확인한 뒤 찾을 수 없습니다. memberId="
                + memberId));
        expireStaleLots(memberPoint, memberId, today);
        requireNoUnsettled(memberId);

        List<PointEarning> usable = pointEarningRepository.findUsableForUpdate(memberId, today);
        long usableTotal = usable.stream().mapToLong(PointEarning::getRemainingAmount).sum();
        if (memberPoint.getBalance() != usableTotal) {
            throw new IllegalStateException("포인트 잔액과 사용 가능 적립 건 합계가 다릅니다. memberId=" + memberId
                + ", balance=" + memberPoint.getBalance() + ", usableTotal=" + usableTotal);
        }
        requireAcknowledged(memberPoint.getBalance(), acknowledgedBalance);
        if (usableTotal == 0) {
            return;
        }

        // 탈퇴 소멸은 주문과 상관없어 이력에 주문을 남기지 않는다. 소멸할 잔량이 없는 적립 건은 이력도 남기지 않는다
        long forfeited = 0L;
        for (PointEarning lot : usable) {
            long amount = lot.forfeit();
            if (amount > 0) {
                pointHistoryRepository.save(PointHistory.withdrawalForfeited(lot, null, amount));
                forfeited += amount;
            }
        }
        memberPoint.decrease(forfeited);
    }

    // 적립 건이 없는 주문은 포인트가 없던 1차 주문이라 할 일이 없다(정상 경로라 로그를 남기지 않는다).
    // 적립 건 엔티티를 잠금 전에 읽어 두면 잠금 뒤에도 예전 값을 보게 되므로 회원 ID 값만 꺼낸다
    private Optional<Long> findPointMemberId(Long orderId) {
        return pointEarningRepository.findMemberIdByOrderId(orderId);
    }

    // 복원 대상을 잠근다. 적립 건이 없는 주문(1차 주문)이면 비어 있다
    private Optional<RestoreTarget> lockRestoreTarget(Long orderId) {
        Optional<Long> memberId = findPointMemberId(orderId);
        if (memberId.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MemberPoint memberPoint = lockMemberPoint(memberId.get(), now);
        PointEarning orderEarning = pointEarningRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalStateException("주문의 적립 건을 찾을 수 없습니다. orderId=" + orderId));
        return Optional.of(new RestoreTarget(memberPoint, orderEarning, now.toLocalDate()));
    }

    // 적립 건을 두 번(이 주문의 적립 건 → 사용한 적립 건들) 나눠 잠그지만, 같은 회원의 포인트 변경은 먼저 잡은 MEMBER_POINT에서
    // 줄을 서고 이 주문의 적립 건(PENDING)과 사용한 적립 건(COMPLETED·EXPIRED)은 겹치지 않아 교착이 생기지 않는다
    private void restoreAndCancel(RestoreTarget target) {
        PointEarning orderEarning = target.orderEarning();
        List<PointUsage> usages = pointUsageRepository.findAllByOrderIdForUpdate(orderEarning.getOrderId());
        Map<Long, PointEarning> sources = lockSources(usages);
        for (PointUsage usage : usages) {
            if (usage.restore()) {
                restoreOne(target.memberPoint(), usage, sources.get(usage.getPointEarning().getId()), target.today());
            }
        }
        if (orderEarning.cancel() && orderEarning.getAmount() > 0) {
            pointHistoryRepository.save(PointHistory.earnCanceled(orderEarning));
        }
    }

    // 확정·만료·탈퇴 소멸은 모두 거래가 끝난 적립이다. 되돌리거나 다시 지급하지 않는다
    private static boolean isEarningFinalized(PointEarning earning) {
        return earning.getStatus() == PointEarningStatus.COMPLETED
            || earning.getStatus() == PointEarningStatus.EXPIRED
            || earning.getStatus() == PointEarningStatus.FORFEITED;
    }

    // 회원 포인트 변경의 첫 잠금. 처음 포인트를 다루는 회원은 잠글 행이 없어 먼저 행을 보장한다.
    // 행이 있는 회원에게도 INSERT를 시도하면 PK 끝의 빈 구간 잠금이 커밋까지 남아 모든 회원의 포인트 변경이 한 줄로 서므로,
    // 잠그지 않는 조회로 없을 때만 넣고 있으면 유니크 키로만 잠근다. 처음 쓰는 회원이 동시에 와도 member_id UNIQUE가 막는다
    private MemberPoint lockMemberPoint(Long memberId, LocalDateTime now) {
        if (!memberPointRepository.existsByMemberId(memberId)) {
            memberPointRepository.insertIfAbsent(memberId, now);
        }
        return memberPointRepository.findByMemberIdForUpdate(memberId)
            .orElseThrow(() -> new IllegalStateException("회원 포인트 행을 보장한 뒤에도 찾을 수 없습니다. memberId=" + memberId));
    }

    // 지연 만료. 만료 배치가 아직 처리하지 않은 적립 건도 포인트를 쓰기 전에 먼저 소멸시켜 만료 포인트를 쓰지 못하게 한다.
    // 만료 배치도 이 메서드를 그대로 써서 소멸 규칙이 한 곳에만 있게 한다. 돌려주는 값은 이번에 소멸한 합계다
    private long expireStaleLots(MemberPoint memberPoint, Long memberId, LocalDate today) {
        long expired = 0L;
        for (PointEarning lot : pointEarningRepository.findExpiredForUpdate(memberId, today)) {
            expired += expireLot(memberPoint, lot);
        }
        return expired;
    }

    // 주문과 상관없는 소멸이라 이력에 주문을 남기지 않는다. 이미 만료됐거나 남은 잔량이 없으면 아무것도 하지 않는다
    private long expireLot(MemberPoint memberPoint, PointEarning lot) {
        long expired = lot.expire();
        if (expired > 0) {
            memberPoint.decrease(expired);
            pointHistoryRepository.save(PointHistory.expired(lot, null, expired));
        }
        return expired;
    }

    private Map<Long, PointEarning> lockSources(List<PointUsage> usages) {
        if (usages.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = usages.stream()
            .map(usage -> usage.getPointEarning().getId())
            .collect(Collectors.toSet());
        Map<Long, PointEarning> sources = pointEarningRepository.findAllByIdInForUpdate(ids).stream()
            .collect(Collectors.toMap(PointEarning::getId, Function.identity()));
        // 사용 내역이 가리키는 적립 건이 없으면 포인트 원장이 깨진 것이다. 복원 중 NPE 대신 원인을 남기고 멈춘다
        if (sources.size() != ids.size()) {
            throw new IllegalStateException("포인트 사용 내역의 원래 적립 건을 모두 찾을 수 없습니다. expected=" + ids.size()
                + ", actual=" + sources.size());
        }
        return sources;
    }

    // 사용 하나를 원래 적립 건으로 되돌린다. 만료 여부는 상태가 아니라 날짜로 본다 — 다 써서 잔량이 0인 적립 건은
    // 만료 처리 대상이 아니어서 만료일이 지나도 COMPLETED로 남아 있을 수 있다
    private void restoreOne(MemberPoint memberPoint, PointUsage usage, PointEarning source, LocalDate today) {
        long amount = usage.getAmount();
        if (source.isUsableOn(today)) {
            source.restore(amount);
            memberPoint.increase(amount);
            pointHistoryRepository.save(PointHistory.restored(usage));
            return;
        }
        // 원래 적립 건이 탈퇴로 소멸했다. 탈퇴한 회원에게 포인트를 다시 주지 않으므로 잔액에 넣지 않고, 되돌렸다는 기록과
        // 그 사용분의 탈퇴 소멸 이력만 남긴다. 복원 대상은 적립 예정 주문뿐이라 정상 흐름에서는 오지 않지만,
        // 오더라도 예외로 외부 취소를 롤백시키지 않는다. 아래 만료 처리보다 먼저 봐야 한다 — 소멸 건에 EXPIRED 이력은 남길 수 없다
        if (source.getStatus() == PointEarningStatus.FORFEITED) {
            pointHistoryRepository.save(PointHistory.restored(usage));
            pointHistoryRepository.save(PointHistory.withdrawalForfeited(source, usage.getOrderId(), amount));
            return;
        }
        // 원래 만료일이 지났다. 그 적립 건에 남아 있던 잔량을 먼저 소멸시키고, 환불된 사용분은 되돌렸다는 기록만 남긴 채
        // 잔액에 넣지 않고 바로 소멸시킨다. 소멸 금액은 적립 건 잔량이 아니라 이 사용분이다
        expireLot(memberPoint, source);
        pointHistoryRepository.save(PointHistory.restored(usage));
        pointHistoryRepository.save(PointHistory.expired(source, usage.getOrderId(), amount));
    }

    // 적립 예정·사용 예약은 주문이 끝나면 확정되거나 되돌아가는 돈이다. 남아 있으면 정산이 끝나지 않은 것이라 탈퇴를 막는다.
    // 잠그지 않는 조회라 이 트랜잭션의 스냅숏을 보지만, 늦게 보이는 PENDING·RESERVED는 막는 쪽으로만 틀려 재시도하면 통과한다
    private void requireNoUnsettled(Long memberId) {
        if (pointEarningRepository.existsByMemberIdAndStatus(memberId, PointEarningStatus.PENDING)
            || pointUsageRepository.existsReservedByMemberId(memberId)) {
            throw new BusinessException(PaymentErrorCode.POINT_UNSETTLED_FOR_WITHDRAWAL);
        }
    }

    // 회원이 화면에서 확인한 잔액과 다르면 그사이 잔액이 바뀐 것이다. 최신 잔액을 다시 안내받고 재시도하도록 거절한다
    private static void requireAcknowledged(long balance, long acknowledgedBalance) {
        if (balance != acknowledgedBalance) {
            throw new BusinessException(PaymentErrorCode.POINT_BALANCE_MISMATCH);
        }
    }

    // 같은 주문의 예약 재호출. confirmUsage는 한 트랜잭션에서 전부 확정하므로 정상이라면 전부 RESERVED이거나 전부 CONFIRMED다
    private static void requireSameReservation(Long orderId, List<PointUsage> existing, long usedPoint) {
        long reserved = existing.stream().mapToLong(PointUsage::getAmount).sum();
        boolean allReserved = existing.stream().allMatch(usage -> usage.getStatus() == PointUsageStatus.RESERVED);
        boolean allConfirmed = existing.stream().allMatch(usage -> usage.getStatus() == PointUsageStatus.CONFIRMED);
        if (reserved != usedPoint || !(allReserved || allConfirmed)) {
            throw new IllegalStateException("같은 주문에 다른 포인트 사용 내역이 이미 있습니다. orderId=" + orderId
                + ", reserved=" + reserved + ", requested=" + usedPoint);
        }
    }

    private static void requireSamePending(PointEarning earning, Long memberId, long amount) {
        boolean same = earning.getMemberId().equals(memberId)
            && earning.getAmount() == amount
            && earning.getStatus() == PointEarningStatus.PENDING;
        if (!same) {
            throw new IllegalStateException("같은 주문에 다른 적립 건이 이미 있습니다. orderId=" + earning.getOrderId()
                + ", status=" + earning.getStatus());
        }
    }

    private static void requireId(Long id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + "는 필수입니다.");
        }
    }

    private record RestoreTarget(MemberPoint memberPoint, PointEarning orderEarning, LocalDate today) {
    }
}
