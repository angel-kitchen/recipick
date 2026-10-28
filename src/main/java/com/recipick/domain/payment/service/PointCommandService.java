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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 포인트 자산 변경 명령. MEMBER_POINT는 회원 포인트 변경의 직렬화 지점, POINT_EARNING은 실제 자산 lot,
// POINT_USAGE는 어느 lot의 포인트를 어느 주문이 가져갔는지 남기는 연결 원장이라 잔액만 바꾸지 않고 셋과 이력을 함께 움직인다.
// 포인트만 따로 커밋되면 주문·결제 상태와 어긋나므로 모든 명령은 호출한 주문·결제 트랜잭션 안에서만 실행한다(MANDATORY)
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

    // 주문 생성에서 주문 INSERT 뒤·재고 차감 전에 부른다
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

        List<PointUsage> existing = pointUsageRepository.findAllByOrderIdForUpdate(orderId);
        if (!existing.isEmpty()) {
            requireSameReservation(orderId, existing, usedPoint);
            return;
        }

        expireStaleLots(memberPoint, memberId, today);

        List<PointEarning> usable = new ArrayList<>(pointEarningRepository.findUsableForUpdate(memberId, today));
        long usableTotal = usable.stream().mapToLong(PointEarning::getRemainingAmount).sum();

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
    // 적립 건을 두 번(이 주문의 적립 건 → 사용한 적립 건들) 나눠 잠그지만, 같은 회원의 포인트 변경은 먼저 잡은 MEMBER_POINT에서
    // 줄을 서고 이 주문의 적립 건(PENDING)과 사용한 적립 건(COMPLETED·EXPIRED)은 겹치지 않아 교착이 생기지 않는다
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreUsageAndCancelEarning(Long orderId) {
        requireId(orderId, "주문 ID");
        Optional<Long> memberId = findPointMemberId(orderId);
        if (memberId.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        MemberPoint memberPoint = lockMemberPoint(memberId.get(), now);
        PointEarning orderEarning = pointEarningRepository.findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalStateException("주문의 적립 건을 찾을 수 없습니다. orderId=" + orderId));
        // 배송완료로 적립이 확정된 주문은 거래가 끝난 것이라 사용 포인트도 적립도 자동으로 되돌리지 않는다(회수 없음)
        if (orderEarning.getStatus() == PointEarningStatus.COMPLETED
            || orderEarning.getStatus() == PointEarningStatus.EXPIRED) {
            throw new IllegalStateException("적립이 확정된 주문의 포인트는 자동으로 되돌리지 않습니다. orderId=" + orderId
                + ", status=" + orderEarning.getStatus());
        }

        List<PointUsage> usages = pointUsageRepository.findAllByOrderIdForUpdate(orderId);
        Map<Long, PointEarning> sources = lockSources(usages);
        for (PointUsage usage : usages) {
            if (usage.restore()) {
                restoreOne(memberPoint, usage, sources.get(usage.getPointEarning().getId()), today);
            }
        }

        if (orderEarning.cancel() && orderEarning.getAmount() > 0) {
            pointHistoryRepository.save(PointHistory.earnCanceled(orderEarning));
        }
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

    // 적립 건이 없는 주문은 포인트가 없던 1차 주문이라 할 일이 없다(정상 경로라 로그를 남기지 않는다).
    // 적립 건 엔티티를 잠금 전에 읽어 두면 잠금 뒤에도 예전 값을 보게 되므로 회원 ID 값만 꺼낸다
    private Optional<Long> findPointMemberId(Long orderId) {
        return pointEarningRepository.findMemberIdByOrderId(orderId);
    }

    // 회원 포인트 변경의 첫 잠금. 처음 포인트를 다루는 회원은 잠글 행이 없어 먼저 행을 보장한다
    private MemberPoint lockMemberPoint(Long memberId, LocalDateTime now) {
        memberPointRepository.insertIfAbsent(memberId, now);
        return memberPointRepository.findByMemberIdForUpdate(memberId)
            .orElseThrow(() -> new IllegalStateException("회원 포인트 행을 보장한 뒤에도 찾을 수 없습니다. memberId=" + memberId));
    }

    // 지연 만료. 만료 배치가 아직 처리하지 않은 적립 건도 포인트를 쓰기 전에 먼저 소멸시켜 만료 포인트를 쓰지 못하게 한다
    private void expireStaleLots(MemberPoint memberPoint, Long memberId, LocalDate today) {
        for (PointEarning lot : pointEarningRepository.findExpiredForUpdate(memberId, today)) {
            expireLot(memberPoint, lot);
        }
    }

    // 주문과 상관없는 소멸이라 이력에 주문을 남기지 않는다. 이미 만료됐거나 남은 잔량이 없으면 아무것도 하지 않는다
    private void expireLot(MemberPoint memberPoint, PointEarning lot) {
        long expired = lot.expire();
        if (expired > 0) {
            memberPoint.decrease(expired);
            pointHistoryRepository.save(PointHistory.expired(lot, null, expired));
        }
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
        // 원래 만료일이 지났다. 그 적립 건에 남아 있던 잔량을 먼저 소멸시키고, 환불된 사용분은 되돌렸다는 기록만 남긴 채
        // 잔액에 넣지 않고 바로 소멸시킨다. 소멸 금액은 적립 건 잔량이 아니라 이 사용분이다
        expireLot(memberPoint, source);
        pointHistoryRepository.save(PointHistory.restored(usage));
        pointHistoryRepository.save(PointHistory.expired(source, usage.getOrderId(), amount));
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
}
