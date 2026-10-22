package com.recipick.domain.member.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberAddressUpdateRequest(
        @Size(max = 30) String label,
        @Size(max = 255) String address,
        @Size(max = 255) String detailAddress,
        @Pattern(regexp = "[0-9]{10}", message = "법정동 코드는 숫자 10자리여야 합니다.") String legalDongCode
) {
    @JsonCreator
    public static MemberAddressUpdateRequest fromJson(@JsonProperty("label") JsonNode label,
            @JsonProperty("address") JsonNode address, @JsonProperty("detailAddress") JsonNode detailAddress,
            @JsonProperty("legalDongCode") JsonNode legalDongCode) {
        return new MemberAddressUpdateRequest(readValue(label), readValue(address),
                readValue(detailAddress), readValue(legalDongCode));
    }

    // PATCH에서 생략은 유지, 명시적 null은 비우기로 구분한다.
    private static String readValue(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("배송지 수정 값은 문자열이어야 합니다.");
        }
        return value.textValue();
    }

    @AssertTrue(message = "수정할 항목이 하나 이상 필요합니다.")
    public boolean isUpdatePresent() {
        return label != null || address != null || detailAddress != null || legalDongCode != null;
    }

    @AssertTrue(message = "주소·상세 주소·법정동 코드는 빈 값 없이 함께 보내야 합니다.")
    public boolean isAddressGroupValid() {
        if (address == null && detailAddress == null && legalDongCode == null) {
            return true;
        }
        return address != null && !address.isBlank()
                && detailAddress != null && !detailAddress.isBlank() && legalDongCode != null;
    }

    @Override
    public String toString() {
        return "MemberAddressUpdateRequest[REDACTED]";
    }
}
