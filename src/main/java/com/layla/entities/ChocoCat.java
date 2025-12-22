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
 * NPC decorativo (sin colisiones) que pasea, se queda quieto o duerme,
 * mostrando animación desde un spritesheet.
 *
 * <p>Notas de diseño:</p>
 * <ul>
 *   <li>Se renderiza en un {@link Canvas} para dibujar frames del spritesheet.</li>
 *   <li>No interactúa con colisiones: {@link #getBounds()} devuelve un bounds de tamaño 0.</li>
 *   <li>El movimiento se limita a un área (Pane) usando un margen simple.</li>
 * </ul>
 */
public class ChocoCat implements GameEntity {

    private enum State { IDLE, WALKING, SLEEPING }
    private enum Direction { UP, DOWN, LEFT, RIGHT }

    private static final ThreadLocalRandom RNG = ThreadLocalRandom.current();

    // Probabilidades de estado
    private static final double WALK_CHANCE = 0.40;
    private static final double IDLE_CHANCE = 0.30; // el resto es dormir

    // Límites por defecto si el Pane aún no tiene tamaño (no “layouted”)
    private static final double DEFAULT_AREA_WIDTH = 1280.0;
    private static final double DEFAULT_AREA_HEIGHT = 720.0;

    // Margen simple para evitar que se vaya a los bordes
    private static final double BOUNDS_MARGIN = 20.0;

    // Sprite config
    private static final int FRAME_WIDTH = 169;
    private static final int FRAME_HEIGHT = 123;
    private static final double RENDER_SCALE = 0.35;

    // Movimiento/animación
    private static final double MOVE_SPEED = 40.0;
    private static final double ANIM_SPEED_SLEEPING = 1.0; // cambia frame cada ~1s
    private static final double ANIM_SPEED_DEFAULT = 0.2;  // cambia frame cada ~0.2s

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
     * Crea el ChocoCat en la posición inicial y carga el spritesheet.
     *
     * @param startX   X inicial (layoutX)
     * @param startY   Y inicial (layoutY)
     * @param gameArea pane donde se moverá y se dibujará
     * @throws NullPointerException si {@code gameArea} es null
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
        drawCurrentFrame(); // pinta algo desde el frame 0
    }

    /**
     * Tick del NPC:
     * <ul>
     *   <li>Cambia de estado cuando el timer expira</li>
     *   <li>Si camina, se mueve y evita salirse del área</li>
     *   <li>Actualiza la animación y dibuja el frame</li>
     * </ul>
     *
     * @param dt delta time en segundos
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
     * Devuelve el nodo visual del NPC (Canvas).
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Devuelve bounds de tamaño 0 para que sea “fantasma” (sin colisiones).
     * Se posiciona en el layout del Canvas por consistencia.
     */
    @Override
    public Bounds getBounds() {
        return new BoundingBox(view.getLayoutX(), view.getLayoutY(), 0, 0);
    }

    /**
     * Actualiza el movimiento cuando el estado es WALKING, respetando límites simples.
     */
    private void updateWalking(double dt) {
        double nextX = view.getLayoutX() + velX * dt;
        double nextY = view.getLayoutY() + velY * dt;

        double areaW = (gameArea.getWidth() > 0) ? gameArea.getWidth() : DEFAULT_AREA_WIDTH;
        double areaH = (gameArea.getHeight() > 0) ? gameArea.getHeight() : DEFAULT_AREA_HEIGHT;

        double rightLimit = areaW - BOUNDS_MARGIN;
        double bottomLimit = areaH - BOUNDS_MARGIN;

        if (nextX < BOUNDS_MARGIN || nextX > rightLimit || nextY < BOUNDS_MARGIN || nextY > bottomLimit) {
            // Mantengo tu comportamiento: al “chocar”, cambia a un estado aleatorio (no solo girar).
            pickNextState();
            return;
        }

        view.setLayoutX(nextX);
        view.setLayoutY(nextY);
    }

    /**
     * Elige el siguiente estado con probabilidades:
     * <ul>
     *   <li>40% caminar</li>
     *   <li>30% idle</li>
     *   <li>30% dormir</li>
     * </ul>
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
     * Entra en estado WALKING con dirección aleatoria y duración aleatoria.
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
     * Entra en estado IDLE con duración aleatoria.
     */
    private void startIdle() {
        currentState = State.IDLE;
        stateTimerSeconds = RNG.nextDouble(1.5, 3.5);
        velX = 0.0;
        velY = 0.0;
    }

    /**
     * Entra en estado SLEEPING con duración aleatoria.
     */
    private void startSleeping() {
        currentState = State.SLEEPING;
        stateTimerSeconds = RNG.nextDouble(5.0, 10.0);
        velX = 0.0;
        velY = 0.0;
    }

    /**
     * Avanza el tiempo de animación y dibuja el frame correcto según:
     * <ul>
     *   <li>Fila 0: UP (0-1) y RIGHT (2-3)</li>
     *   <li>Fila 1: DOWN (0-1) y LEFT (2-3)</li>
     *   <li>Fila 2: SLEEP (0-1)</li>
     * </ul>
     */
    private void updateAnimation(double dt) {
        animTimeSeconds += dt;
        drawCurrentFrame();
    }

    /**
     * Calcula row/col actuales y dibuja el frame.
     */
    private void drawCurrentFrame() {
        int row;
        int col;

        double speedFactor = (currentState == State.SLEEPING) ? ANIM_SPEED_SLEEPING : ANIM_SPEED_DEFAULT;
        int frameStep = ((int) (animTimeSeconds / speedFactor)) % 2; // 0..1

        if (currentState == State.SLEEPING) {
            row = 2;
            col = frameStep;
        } else {
            if (currentState == State.IDLE) {
                frameStep = 0; // quieto: primer frame de la pareja
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
     * Dibuja un frame concreto del spritesheet en el Canvas.
     *
     * @param row fila del spritesheet
     * @param col columna del spritesheet
     */
    private void drawFrame(int row, int col) {
        gc.clearRect(0, 0, view.getWidth(), view.getHeight());

        if (spriteSheet == null) {
            // Fallback visual si falla la carga
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
     * Carga el spritesheet probando rutas típicas.
     *
     * @return la imagen o {@code null} si falla.
     */
    private Image loadSpriteSheet() {
        Image img = AssetsManager.loadImage("/assets/images/choco_sheet.png");
        if (img != null) return img;

        img = AssetsManager.loadImage("assets/images/choco_sheet.png");
        return img;
    }
}
