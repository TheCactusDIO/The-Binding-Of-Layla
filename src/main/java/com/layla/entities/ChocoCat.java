package com.layla.entities;

import java.util.Objects;
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
import javafx.scene.paint.Color;

/**
 * NPC decorativo (gato) que deambula, se detiene o duerme.
 * <p>
 * Características:
 * <ul>
 * <li>Renderizado mediante {@link Canvas} para dibujar frames de un spritesheet.</li>
 * <li>Entidad "fantasma": {@link #getBounds()} devuelve tamaño 0 para no tener colisiones físicas.</li>
 * <li>Se mueve dentro de un área definida (Pane) rebotando o cambiando de estado al llegar al borde.</li>
 * </ul>
 * </p>
 */
public class ChocoCat implements GameEntity {

    private enum State { IDLE, WALKING, SLEEPING }
    private enum Direction { UP, DOWN, LEFT, RIGHT }

    private static final ThreadLocalRandom RNG = ThreadLocalRandom.current();

    // Probabilidades de transición de estado
    private static final double WALK_CHANCE = 0.40;
    private static final double IDLE_CHANCE = 0.30; // El resto es SLEEPING

    // Dimensiones por defecto si el área de juego no está inicializada
    private static final double DEFAULT_AREA_WIDTH = 1280.0;
    private static final double DEFAULT_AREA_HEIGHT = 720.0;

    // Margen de seguridad para no pegarse a los bordes
    private static final double BOUNDS_MARGIN = 20.0;

    // Configuración del SpriteSheet
    private static final int FRAME_WIDTH = 169;
    private static final int FRAME_HEIGHT = 123;
    private static final double RENDER_SCALE = 0.35;

    // Velocidades y tiempos de animación
    private static final double MOVE_SPEED = 40.0;
    private static final double ANIM_SPEED_SLEEPING = 1.0; // Lento (respirar)
    private static final double ANIM_SPEED_DEFAULT = 0.2;  // Normal (caminar)

    private final Pane gameArea;
    private final Canvas view;
    private final GraphicsContext gc;

    private Image spriteSheet;

    private State currentState = State.IDLE;
    private Direction currentDirection = Direction.DOWN;

    private double stateTimerSeconds = 0.0;
    private double velX = 0.0;
    private double velY = 0.0;

    private double animTimeSeconds = 0.0;

    /**
     * Crea un ChocoCat y carga sus recursos gráficos.
     *
     * @param startX   Posición X inicial.
     * @param startY   Posición Y inicial.
     * @param gameArea Panel que delimita su movimiento.
     * @throws NullPointerException si {@code gameArea} es null.
     */
    public ChocoCat(double startX, double startY, Pane gameArea) {
        this.gameArea = Objects.requireNonNull(gameArea, "gameArea");

        this.view = new Canvas(FRAME_WIDTH * RENDER_SCALE, FRAME_HEIGHT * RENDER_SCALE);
        this.view.setLayoutX(startX);
        this.view.setLayoutY(startY);

        this.gc = view.getGraphicsContext2D();

        this.spriteSheet = loadSpriteSheet();
        if (this.spriteSheet == null) {
            System.err.println("[ChocoCat] ERROR: No se pudo cargar choco_sheet.png (fallback visual activado).");
        }

        pickNextState();
        drawCurrentFrame(); // Dibuja el estado inicial
    }

    /**
     * Actualiza el comportamiento del NPC.
     * <ul>
     * <li>Gestiona temporizadores de estado (caminar, dormir, idle).</li>
     * <li>Mueve la entidad si está caminando.</li>
     * <li>Actualiza el frame de animación.</li>
     * </ul>
     *
     * @param dt Delta time en segundos.
     */
    @Override
    public void update(double dt) {
        if (dt <= 0) {
            return;
        }

        stateTimerSeconds -= dt;
        if (stateTimerSeconds <= 0.0) {
            pickNextState();
        }

        if (currentState == State.WALKING) {
            updateWalking(dt);
        }

        updateAnimation(dt);
    }

    /**
     * Devuelve la vista del gato (Canvas).
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Devuelve límites vacíos (0x0) para evitar colisiones físicas con el jugador o proyectiles.
     * Se usa la posición del canvas para la referencia.
     */
    @Override
    public Bounds getBounds() {
        return new BoundingBox(view.getLayoutX(), view.getLayoutY(), 0, 0);
    }

