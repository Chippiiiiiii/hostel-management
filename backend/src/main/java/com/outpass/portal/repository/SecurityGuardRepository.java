package com.outpass.portal.repository;

import com.outpass.portal.model.entity.SecurityGuard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityGuardRepository extends JpaRepository<SecurityGuard, Long> {
    Optional<SecurityGuard> findByEmail(String email);
    boolean existsByEmail(String email);
}

