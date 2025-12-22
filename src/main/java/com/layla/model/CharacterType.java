package com.layla.model;

/**
 * Playable character variants.
 *
 * <p>Each character type may imply different starting stats, perks, or run modifiers
 * applied by the game logic (e.g., {@code THE_FRAGILE} starting with lower max HP).</p>
 */
public enum CharacterType {

    /**
     * Default character.
     */
    LAYLA,

    /**
     * Fragile character variant (typically lower max HP, potentially different bonuses).
     */
    THE_FRAGILE
}
