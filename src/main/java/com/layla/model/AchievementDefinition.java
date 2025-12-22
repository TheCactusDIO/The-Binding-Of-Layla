package com.layla.model;

import java.util.Objects;

/**
 * Immutable metadata for an achievement (static definition).
 *
 * <p>This class represents what an achievement <i>is</i> (id, name, description, icon, reward text),
 * not whether a specific profile has unlocked it. Per-profile unlock state should live in persistence
 * (DB) or in a separate status model.</p>
 */
public final class AchievementDefinition {

    /**
     * Default icon used when no icon path is provided.
     */
    public static final String DEFAULT_ICON_PATH = "assets/images/achievements/default.png";

    /**
     * Default reward/extra text used when no unlock content is provided.
     */
    public static final String DEFAULT_UNLOCK_CONTENT = "No Content Unlock.";

    private final String id;
    private final String name;
    private final String description;
    private final String iconPath;
    private final String unlockContent;

    /**
     * Creates an immutable achievement definition.
     *
     * @param id unique achievement identifier (not null / not blank)
     * @param name display name (not null)
     * @param description how to unlock it (not null)
     * @param iconPath path to an icon resource (nullable). If null/blank, {@link #DEFAULT_ICON_PATH} is used.
     * @param unlockContent reward text / what it unlocks (nullable). If null/blank, {@link #DEFAULT_UNLOCK_CONTENT} is used.
     * @throws IllegalArgumentException if {@code id} is blank
     * @throws NullPointerException if {@code id}, {@code name}, or {@code description} are null
     */
    public AchievementDefinition(String id,
                                 String name,
                                 String description,
                                 String iconPath,
                                 String unlockContent) {
        this.id = requireNonBlank(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.iconPath = normalizeOrDefault(iconPath, DEFAULT_ICON_PATH);
        this.unlockContent = normalizeOrDefault(unlockContent, DEFAULT_UNLOCK_CONTENT);
    }

    /**
     * @return unique achievement identifier
     */
    public String getId() {
        return id;
    }

    /**
     * @return display name
     */
    public String getName() {
        return name;
    }

    /**
     * @return how to unlock the achievement
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return icon resource path
     */
    public String getIconPath() {
        return iconPath;
    }

    /**
     * @return reward text / what the achievement unlocks
     */
    public String getUnlockContent() {
        return unlockContent;
    }

    @Override
    public String toString() {
        return "AchievementDefinition{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", iconPath='" + iconPath + '\'' +
            '}';
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        if (value == null) return defaultValue;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
