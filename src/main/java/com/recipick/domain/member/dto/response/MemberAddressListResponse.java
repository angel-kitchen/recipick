package com.recipick.domain.member.dto.response;

import java.util.List;

public record MemberAddressListResponse(int maxCount, List<Address> addresses) {

    public record Address(Long addressId, String label, String address, String detailAddress,
            String legalDongCode, boolean serviceAvailable, boolean selected) {
        @Override
        public String toString() {
            return "MemberAddressListResponse.Address[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "MemberAddressListResponse[REDACTED]";
    }
}
