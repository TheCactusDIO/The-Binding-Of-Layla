package com.layla.core;

import javafx.geometry.Rectangle2D;

/**
 * Maneja la lógica de animación de sprites basada en frames.
 * Calcula qué parte de la textura (Viewport) mostrar según el tiempo transcurrido.
 */
public class SpriteAnimator {

    private final int frameWidth;
    private final int frameHeight;
    private final int columns;
    private final double frameDuration; // Segundos por frame

    // Ya no son final para permitir cambios de animación
    private int totalFrames;

    private double timer = 0.0;
    private int currentFrame = 0;
    private boolean playing = true;
    private boolean loop = true;

    // Offset en la hoja de sprites
    private int startRow = 0;
    private int startCol = 0;

    public SpriteAnimator(int frameWidth, int frameHeight, int totalFrames, double fps, int columns) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.totalFrames = totalFrames;
        this.frameDuration = 1.0 / fps;
        this.columns = columns;
    }

    public void update(double dt) {
        if (!playing) return;

        timer += dt;
        if (timer >= frameDuration) {
            timer -= frameDuration;
            currentFrame++;

            if (currentFrame >= totalFrames) {
                if (loop) {
                    currentFrame = 0;
                } else {
                    currentFrame = totalFrames - 1;
                    playing = false;
                }
            }
        }
    }

    public Rectangle2D getCurrentViewport() {
        // Calcular posición en la rejilla global
        // Asumiendo que startCol y startRow definen el inicio de la tira
        // y que la tira continúa hacia la derecha y salta de línea si se acaba el ancho

        int absoluteFrameIndex = currentFrame + startCol + (startRow * columns);

        int col = absoluteFrameIndex % columns;
        int row = absoluteFrameIndex / columns;

        return new Rectangle2D(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
    }

    public void play() {
        playing = true;
    }

    public void stop() {
        playing = false;
        currentFrame = 0;
        timer = 0;
    }

    public void setAnimationConfig(int startRow, int startCol, int totalFrames, boolean loop) {
        // Solo reiniciamos si la configuración cambia para evitar saltos
        if (this.startRow != startRow || this.startCol != startCol || this.totalFrames != totalFrames) {
            this.startRow = startRow;
            this.startCol = startCol;
            this.totalFrames = Math.max(1, totalFrames);
            this.loop = loop;
            this.currentFrame = 0;
            this.timer = 0;
            this.playing = true;
        }
    }
}
