package com.layla.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.layla.model.StatType;

public class ProjectileLifetimeTest {

  @Test
  void lifetimeIsRangeOverSpeed() {
    StatsService stats = new StatsService();
    stats.getBaseStats().setBase(StatType.PROJECTILE_SPEED, 500.0);
    stats.getBaseStats().setBase(StatType.RANGE_PIXELS, 600.0);
    double speed = stats.getProjectileSpeed();
    double range = stats.getRangePixels();
    double lifetime = range / speed; // fórmula usada por ShootingService
    assertEquals(1.2, lifetime, 1e-6);
  }
}
