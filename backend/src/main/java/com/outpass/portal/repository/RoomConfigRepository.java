package com.outpass.portal.repository;

import com.outpass.portal.model.entity.RoomConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomConfigRepository extends JpaRepository<RoomConfig, Long> {
    Optional<RoomConfig> findByConfigKey(String configKey);
}
