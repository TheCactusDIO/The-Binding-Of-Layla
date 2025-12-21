package com.layla.core;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Ajustes de vídeo (resolución virtual) usados por la cámara/viewport.
 *
 * La "resolución virtual" define el tamaño del mundo lógico en píxeles sobre el que
 * se calcula el escalado. Esto te permite renderizar a distintas resoluciones reales
 * sin cambiar las coordenadas del juego.
 *
 * Es un singleton porque es un estado global compartido por el sistema de cámara/escena.
 */
public final class VideoSettings {

    private static final VideoSettings INSTANCE = new VideoSettings();

    /** Ancho virtual del mundo (resolución lógica). */
    private final IntegerProperty virtualWidth = new SimpleIntegerProperty(1920);

    /** Alto virtual del mundo (resolución lógica). */
    private final IntegerProperty virtualHeight = new SimpleIntegerProperty(1080);

    /** Constructor privado: singleton. */
    private VideoSettings() {}

    /**
     * Devuelve la instancia global de VideoSettings.
     */
    public static VideoSettings get() {
        return INSTANCE;
    }

    /**
     * Devuelve el ancho virtual actual.
     */
    public int getVirtualWidth() {
        return virtualWidth.get();
    }

    /**
     * Cambia el ancho virtual.
     * Se valida para evitar valores no válidos (<= 0).
     */
    public void setVirtualWidth(int width) {
        if (width <= 0) return;
        virtualWidth.set(width);
    }

    /**
     * Devuelve la property JavaFX del ancho virtual.
     * Útil para hacer bindings (por ejemplo, recalcular cámara al cambiar).
     */
    public IntegerProperty virtualWidthProperty() {
        return virtualWidth;
    }

    /**
     * Devuelve el alto virtual actual.
     */
    public int getVirtualHeight() {
        return virtualHeight.get();
    }

    /**
     * Cambia el alto virtual.
     * Se valida para evitar valores no válidos (<= 0).
     */
    public void setVirtualHeight(int height) {
        if (height <= 0) return;
        virtualHeight.set(height);
    }

    /**
     * Devuelve la property JavaFX del alto virtual.
     * Útil para hacer bindings (por ejemplo, recalcular cámara al cambiar).
     */
    public IntegerProperty virtualHeightProperty() {
        return virtualHeight;
    }

    /**
     * Cambia la resolución virtual completa (ancho y alto).
     * Llama internamente a los setters, así que hereda sus validaciones.
     */
    public void setResolution(int width, int height) {
        setVirtualWidth(width);
        setVirtualHeight(height);
    }
}
