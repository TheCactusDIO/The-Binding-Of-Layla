package com.layla.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.layla.model.StatModifier;
import com.layla.model.StatType;

public class StatsServiceTest {

  @Test
  void additiveAndMultiplicativeStacking() {
    StatsService stats = new StatsService();
    // base moveSpeed = 300
    stats.getBaseStats().setBase(StatType.MOVE_SPEED, 300.0);
    // +50 y ×1.10 ×1.20  => (300+50)*1.32 = 462.0
    StatModifier add = StatModifier.additive(StatType.MOVE_SPEED, 50.0);
    StatModifier m1  = StatModifier.multiplier(StatType.MOVE_SPEED, 1.10);
    StatModifier m2  = StatModifier.multiplier(StatType.MOVE_SPEED, 1.20);
    stats.addModifier(add); stats.addModifier(m1); stats.addModifier(m2);
    assertEquals(462.0, stats.getMoveSpeed(), 1e-6);
  }
}
