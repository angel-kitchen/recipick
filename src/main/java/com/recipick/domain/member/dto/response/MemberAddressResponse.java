package com.recipick.domain.member.dto.response;

public record MemberAddressResponse(Long addressId, String label, String address, String detailAddress,
        String legalDongCode, boolean serviceAvailable, boolean selected, Mart mart, boolean martChanged) {

    public record Mart(Long martId, String name) {
    }

    @Override
    public String toString() {
        return "MemberAddressResponse[REDACTED]";
    }
}
