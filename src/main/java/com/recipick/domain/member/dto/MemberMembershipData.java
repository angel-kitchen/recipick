package com.recipick.domain.member.dto;

import com.recipick.domain.member.entity.enums.MembershipType;
import java.time.LocalDateTime;

public record MemberMembershipData(Long memberId, MembershipType type, LocalDateTime endAt) {
}
