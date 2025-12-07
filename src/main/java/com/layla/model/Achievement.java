package com.layla.model;

/**
 * Define la estructura de un Logro (Achievement).
 * Incluye la definición estática y el estado de desbloqueo del jugador.
 */
public class Achievement {
    public final String id;
    public final String name;
    public final String description; // Cómo se consigue
    public final String unlockContent; // Lo que desbloquea (para futuras implementaciones)
    public final String iconPath;

    // Estado dinámico del jugador
    private boolean isUnlocked;
    private long unlockDate; // Timestamp

    public Achievement(String id, String name, String description, String unlockContent, String iconPath) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.unlockContent = unlockContent;
        this.iconPath = iconPath;
        this.isUnlocked = false;
        this.unlockDate = 0;
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public void setUnlocked(boolean unlocked, long date) {
        isUnlocked = unlocked;
        unlockDate = date;
    }

    public long getUnlockDate() {
        return unlockDate;
    }
}
