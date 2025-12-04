package com.layla.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.layla.items.ItemId;
import com.layla.model.PlayerStatId;
import com.layla.model.StatModifier;

public class StatsServiceTest {

    @Test
    void runtimeModifiersAndItemsStack() {
        StatsService stats = new StatsService();
        stats.setBaseStat(PlayerStatId.MOVE_SPEED, 300.0);

        stats.addModifier(StatModifier.additive(PlayerStatId.MOVE_SPEED, 50.0));
        stats.addModifier(StatModifier.multiplicative(PlayerStatId.MOVE_SPEED, 1.10));

        assertTrue(stats.grantItem(ItemId.SWIFT_BOOTS));

        double value = stats.getMoveSpeed();
        double expected = (300.0 + 50.0 + 40.0) * 1.10 * 1.15;
        assertEquals(expected, value, 1e-6);
    }
}
