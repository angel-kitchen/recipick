package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
}
