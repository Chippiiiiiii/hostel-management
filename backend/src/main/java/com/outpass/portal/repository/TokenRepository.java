package com.outpass.portal.repository;

import com.outpass.portal.model.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {
    boolean existsByJid(String jid);

    @Modifying
    @Query("DELETE FROM Token t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(Instant now);
}

