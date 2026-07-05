package com.planmate.community.domain.stats.repository;

import com.planmate.community.domain.stats.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {
}
