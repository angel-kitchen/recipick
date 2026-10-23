package com.recipick.domain.member.repository;

import com.recipick.domain.member.dto.MemberAdminRow;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberRepositoryCustom {

    Page<MemberAdminRow> searchMembers(String email, Boolean membershipActive, LocalDateTime now, Pageable pageable);
}
