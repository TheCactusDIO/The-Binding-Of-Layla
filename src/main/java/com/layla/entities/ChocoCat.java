package com.layla.entities;

import java.util.concurrent.ThreadLocalRandom;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;

public class ChocoCat implements GameEntity {

    private enum State {
        IDLE, WALKING, SLEEPING
    }

    private enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private final Pane gameArea;
    private final Canvas view;
    private Image spriteSheet;

    private State currentState = State.IDLE;
    private Direction currentDirection = Direction.DOWN;

    private double stateTimer = 0.0;
    private double moveSpeed = 40.0;
    private double velX = 0;
    private double velY = 0;

    // Dimensiones del sprite según tu descripción
    private static final int FRAME_WIDTH = 169;
    private static final int FRAME_HEIGHT = 123;
    private static final double RENDER_SCALE = 0.35; // Escala reducida para que no sea gigante

    private double animTime = 0;

    public ChocoCat(double startX, double startY, Pane gameArea) {
        this.gameArea = gameArea;

        this.view = new Canvas(FRAME_WIDTH * RENDER_SCALE, FRAME_HEIGHT * RENDER_SCALE);
        this.view.setLayoutX(startX);
        this.view.setLayoutY(startY);

        // --- INTENTO DE CARGA ROBUSTA ---
        // Probamos rutas comunes para asegurar que la encuentre
        this.spriteSheet = AssetsManager.loadImage("/assets/images/choco_sheet.png");
        if (this.spriteSheet == null) {
            this.spriteSheet = AssetsManager.loadImage("assets/images/choco_sheet.png");
        }

        // Si sigue siendo null, imprimir error en consola para depurar
        if (this.spriteSheet == null) {
            System.err.println("!!! ERROR CRÍTICO: No se pudo cargar choco_sheet.png en ninguna ruta conocida.");
        }

        pickNextState();
    }

    @Override
    public void update(double dt) {
        stateTimer -= dt;

        if (stateTimer <= 0) {
            pickNextState();
        }

        if (currentState == State.WALKING) {
            double nextX = view.getLayoutX() + velX * dt;
            double nextY = view.getLayoutY() + velY * dt;

            // Límites simples (evitar salirse del gameArea)
            double margin = 20;
            double rightLimit = (gameArea.getWidth() > 0 ? gameArea.getWidth() : 1280) - margin;
            double bottomLimit = (gameArea.getHeight() > 0 ? gameArea.getHeight() : 720) - margin;

            if (nextX < margin || nextX > rightLimit || nextY < margin || nextY > bottomLimit) {
                pickNextState(); // Cambiar dirección si choca
            } else {
                view.setLayoutX(nextX);
                view.setLayoutY(nextY);
            }
        }

        updateAnimation(dt);
    }

    private void pickNextState() {
        double roll = ThreadLocalRandom.current().nextDouble();

        // 40% Caminar, 30% Quieto, 30% Dormir
        if (roll < 0.4) {
            startWalking();
        } else if (roll < 0.7) {
            startIdle();
        } else {
            startSleeping();
        }
    }

    private void startWalking() {
        currentState = State.WALKING;
        stateTimer = ThreadLocalRandom.current().nextDouble(2.0, 5.0);

        int dir = ThreadLocalRandom.current().nextInt(4);
        switch (dir) {
            case 0 -> { currentDirection = Direction.UP;    velX = 0; velY = -moveSpeed; }
            case 1 -> { currentDirection = Direction.DOWN;  velX = 0; velY = moveSpeed; }
            case 2 -> { currentDirection = Direction.LEFT;  velX = -moveSpeed; velY = 0; }
            case 3 -> { currentDirection = Direction.RIGHT; velX = moveSpeed; velY = 0; }
        }
    }

    private void startIdle() {
        currentState = State.IDLE;
        stateTimer = ThreadLocalRandom.current().nextDouble(1.5, 3.5);
        velX = 0;
        velY = 0;
    }

    private void startSleeping() {
        currentState = State.SLEEPING;
        stateTimer = ThreadLocalRandom.current().nextDouble(5.0, 10.0);
        velX = 0;
        velY = 0;
    }

    private void updateAnimation(double dt) {
        animTime += dt;

        int row = 0;
        int col = 0;

        // LÓGICA DE SPRITES PERSONALIZADA
        // Fila 0 (índice 0): Arriba (cols 0,1) | Derecha (cols 2,3)
        // Fila 1 (índice 1): Abajo (cols 0,1) | Izquierda (cols 2,3)
        // Fila 2 (índice 2): Dormir (cols 0,1)

        double speedFactor = (currentState == State.SLEEPING) ? 1.0 : 0.2; // Velocidad de la animación
        int frameStep = (int)(animTime / speedFactor) % 2; // Siempre alterna entre 0 y 1

        if (currentState == State.SLEEPING) {
            row = 2; // Fila 3
            col = frameStep; // Alterna col 0 y 1 (Dormir 1 y 2)
        } else {
            // IDLE o WALKING
            // Si está IDLE, forzamos el primer frame de la pareja (el "quieto")
            if (currentState == State.IDLE) {
                frameStep = 0;
            }

            switch (currentDirection) {
                case UP:
                    row = 0;
                    col = 0 + frameStep; // Cols 0 y 1
                    break;
                case RIGHT:
                    row = 0;
                    col = 2 + frameStep; // Cols 2 y 3
                    break;
                case DOWN:
                    row = 1;
                    col = 0 + frameStep; // Cols 0 y 1
                    break;
                case LEFT:
                    row = 1;
                    col = 2 + frameStep; // Cols 2 y 3
                    break;
            }
        }

        drawFrame(row, col);
    }

    private void drawFrame(int row, int col) {
        GraphicsContext gc = view.getGraphicsContext2D();
        gc.clearRect(0, 0, view.getWidth(), view.getHeight());

        if (spriteSheet != null) {
            double sx = col * FRAME_WIDTH;
            double sy = row * FRAME_HEIGHT;

            gc.drawImage(spriteSheet, sx, sy, FRAME_WIDTH, FRAME_HEIGHT,
                         0, 0, FRAME_WIDTH * RENDER_SCALE, FRAME_HEIGHT * RENDER_SCALE);
        } else {
            // Fallback visual (círculo naranja) si falla la carga
            gc.setFill(javafx.scene.paint.Color.ORANGE);
            gc.fillOval(0, 0, 30, 30);
        }
    }

    // --- MÉTODOS DE INTERFAZ GameEntity ---

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public Bounds getBounds() {
        // Bounds vacíos para ser un fantasma (sin colisiones)
        return new BoundingBox(view.getLayoutX(), view.getLayoutY(), 0, 0);
    }
}
