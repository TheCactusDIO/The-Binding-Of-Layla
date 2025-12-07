package com.layla.model;

import java.util.Objects;

public final class AchievementDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final String iconPath;
    private final String unlockContent; // Lo que este logro desbloquea

    public AchievementDefinition(String id, String name, String description, String iconPath, String unlockContent) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        // Implementación de placeholder para iconos
        this.iconPath = iconPath != null ? iconPath : "assets/images/achievements/default.png";
        this.unlockContent = unlockContent != null ? unlockContent : "No Content Unlock.";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIconPath() { return iconPath; }
    public String getUnlockContent() { return unlockContent; }
}
