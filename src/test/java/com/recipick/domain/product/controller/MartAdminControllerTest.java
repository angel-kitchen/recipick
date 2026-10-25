package com.recipick.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class MartAdminControllerTest extends AdminApiTestSupport {

    private Mart martA;
    private Mart martB;

    @BeforeEach
    void setUp() {
        martA = saveMart(925L, "마트 컨트롤러A");
        martB = saveMart(926L, "마트 컨트롤러B");
    }

    @Test
    @DisplayName("SUPER_ADMIN은 모든 마트 목록을 조회한다")
    void listBySuperAdmin() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/admin/marts", superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("MART_ADMIN은 본인 마트 하나만 조회한다")
    void listByMartAdmin() throws Exception {
        // when
        MockHttpServletResponse response = get("/api/admin/marts", martAdminToken(martA.getId()));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).size()).isEqualTo(1);
        assertThat(data(response).get(0).path("martId").asLong()).isEqualTo(martA.getId());
    }

    @Test
    @DisplayName("SUPER_ADMIN은 서비스 반경과 상태를 수정한다")
    void updateBySuperAdmin() throws Exception {
        // when
        MockHttpServletResponse response = patch("/api/admin/marts/" + martA.getId(), superToken(),
            Map.of("serviceRadiusM", 2500, "status", "CLOSED"));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(martA.getServiceRadiusM()).isEqualTo(2500);
        assertThat(martA.getStatus()).isEqualTo(MartStatus.CLOSED);
    }

    @Test
    @DisplayName("MART_ADMIN은 본인 마트의 운영 상태를 바꿀 수 있다")
    void updateStatusByMartAdmin() throws Exception {
        // when
        MockHttpServletResponse response = patch("/api/admin/marts/" + martA.getId(),
            martAdminToken(martA.getId()), Map.of("status", "CLOSED"));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(martA.getStatus()).isEqualTo(MartStatus.CLOSED);
    }

    @Test
    @DisplayName("MART_ADMIN이 서비스 반경을 보내면 403 SUPER_ADMIN_ONLY")
    void updateRadiusByMartAdmin() throws Exception {
        // when
        MockHttpServletResponse response = patch("/api/admin/marts/" + martA.getId(),
            martAdminToken(martA.getId()), Map.of("serviceRadiusM", 3000));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-006");
        assertThat(martA.getServiceRadiusM()).isEqualTo(2000);
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트를 수정하면 403 MART_ACCESS_DENIED")
    void updateOtherMart() throws Exception {
        // when
        MockHttpServletResponse response = patch("/api/admin/marts/" + martB.getId(),
            martAdminToken(martA.getId()), Map.of("status", "CLOSED"));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
        assertThat(martB.getStatus()).isEqualTo(MartStatus.OPEN);
    }

    @Test
    @DisplayName("위도만 보내면 400 INVALID_MART_AREA")
    void updateOnlyLatitude() throws Exception {
        // when
        MockHttpServletResponse response = patch("/api/admin/marts/" + martA.getId(), superToken(),
            Map.of("latitude", "37.6000000"));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("MART-004");
    }

    @Test
    @DisplayName("MART_ADMIN은 마트 등록을 할 수 없다(403 SUPER_ADMIN_ONLY)")
    void createByMartAdmin() throws Exception {
        // given
        Map<String, Object> request = Map.of(
            "regionId", 927, "name", "새 마트", "latitude", "37.5000000", "longitude", "127.0000000");

        // when
        MockHttpServletResponse response = post("/api/admin/marts", martAdminToken(martA.getId()), request);

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-006");
    }
}
