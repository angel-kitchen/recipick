package com.recipick.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipStatus;
import com.recipick.domain.member.entity.enums.MembershipType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MemberEntityIntegrationTest extends IntegrationTestSupport {

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    DataSource dataSource;

    private Member persistMember(String email, String phone) {
        Member member = Member.builder().email(email).phone(phone).name("엔티티검증").build();
        entityManager.persist(member);
        return member;
    }

    @Test
    @DisplayName("백필로 기존 혜택을 복구하고 결제 대기 유료 행은 제외한다")
    void backfillsOnlyTrialAndAdminGrantWithoutChangingBenefits() {
        // given
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
        Member member = persistMember("activation@example.com", "01099990003");
        Membership trial = Membership.builder().member(member).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(start).activatedAt(start)
                .endAt(start.plusDays(7)).build();
        Membership grant = Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.ADMIN_GRANT).startAt(start).activatedAt(start)
                .endAt(start.plusDays(30)).build();
        Membership pending = Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.SUBSCRIBE).startAt(start).endAt(start.plusMonths(1)).build();
        Membership conversion = Membership.builder().member(member).type(MembershipType.PAID)
                .source(MembershipSource.TRIAL_CONVERT).startAt(start).endAt(start.plusMonths(1)).build();
        entityManager.persist(trial);
        entityManager.persist(grant);
        entityManager.persist(pending);
        entityManager.persist(conversion);
        entityManager.flush();
        Long trialId = trial.getId();
        Long grantId = grant.getId();
        Long pendingId = pending.getId();
        Long conversionId = conversion.getId();
        entityManager.clear();
        assertThat(entityManager.find(Membership.class, trialId).getActivatedAt()).isEqualTo(start);
        assertThat(entityManager.find(Membership.class, grantId).getGraceEndAt()).isNull();
        // 기존 데이터의 NULL 컬럼 상태를 재현한다.
        entityManager.createNativeQuery("UPDATE membership SET activated_at = NULL WHERE id IN (:trial, :grant)")
                .setParameter("trial", trialId).setParameter("grant", grantId).executeUpdate();
        entityManager.clear();
        Membership legacy = entityManager.find(Membership.class, trialId);
        assertThat(legacy.getActivatedAt()).isNull();
        assertThat(legacy.isActive(start.plusDays(1))).isFalse();

        String backfill = "UPDATE membership SET activated_at = start_at, updated_at = NOW() "
                + "WHERE activated_at IS NULL AND source IN ('TRIAL', 'ADMIN_GRANT')";
        // when
        entityManager.createNativeQuery(backfill).executeUpdate();
        entityManager.clear();
        // then
        assertThat(entityManager.find(Membership.class, trialId).getActivatedAt()).isEqualTo(start);
        assertThat(entityManager.find(Membership.class, trialId).isActive(start.plusDays(1))).isTrue();
        assertThat(entityManager.find(Membership.class, grantId).getActivatedAt()).isEqualTo(start);
        assertThat(entityManager.find(Membership.class, pendingId).getActivatedAt()).isNull();
        assertThat(entityManager.find(Membership.class, conversionId).getActivatedAt()).isNull();
        assertThat(entityManager.createNativeQuery(backfill).executeUpdate()).isZero();
    }

    @Test
    @DisplayName("서비스 밖 주소와 회원의 현재 배송지 참조를 저장하고 다시 조회한다")
    void persistsSelectedAddressAndOwnership() {
        // given
        Member member = persistMember("entity@example.com", "01099990001");
        MemberAddress address = MemberAddress.builder().member(member).label("집")
                .address("배송지 주소").detailAddress("101호").legalDongCode("9999999999").build();
        entityManager.persist(address);
        member.selectAddress(address, null);
        // when
        entityManager.flush();
        Long memberId = member.getId();
        Long addressId = address.getId();
        entityManager.clear();
        Member loaded = entityManager.find(Member.class, memberId);
        // then
        assertThat(loaded.getSelectedAddress().getId()).isEqualTo(addressId);
        assertThat(loaded.getSelectedAddress().isOwnedBy(loaded)).isTrue();
        assertThat(loaded.getSelectedAddress().getLegalDongCode()).isEqualTo("9999999999");
        assertThat(loaded.getSelectedAddress().getCreatedAt()).isNotNull();
        assertThat(loaded.getSelectedAddress().getUpdatedAt()).isNotNull();
        assertThat(loaded.getMartId()).isNull();
    }

    @Test
    @DisplayName("지역은 좌표 없이 저장하며 관리자 생성·수정 일시는 Auditing으로 기록한다")
    void persistsRegionAndAdminAuditTimes() {
        // given
        Region region = Region.builder().legalDongCode("9999999999").name("테스트동").build();
        Admin admin = Admin.builder().loginId("entity-admin").name("관리자")
                .role(AdminRole.SUPER_ADMIN).active(true).build();
        entityManager.persist(region);
        entityManager.persist(admin);
        // when
        entityManager.flush();
        entityManager.clear();
        Region loadedRegion = entityManager.find(Region.class, region.getId());
        Admin loadedAdmin = entityManager.find(Admin.class, admin.getId());
        // then
        assertThat(loadedRegion.getLegalDongCode()).isEqualTo("9999999999");
        assertThat(loadedAdmin.getCreatedAt()).isNotNull();
        assertThat(loadedAdmin.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("해지 예약과 환불 후 종료 기록은 저장 후에도 동일한 상태로 계산한다")
    void persistsMembershipTransitions() {
        // given
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
        Member member = persistMember("membership@example.com", "01099990002");
        Membership membership = Membership.builder().member(member)
                .type(MembershipType.PAID).source(MembershipSource.SUBSCRIBE)
                .startAt(start).activatedAt(start).endAt(start.plusMonths(1)).autoRenew(true).build();
        entityManager.persist(membership);
        membership.reserveCancellation(start.plusDays(1));
        // when
        entityManager.flush();
        entityManager.clear();
        Membership loaded = entityManager.find(Membership.class, membership.getId());
        // then
        assertThat(loaded.getStatus(start.plusDays(2))).contains(MembershipStatus.CANCEL_RESERVED);
        assertThat(loaded.isAutoRenew()).isFalse();
        // when
        loaded.terminateAfterRefund(start.plusDays(3));
        entityManager.flush();
        entityManager.clear();
        loaded = entityManager.find(Membership.class, membership.getId());
        // then
        assertThat(loaded.getTerminatedAt()).isEqualTo(start.plusDays(3));
        assertThat(loaded.getStatus(start.plusDays(3))).contains(MembershipStatus.TERMINATED);
    }

    @Test
    @DisplayName("회원 테이블의 이전 배송지 컬럼과 지역 좌표 없이 최신 스키마를 생성한다")
    void createsLatestMemberSchema() throws Exception {
        // given
        Set<String> removedMemberColumns = Set.of(
                "region_id", "delivery_address", "delivery_detail_address", "delivery_region_id");
        // when
        Set<String> memberColumns = columns("member");
        Set<String> regionColumns = columns("region");
        Set<String> addressColumns = columns("member_address");
        // then
        assertThat(memberColumns).contains("selected_address_id")
                .doesNotContainAnyElementsOf(removedMemberColumns);
        assertThat(regionColumns).containsExactlyInAnyOrder("id", "legal_dong_code", "name");
        assertThat(addressColumns).contains("member_id", "label", "address", "detail_address", "legal_dong_code")
                .doesNotContain("latitude", "longitude", "selected", "mart_id");
        assertThat(columns("membership")).doesNotContain("status");
        assertThat(columns("admin")).contains("created_at", "updated_at");
    }

    private Set<String> columns(String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
                ResultSet columns = connection.getMetaData().getColumns(
                        connection.getCatalog(), connection.getSchema(), table, null)) {
            Set<String> result = new HashSet<>();
            while (columns.next()) {
                result.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            return result;
        }
    }
}
