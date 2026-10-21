package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    @Transactional(readOnly = true)
    Optional<Admin> findByLoginIdIgnoreCase(String loginId);
}
