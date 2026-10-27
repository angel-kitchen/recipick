package com.recipick.domain.member.dto.request;

import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.entity.enums.MembershipSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record MemberAdminSearchRequest(
        @Size(max = 50) String email,
        Boolean membershipActive,
        @Size(max = 50) String name,
        MembershipType membershipType,
        MembershipSource membershipSource,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Pattern(regexp = "(createdAt|email|name|memberId),(?i:asc|desc)",
                message = "정렬은 createdAt·email·name·memberId와 asc·desc를 사용해야 합니다.") String sort
) {
    public MemberAdminSearchRequest {
        email = email == null ? null : email.strip();
        name = name == null ? null : name.strip();
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sort = sort == null ? "createdAt,desc" : sort.strip();
    }

    public Pageable toPageable() {
        String[] parts = sort.split(",", -1);
        String property = parts[0].equals("memberId") ? "id" : parts[0];
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]), property));
    }

    @Override
    public String toString() {
        return "MemberAdminSearchRequest[REDACTED]";
    }
}
