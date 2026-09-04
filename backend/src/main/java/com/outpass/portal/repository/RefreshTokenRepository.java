package com.outpass.portal.repository;

import com.outpass.portal.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    // A single bulk JPQL DELETE (not a find-then-remove(entity) pair) so it compiles to one
    // SQL "DELETE ... WHERE token = ?" statement executed atomically by the database: the row
    // lock it takes means at most one concurrent caller presenting the same token string can
    // ever see returnedRows == 1, closing the refresh-token-rotation TOCTOU window that
    // findByToken()+delete(entity) had (see RefreshTokenService#consumeToken).
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")
    int deleteByTokenAtomic(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userId = :userId AND rt.userType = :userType")
    void deleteByUserIdAndUserType(Long userId, String userType);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteExpiredTokens(Instant now);
}

