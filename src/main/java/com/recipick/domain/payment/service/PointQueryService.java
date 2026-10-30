package com.recipick.domain.payment.service;

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.dto.response.PointEarningResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.repository.PointHistoryRepository;
import com.recipick.domain.payment.repository.dto.PointExpirySummary;
import com.recipick.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 포인트 조회. 상태를 바꾸지 않는다 — 잠그지 않고, 만료 처리·이력 INSERT·잔액 수정도 하지 않는다.
// 만료일이 지난 잔량은 날짜 조건으로 계산에서 빼기만 하고, 실제 소멸은 만료 배치와 포인트 사용 직전의 지연 만료가 맡는다.
// 잔액과 가까운 만료를 한 읽기 트랜잭션에서 읽어 같은 시점의 값으로 맞춘다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {

    // 최신순. createdAt이 같은 행의 순서가 페이지마다 흔들리지 않도록 id를 보조 정렬로 둔다
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PointEarningRepository pointEarningRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final Clock clock;

    // 포인트를 써 본 적 없는 회원도 404가 아니라 0으로 답한다
    public PointBalanceResponse getBalance(Long memberId) {
        LocalDate today = LocalDate.now(clock);
        long available = pointEarningRepository.sumUsableRemaining(memberId, today);
        List<PointExpirySummary> nearest =
            pointEarningRepository.findUsableExpiries(memberId, today, PageRequest.of(0, 1));
        if (nearest.isEmpty()) {
            return new PointBalanceResponse(memberId, available, 0L, null);
        }
        PointExpirySummary first = nearest.get(0);
        return new PointBalanceResponse(memberId, available, first.getAmount(), first.getExpiresOn());
    }

    // 적립 건은 상태와 관계없이 모두 보여준다 — 적립 예정부터 확정·취소·만료까지가 한 적립 건의 기록이다
    public PageResponse<PointEarningResponse> findEarnings(Long memberId, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return PageResponse.from(pointEarningRepository.findByMemberId(memberId, latestFirst(pageable))
            .map(earning -> PointEarningResponse.of(earning, today)));
    }

    // 주문 ID가 있으면 그 주문의 이력만 본다. 회원 조건이 항상 함께 붙어 다른 회원의 주문이면 빈 페이지다
    public PageResponse<PointHistoryResponse> findHistory(Long memberId, Long orderId, Pageable pageable) {
        Pageable fixed = latestFirst(pageable);
        return PageResponse.from((orderId == null
            ? pointHistoryRepository.findByMemberId(memberId, fixed)
            : pointHistoryRepository.findByMemberIdAndOrderId(memberId, orderId, fixed))
            .map(PointHistoryResponse::from));
    }

    // 만료 배치가 쓰는 대상 회원 목록. afterMemberId보다 큰 회원 ID를 오름차순으로 limit개까지 돌려준다
    public List<Long> findExpirationCandidateMemberIds(LocalDate today, Long afterMemberId, int limit) {
        Objects.requireNonNull(today, "기준 날짜는 필수입니다.");
        return pointEarningRepository.findExpirationCandidateMemberIds(
            today, afterMemberId, PageRequest.of(0, limit));
    }

    // 클라이언트가 보낸 정렬은 버린다. 최신순은 서버가 정하는 계약이라 page·size만 받아 다시 만든다
    private Pageable latestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
    }
}
