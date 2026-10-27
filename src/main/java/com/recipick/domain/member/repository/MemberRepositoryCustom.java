package com.recipick.domain.member.repository;

import com.recipick.domain.member.dto.MemberAdminRow;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.entity.enums.MembershipSource;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberRepositoryCustom {

    Page<MemberAdminRow> searchMembers(String email, String name, Boolean membershipActive, MembershipType type,
            MembershipSource source, LocalDateTime now, Pageable pageable);
}
