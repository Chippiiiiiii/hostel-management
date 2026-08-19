package com.outpass.portal.repository;

import com.outpass.portal.model.entity.Warden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WardenRepository extends JpaRepository<Warden, Long> {
    Optional<Warden> findByEmail(String email);
    boolean existsByEmail(String email);
}

