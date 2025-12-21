package com.layla.core;

import javafx.geometry.Rectangle2D;

/**
 * Controla una animación de sprites basada en frames dentro de una sprite sheet.
 *
 * Idea:
 * - Tú le dices dónde empieza la animación (fila/columna) y cuántos frames dura.
 * - Cada update(dt) avanza el frame según el FPS configurado.
 * - getCurrentViewport() devuelve el Rectangle2D para aplicar a ImageView.setViewport(...).
 *
 * Notas:
 * - No dibuja nada; solo calcula el viewport.
 * - Es genérico: sirve para cualquier sprite sheet en rejilla.
 */
public final class SpriteAnimator {

    // =========================
    // CONFIGURACIÓN DE LA SHEET
    // =========================

    /** Ancho de un frame en píxeles dentro de la sprite sheet. */
    private final int frameWidth;

    /** Alto de un frame en píxeles dentro de la sprite sheet. */
    private final int frameHeight;

    /** Número de columnas que tiene la sprite sheet. */
    private final int columns;

    /**
     * Duración de cada frame en segundos.
     * Se calcula como 1 / fps.
     */
    private final double frameDuration;

    // =========================
    // ESTADO DE LA ANIMACIÓN
    // =========================

    /** Número total de frames de la animación actual. Mínimo 1. */
    private int totalFrames;

    /** Tiempo acumulado desde el último cambio de frame (segundos). */
    private double timer = 0.0;

    /** Frame actual dentro de [0..totalFrames-1]. */
    private int currentFrame = 0;

    /** Si está reproduciendo o en pausa. */
    private boolean playing = true;

    /** Si al llegar al final reinicia (loop) o se queda en el último frame. */
    private boolean loop = true;

    /** Fila inicial dentro de la sheet para esta animación. */
    private int startRow = 0;

    /** Columna inicial dentro de la sheet para esta animación. */
    private int startCol = 0;

    /**
     * Crea un animador para una sprite sheet de rejilla.
     *
     * @param frameWidth  ancho del frame en píxeles
     * @param frameHeight alto del frame en píxeles
     * @param totalFrames frames de la animación actual (mínimo 1)
     * @param fps         frames por segundo (debe ser > 0)
     * @param columns     columnas totales de la sheet (debe ser > 0)
     */
    public SpriteAnimator(int frameWidth, int frameHeight, int totalFrames, double fps, int columns) {
        if (fps <= 0) throw new IllegalArgumentException("fps debe ser > 0");
        if (columns <= 0) throw new IllegalArgumentException("columns debe ser > 0");
        if (frameWidth <= 0 || frameHeight <= 0) throw new IllegalArgumentException("frame size debe ser > 0");

        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.totalFrames = Math.max(1, totalFrames);
        this.frameDuration = 1.0 / fps;
        this.columns = columns;
    }

    /**
     * Avanza la animación en función del delta time.
     *
     * Qué hace:
     * - Acumula dt y, cuando supera frameDuration, avanza frames.
     * - Si loop=true, vuelve a 0 al llegar al final.
     * - Si loop=false, se queda en el último frame y detiene la reproducción.
     *
     * Detalle:
     * - Se usa un while con límite para evitar “saltos locos” si dt es muy grande.
     */
    public void update(double dt) {
        if (!playing) return;
        if (dt <= 0) return;

        timer += dt;

        // Evitar bucles enormes si dt viene muy grande por lag (límite de seguridad).
        int guard = 0;
        while (timer >= frameDuration && guard < 10) {
            timer -= frameDuration;
            currentFrame++;

            if (currentFrame >= totalFrames) {
                if (loop) {
                    currentFrame = 0;
                } else {
                    currentFrame = totalFrames - 1;
                    playing = false;
                    timer = 0.0;
                    break;
                }
            }
            guard++;
        }
    }

    /**
     * Devuelve el viewport (Rectangle2D) correspondiente al frame actual.
     *
     * Qué hace:
     * - Calcula el índice absoluto dentro de la sheet partiendo de (startRow,startCol).
     * - Lo convierte a (fila,columna) según el número total de columnas.
     *
     * @return Rectangle2D con x,y,width,height para ImageView.setViewport(...)
     */
    public Rectangle2D getCurrentViewport() {
        int absoluteFrameIndex = (startRow * columns) + startCol + currentFrame;

        int col = absoluteFrameIndex % columns;
        int row = absoluteFrameIndex / columns;

        return new Rectangle2D(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
    }

    /**
     * Reanuda la animación desde el frame actual.
     * No resetea el contador de frame.
     */
    public void play() {
        playing = true;
    }

    /**
     * Detiene la animación y la resetea al frame 0.
     * Útil si quieres reiniciar una animación desde el principio.
     */
    public void stop() {
        playing = false;
        currentFrame = 0;
        timer = 0.0;
    }

    /**
     * Configura una animación dentro de la sheet.
     *
     * Qué hace:
     * - Define desde qué fila/col empieza la animación.
     * - Cambia el número de frames que dura.
     * - Decide si loop está activo.
     * - Si la configuración cambia, resetea a frame 0 y arranca la reproducción.
     *
     * Nota:
     * - Si llamas con la misma configuración, no resetea (evita flicker).
     */
    public void setAnimationConfig(int startRow, int startCol, int totalFrames, boolean loop) {
        int safeFrames = Math.max(1, totalFrames);

        boolean changed =
                this.startRow != startRow ||
                this.startCol != startCol ||
                this.totalFrames != safeFrames ||
                this.loop != loop;

        if (!changed) return;

        this.startRow = startRow;
        this.startCol = startCol;
        this.totalFrames = safeFrames;
        this.loop = loop;

        this.currentFrame = 0;
        this.timer = 0.0;
        this.playing = true;
    }

    // =========================
    // GETTERS ÚTILES (OPCIONALES)
    // =========================

    public boolean isPlaying() { return playing; }
    public int getCurrentFrame() { return currentFrame; }
    public int getTotalFrames() { return totalFrames; }
}
