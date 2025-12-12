package com.layla.core;

import javafx.geometry.Rectangle2D;

/**
 * Maneja la lógica de animación de sprites basada en frames.
 */
public class SpriteAnimator {

    private final int frameWidth;
    private final int frameHeight;
    private final int columns;
    private final double frameDuration;

    // ERROR CORREGIDO: Ya no es final
    private int totalFrames;

    private double timer = 0.0;
    private int currentFrame = 0;
    private boolean playing = true;
    private boolean loop = true;

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
        // Calcular índice absoluto en la hoja
        int absoluteFrameIndex = (startRow * columns) + startCol + currentFrame;

        int col = absoluteFrameIndex % columns;
        int row = absoluteFrameIndex / columns;

        return new Rectangle2D(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
    }

    public void play() { playing = true; }
    public void stop() { playing = false; currentFrame = 0; timer = 0; }

    public void setAnimationConfig(int startRow, int startCol, int totalFrames, boolean loop) {
        // Evitar reseteos si la configuración es la misma
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
