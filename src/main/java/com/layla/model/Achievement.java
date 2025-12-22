package com.layla.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de un logro (Achievement).
 *
 * <p>Esta clase contiene:</p>
 * <ul>
 *   <li><b>Definición estática</b>: id, nombre, descripción, contenido que desbloquea, icono.</li>
 *   <li><b>Estado dinámico</b>: si está desbloqueado y cuándo se desbloqueó.</li>
 * </ul>
 *
 * <p><b>Nota sobre fechas</b>: {@code unlockDate} se guarda como timestamp en milisegundos desde epoch
 * (equivalente a {@link System#currentTimeMillis()}). Si no está desbloqueado, su valor es {@code 0}.</p>
 */
public class Achievement {

    /**
     * Identificador único del logro (p. ej. {@code "ACH_TRY_AGAIN"}).
     */
    public final String id;

    /**
     * Nombre mostrado al usuario.
     */
    public final String name;

    /**
     * Descripción de cómo se consigue el logro.
     */
    public final String description;

    /**
     * Texto informativo sobre lo que desbloquea el logro (items/modos/personajes, etc.).
     */
    public final String unlockContent;

    /**
     * Ruta del icono del logro (classpath / assets).
     */
    public final String iconPath;

    // Estado dinámico del jugador
    private boolean unlocked;
    private long unlockDate; // epoch millis, 0 si no está desbloqueado

    /**
     * Crea un logro en estado "bloqueado".
     *
     * @param id identificador único del logro (no null / no blank)
     * @param name nombre visible (no null)
     * @param description descripción (no null)
     * @param unlockContent texto de recompensa (puede ser null si no se usa)
     * @param iconPath ruta del icono (puede ser null si no se usa)
     * @throws IllegalArgumentException si {@code id} está vacío o en blanco
     * @throws NullPointerException si {@code id}, {@code name} o {@code description} son null
     */
    public Achievement(String id, String name, String description, String unlockContent, String iconPath) {
        this.id = requireNonBlank(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.unlockContent = unlockContent;
        this.iconPath = iconPath;

        this.unlocked = false;
        this.unlockDate = 0L;
    }

    /**
     * Indica si el logro está desbloqueado.
     *
     * @return {@code true} si está desbloqueado; {@code false} si no
     */
    public boolean isUnlocked() {
        return unlocked;
    }

    /**
     * Devuelve el timestamp (epoch millis) de desbloqueo.
     *
     * <p>Si el logro no está desbloqueado, devuelve {@code 0}.</p>
     *
     * @return epoch millis del desbloqueo, o {@code 0} si no está desbloqueado
     */
    public long getUnlockDate() {
        return unlockDate;
    }

    /**
     * Devuelve la fecha de desbloqueo como {@link Instant}.
     *
     * @return {@link Instant} del desbloqueo, o {@code null} si no está desbloqueado
     */
    public Instant getUnlockInstant() {
        return (unlockDate > 0) ? Instant.ofEpochMilli(unlockDate) : null;
    }

    /**
     * Actualiza el estado del logro.
     *
     * <p>Reglas:</p>
     * <ul>
     *   <li>Si {@code unlocked} es {@code false}, el timestamp se resetea a {@code 0}.</li>
     *   <li>Si {@code unlocked} es {@code true} y {@code dateMillis <= 0}, se usa {@link System#currentTimeMillis()}.</li>
     * </ul>
     *
     * @param unlocked nuevo estado
     * @param dateMillis timestamp (epoch millis) del desbloqueo
     */
    public void setUnlocked(boolean unlocked, long dateMillis) {
        this.unlocked = unlocked;

        if (!unlocked) {
            this.unlockDate = 0L;
            return;
        }

        this.unlockDate = (dateMillis > 0) ? dateMillis : System.currentTimeMillis();
    }

    /**
     * Atajo para desbloquear el logro usando la hora actual.
     */
    public void unlockNow() {
        setUnlocked(true, System.currentTimeMillis());
    }

    /**
     * Atajo para volver a bloquear el logro (y limpiar fecha).
     */
    public void lock() {
        setUnlocked(false, 0L);
    }

    @Override
    public String toString() {
        return "Achievement{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", unlocked=" + unlocked +
            ", unlockDate=" + unlockDate +
            '}';
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
