package com.layla.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.layla.model.StatType;

public class ProjectileLifetimeTest {

    @Test
        void projectileRangeIsStoredInSeconds() {
        StatsService stats = new StatsService();
        stats.getBaseStats().setBase(StatType.PROJECTILE_RANGE, 1.5);
        assertEquals(1.5, stats.getProjectileRange(), 1e-6);
    }
}