    /**
     * Mueve al gato y verifica colisiones con los bordes del área de juego.
     * Si toca un borde, cambia de estado inmediatamente.
     *
     * @param dt Delta time.
     */
    private void updateWalking(double dt) {
        double nextX = view.getLayoutX() + velX * dt;
        double nextY = view.getLayoutY() + velY * dt;

        double areaW = (gameArea.getWidth() > 0) ? gameArea.getWidth() : DEFAULT_AREA_WIDTH;
        double areaH = (gameArea.getHeight() > 0) ? gameArea.getHeight() : DEFAULT_AREA_HEIGHT;

        double rightLimit = areaW - BOUNDS_MARGIN;
        double bottomLimit = areaH - BOUNDS_MARGIN;

        if (nextX < BOUNDS_MARGIN || nextX > rightLimit || nextY < BOUNDS_MARGIN || nextY > bottomLimit) {
            // Si choca con un borde, elige un nuevo estado (ej. girar o pararse)
            pickNextState();
            return;
        }

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);
    }

    /**
     * Selecciona aleatoriamente el siguiente estado basado en probabilidades predefinidas.
     */
    private void pickNextState() {
        double roll = RNG.nextDouble();

        if (roll < WALK_CHANCE) {
            startWalking();
        } else if (roll < WALK_CHANCE + IDLE_CHANCE) {
            startIdle();
        } else {
            startSleeping();
        }
    }

    /**
     * Inicia el estado WALKING: elige una dirección cardinal aleatoria y velocidad.
     */
    private void startWalking() {
        currentState = State.WALKING;
        stateTimerSeconds = RNG.nextDouble(2.0, 5.0);

        int dir = RNG.nextInt(4);
        switch (dir) {
            case 0 -> { currentDirection = Direction.UP;    velX = 0.0;        velY = -MOVE_SPEED; }
            case 1 -> { currentDirection = Direction.DOWN;  velX = 0.0;        velY =  MOVE_SPEED; }
            case 2 -> { currentDirection = Direction.LEFT;  velX = -MOVE_SPEED; velY = 0.0;        }
            default -> { currentDirection = Direction.RIGHT; velX =  MOVE_SPEED; velY = 0.0;        }
        }
    }

    /**
     * Inicia el estado IDLE: se queda quieto un tiempo breve.
     */
    private void startIdle() {
        currentState = State.IDLE;
        stateTimerSeconds = RNG.nextDouble(1.5, 3.5);
        velX = 0.0;
        velY = 0.0;
    }

    /**
     * Inicia el estado SLEEPING: se queda quieto un tiempo largo (dormido).
     */
    private void startSleeping() {
        currentState = State.SLEEPING;
        stateTimerSeconds = RNG.nextDouble(5.0, 10.0);
        velX = 0.0;
        velY = 0.0;
    }

    /**
     * Avanza el tiempo de animación y solicita el redibujado del frame.
     *
     * @param dt Delta time.
     */
    private void updateAnimation(double dt) {
        animTimeSeconds += dt;
        drawCurrentFrame();
    }

    /**
     * Determina qué fila y columna del spritesheet dibujar según estado y dirección.
     * <p>Mapeo:</p>
     * <ul>
     * <li>Fila 0: UP (cols 0-1), RIGHT (cols 2-3)</li>
     * <li>Fila 1: DOWN (cols 0-1), LEFT (cols 2-3)</li>
     * <li>Fila 2: SLEEP (cols 0-1)</li>
     * </ul>
     */
    private void drawCurrentFrame() {
        int row;
        int col;

        double speedFactor = (currentState == State.SLEEPING) ? ANIM_SPEED_SLEEPING : ANIM_SPEED_DEFAULT;
        // Alterna entre 0 y 1 para la animación básica de 2 frames
        int frameStep = ((int) (animTimeSeconds / speedFactor)) % 2;

        if (currentState == State.SLEEPING) {
            row = 2;
            col = frameStep;
        } else {
            if (currentState == State.IDLE) {
                frameStep = 0; // Si está quieto, usa siempre el primer frame
            }
            switch (currentDirection) {
                case UP    -> { row = 0; col = 0 + frameStep; }
                case RIGHT -> { row = 0; col = 2 + frameStep; }
                case DOWN  -> { row = 1; col = 0 + frameStep; }
                case LEFT  -> { row = 1; col = 2 + frameStep; }
                default    -> { row = 1; col = 0; }
            }
        }

        drawFrame(row, col);
    }

    /**
     * Dibuja una región del spritesheet en el Canvas.
     *
     * @param row Fila del sprite (base 0).
     * @param col Columna del sprite (base 0).
     */
    private void drawFrame(int row, int col) {
        gc.clearRect(0, 0, view.getWidth(), view.getHeight());

        if (spriteSheet == null) {
            // Fallback: círculo naranja si no hay imagen
            gc.setFill(Color.ORANGE);
            gc.fillOval(0, 0, 30, 30);
            return;
        }

        double sx = (double) col * FRAME_WIDTH;
        double sy = (double) row * FRAME_HEIGHT;

        gc.drawImage(
                spriteSheet,
                sx, sy, FRAME_WIDTH, FRAME_HEIGHT,
                0, 0, FRAME_WIDTH * RENDER_SCALE, FRAME_HEIGHT * RENDER_SCALE
        );
    }

    /**
     * Intenta cargar el spritesheet desde rutas comunes.
     *
     * @return La imagen cargada o null si falla.
     */
    private Image loadSpriteSheet() {
        Image img = AssetsManager.loadImage("/assets/images/choco_sheet.png");
        if (img != null) return img;

        img = AssetsManager.loadImage("assets/images/choco_sheet.png");
        return img;
    }
}
