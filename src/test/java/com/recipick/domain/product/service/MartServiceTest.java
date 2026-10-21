package com.recipick.domain.product.service;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.MartIdResponse;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.MartUpdateRequest;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.catchThrowable;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class MartServiceTest {

    @Mock
    private MartRepository martRepository;

    @InjectMocks
    private MartService martService;

    private MartCreateRequest request(String latitude, String longitude,
                                      Integer radius) {
        return new MartCreateRequest(
            31L, "삼청동 마트", new BigDecimal(latitude),
            new BigDecimal(longitude), radius);
    }

    private void stubSave() {
        given(martRepository.save(any(Mart.class))).willAnswer(invocation -> {
            Mart mart = invocation.getArgument(0);
            ReflectionTestUtils.setField(mart, "id", 1L);
            return mart;
        });
    }

    @Test
    @DisplayName("등록 : 성공하면 저장된 martId를 돌려주고 상태는 OPEN이다")
    void createMart() {
        given(martRepository.existsByRegionId(31L)).willReturn(false);
        stubSave();

        MartIdResponse result = martService.createMart(request("37.367000", "127.1080000", 2500));

        assertThat(result.martId()).isEqualTo(1L);
        ArgumentCaptor<Mart> captor = ArgumentCaptor.forClass(Mart.class);
        verify(martRepository).save(captor.capture());
        assertThat(captor.getValue().getServiceRadiusM()).isEqualTo(2500);
        assertThat(captor.getValue().getStatus().name()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("등록 : 반경을 생략하면 기본값 2000이 저장된다.")
    void CreateMart_defaultRadius() {
        given(martRepository.existsByRegionId(31L)).willReturn(false);
        stubSave();

        martService.createMart(request("37.367000", "127.1080000", null));

        ArgumentCaptor<Mart> captor = ArgumentCaptor.forClass(Mart.class);
        verify(martRepository).save(captor.capture());
        assertThat(captor.getValue().getServiceRadiusM()).isEqualTo(2000);
    }

    @Test
    @DisplayName("등록 : 위도, 경도가 경계값(90,180)이면 성공한다.")
    void createMart_boundaryCoordinates() {
        given(martRepository.existsByRegionId(31L)).willReturn(false);
        stubSave();

        MartIdResponse result = martService.createMart(request("90", "-180", 1));

        assertThat(result.martId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록: 위도가 범위 밖이면 INVALID_MART_AREA")
    void createMart_invalidLatitude() {
        assertThatThrownBy(() -> martService.createMart(request("90.0000001",
            "127.1080000", 2000)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        verify(martRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 반경이 0 이하이면 INVALID_MART_AREA")
    void createMart_invalidRadius() {
        assertThatThrownBy(() -> martService.createMart(request("37.3670000",
            "127.1080000", 0)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        assertThatThrownBy(() -> martService.createMart(request("37.3670000",
            "127.1080000", -1)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        verify(martRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 : 그 지역에 이미 마트가 있으면 MART_REGION_DUPLICATED")
    void createMart_regionDuplicated() {
        given(martRepository.existsByRegionId(31L)).willReturn(true);

        assertThatThrownBy(() -> martService.createMart(request("37.3670000",
            "127.1080000", 2000)))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_REGION_DUPLICATED);
        verify(martRepository, never()).save(any());
    }

    private Mart mart(Long id, Long regionId, String name){
        Mart mart = Mart.builder()
            .regionId(regionId)
            .name(name)
            .latitude(new BigDecimal("37.367000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", id);
        return mart;
    }

    @Test
    @DisplayName("목록 : SUPER_ADMIN은 운영 상태와 관계없이 모든 마트를 조회한다.")
    void getMarts_superAdmin(){
        Mart open = mart(1L, 31L, "삼청동 마트");
        Mart closed = mart(2L, 32L, "혜화동 마트");
        ReflectionTestUtils.setField(closed, "status", MartStatus.CLOSED);
        given(martRepository.findAll()).willReturn(List.of(open,closed));

        List<MartResponse> result = martService.getMarts(AdminRole.SUPER_ADMIN, null);
        assertThat(result).extracting(MartResponse :: martId).containsExactly(1L,2L);
        assertThat(result).extracting(MartResponse :: status).containsExactly(MartStatus.OPEN, MartStatus.CLOSED);
    }

    @Test
    @DisplayName("목록 : SUPER_ADMIN은 마트가 없으면 빈 목록")
    void getMarts_superAdmin_empty(){
        given(martRepository.findAll()).willReturn(List.of());

        assertThat(martService.getMarts(AdminRole.SUPER_ADMIN, null)).isEmpty();
    }

    @Test
    @DisplayName("목록: MART_ADMIN은 본인 마트 하나만 조회한다")
    void getMarts_martAdmin(){
        given(martRepository.findById(1L)).willReturn(Optional.of(mart(1L, 31L, "삼청동 마트")));

        List<MartResponse> result = martService.getMarts(AdminRole.MART_ADMIN, 1L);
        assertThat(result).extracting(MartResponse :: martId).containsExactly(1L);
        verify(martRepository, never()).findAll();
    }

    @Test
    @DisplayName("목록 : MART_ADMIN의 마트가 없으면 빈 목록")
    void getMarts_martAdmin_empty(){
        given(martRepository.findById(99L)).willReturn(Optional.empty());
        assertThat(martService.getMarts(AdminRole.MART_ADMIN, 99L)).isEmpty();

    }

    private Mart martWithId(Long id) {
        Mart mart = Mart.builder()
            .regionId(31L)
            .name("삼청동 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", id);
        return mart;
    }

    private MartUpdateRequest updateRequest(String latitude, String longitude,
                                            Integer radius, MartStatus status) {
        return new MartUpdateRequest(
            latitude == null ? null : new BigDecimal(latitude),
            longitude == null ? null : new BigDecimal(longitude),
            radius, status);
    }

    @Test
    @DisplayName("수정 : SUPER_ADMIN이 보낸 필드만 바꾸면 나머지는 유지된다")
    void updateMart_partial() {
        // given
        Mart mart = martWithId(1L);
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mart));

        // when
        MartIdResponse result = martService.updateMart(
            AdminRole.SUPER_ADMIN, null, 1L, updateRequest(null, null, 2500, null));

        // then
        assertThat(result.martId()).isEqualTo(1L);
        assertThat(mart.getServiceRadiusM()).isEqualTo(2500);
        assertThat(mart.getLatitude()).isEqualByComparingTo("37.3670000");
        assertThat(mart.getStatus()).isEqualTo(MartStatus.OPEN);
    }

    @Test
    @DisplayName("수정 : SUPER_ADMIN이 좌표·반경·상태를 모두 바꾼다")
    void updateMart_all() {
        // given
        Mart mart = martWithId(1L);
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mart));

        // when
        martService.updateMart(AdminRole.SUPER_ADMIN, null, 1L,
            updateRequest("37.5000000", "127.0000000", 3000, MartStatus.CLOSED));

        // then
        assertThat(mart.getLatitude()).isEqualByComparingTo("37.5000000");
        assertThat(mart.getLongitude()).isEqualByComparingTo("127.0000000");
        assertThat(mart.getServiceRadiusM()).isEqualTo(3000);
        assertThat(mart.getStatus()).isEqualTo(MartStatus.CLOSED);
    }

    @Test
    @DisplayName("수정 : MART_ADMIN은 본인 마트의 상태를 바꿀 수 있다")
    void updateMart_martAdminStatus() {
        // given
        Mart mart = martWithId(1L);
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mart));

        // when
        martService.updateMart(AdminRole.MART_ADMIN, 1L, 1L,
            updateRequest(null, null, null, MartStatus.CLOSED));

        // then
        assertThat(mart.getStatus()).isEqualTo(MartStatus.CLOSED);
    }

    @Test
    @DisplayName("수정 : MART_ADMIN이 다른 마트를 지정하면 MART_ACCESS_DENIED이고 조회하지 않는다")
    void updateMart_martAdminOtherMart() {
        // given
        MartUpdateRequest request = updateRequest(null, null, null, MartStatus.CLOSED);

        // when
        Throwable thrown = catchThrowable(
            () -> martService.updateMart(AdminRole.MART_ADMIN, 1L, 2L, request));

        //then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verifyNoInteractions(martRepository);


        assertThatThrownBy(() -> martService.updateMart(AdminRole.MART_ADMIN, 1L, 2L, request))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verifyNoInteractions(martRepository);
    }

    @Test
    @DisplayName("수정 : MART_ADMIN이 좌표나 반경을 보내면 SUPER_ADMIN_ONLY")
    void updateMart_martAdminAreaDenied() {

        // given
        MartUpdateRequest radiusRequest = updateRequest(null, null, 3000, null);
        MartUpdateRequest coordinateRequest = updateRequest("37.5000000", "127.0000000", null, null);

        // when
        Throwable radiusThrown = catchThrowable(
            () -> martService.updateMart(AdminRole.MART_ADMIN, 1L, 1L, radiusRequest));
        Throwable coordinateThrown = catchThrowable(
            () -> martService.updateMart(AdminRole.MART_ADMIN, 1L, 1L, coordinateRequest));

        // then
        assertThat(radiusThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.SUPER_ADMIN_ONLY);
        assertThat(coordinateThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.SUPER_ADMIN_ONLY);
        verifyNoInteractions(martRepository);
    }

    @Test
    @DisplayName("수정 : 위도와 경도 중 한쪽만 보내면 INVALID_MART_AREA이고 값은 바뀌지 않는다")
    void updateMart_onlyOneCoordinate() {
        // given
        Mart mart = martWithId(1L);
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mart));
        MartUpdateRequest latitudeOnly = updateRequest("37.5000000", null, null, null);
        MartUpdateRequest longitudeOnly = updateRequest(null, "127.0000000", null, null);

        // when
        Throwable latitudeThrown = catchThrowable(
            () -> martService.updateMart(AdminRole.SUPER_ADMIN, null, 1L, latitudeOnly));
        Throwable longitudeThrown = catchThrowable(
            () -> martService.updateMart(AdminRole.SUPER_ADMIN, null, 1L, longitudeOnly));

        // then
        assertThat(latitudeThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        assertThat(longitudeThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        assertThat(mart.getLatitude()).isEqualByComparingTo("37.3670000");
    }

    @Test
    @DisplayName("수정 : 좌표가 범위 밖이거나 반경이 0 이하이면 INVALID_MART_AREA")
    void updateMart_invalidArea() {
        // given
        given(martRepository.findByIdForUpdate(1L)).willReturn(Optional.of(martWithId(1L)));
        MartUpdateRequest badCoordinate = updateRequest("91.0000000", "127.0000000", null, null);
        MartUpdateRequest badRadius = updateRequest(null, null, 0, null);

        // when
        Throwable coordinateThrown = catchThrowable(
            () -> martService.updateMart(AdminRole.SUPER_ADMIN, null, 1L, badCoordinate));
        Throwable radiusThrown = catchThrowable(
            () -> martService.updateMart(AdminRole.SUPER_ADMIN, null, 1L, badRadius));

        // then
        assertThat(coordinateThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
        assertThat(radiusThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_MART_AREA);
    }

    @Test
    @DisplayName("수정 : 없는 마트이면 MART_NOT_FOUND")
    void updateMart_notFound() {
        // given
        given(martRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        MartUpdateRequest request = updateRequest(null, null, 3000, null);

        // when
        Throwable thrown = catchThrowable(
            () -> martService.updateMart(AdminRole.SUPER_ADMIN, null, 99L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.MART_NOT_FOUND);
    }

    @Test
    @DisplayName("판매 상태 변경 : null이면 INVALID_INPUT이고 상태는 그대로다")
    void changeStatus_null() {
        // given
        Product product = Product.builder().name("애호박").totalQuantity(5).build();

        // when
        Throwable thrown = catchThrowable(() -> product.changeStatus(null));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }
}
