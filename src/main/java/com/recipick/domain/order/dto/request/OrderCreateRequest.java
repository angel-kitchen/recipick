package com.recipick.domain.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

// 법정동 코드 형식 오류는 요구사항상 ORDER-006으로 응답해야 해서 여기서 검증하지 않고 Facade가 검증한다.
// 사용 포인트는 필드를 보내지 않으면 0이다. 상품금액 초과 여부는 재고를 잠근 뒤의 가격으로만 알 수 있어 OrderAmount가 검증한다
public record OrderCreateRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> cartItemIds,
    @NotBlank @Size(max = 50) String receiverName,
    @NotBlank @Pattern(regexp = "\\d{10,11}") String receiverPhone,
    @NotBlank @Size(max = 255) String address,
    @Size(max = 255) String detailAddress,
    String legalDongCode,
    @PositiveOrZero long usedPoint
) {

    // 포인트를 쓰지 않는 요청. 포인트 연동 전부터 있던 호출부가 그대로 0P 주문을 만든다
    public OrderCreateRequest(List<Long> cartItemIds, String receiverName, String receiverPhone, String address,
                              String detailAddress, String legalDongCode) {
        this(cartItemIds, receiverName, receiverPhone, address, detailAddress, legalDongCode, 0L);
    }

    // 금액은 서버가 계산하므로 넣지 않고, 항목 순서만 다른 재시도는 같은 요청으로 본다
    public String requestHash() {
        String sortedCartItemIds = cartItemIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        List<String> fields = new ArrayList<>(Arrays.asList(
            sortedCartItemIds, receiverName, receiverPhone, address, detailAddress, legalDongCode));
        // 포인트를 쓰지 않는 요청은 포인트 도입 전과 같은 해시가 나와야 배포 직전에 만든 결제 대기 주문의
        // 같은 키 재시도가 COMMON-011이 되지 않는다. 쓰는 요청만 사용 포인트를 붙여 금액이 다른 요청과 구분한다
        if (usedPoint > 0) {
            fields.add(String.valueOf(usedPoint));
        }
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            String value = field == null ? "" : field;
            // 값 안에 구분자가 있어도 서로 다른 요청이 같은 문자열이 되지 않도록 길이를 앞에 붙인다
            canonical.append(value.length()).append(':').append(value);
        }
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
