package com.layla.entities;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.InputService;
import com.layla.core.SpriteAnimator;
import com.layla.model.PlayerStatId;
import com.layla.services.StatsService;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Entidad principal del Jugador.
 * <p>
 * Responsabilidades:
 * <ul>
 * <li><strong>Movimiento:</strong> Implementa física con inercia (aceleración/deceleración suavizada) basada en inputs.</li>
 * <li><strong>Renderizado:</strong> Compone el personaje usando dos capas de sprites (cuerpo y cabeza) para permitir disparar en una dirección mientras se camina en otra.</li>
 * <li><strong>Estado:</strong> Gestiona la vida, invulnerabilidad temporal y la muerte.</li>
 * <li><strong>Interacción:</strong> Actúa como el centro de la cámara y el objetivo de los enemigos.</li>
 * </ul>
 */
public final class Player implements GameEntity {

    // =========================================================
    // Configuración de Física y Renderizado
    // =========================================================

    // Constantes de suavizado de movimiento (Tau = tiempo para alcanzar ~63% de la velocidad objetivo)
    private static final double TAU_ACCEL   = 0.035;
    private static final double TAU_DECEL   = 0.090;
    private static final double TAU_REVERSE = 0.045;

    /** Escala global visual (afecta a sprites y hitbox). */
    private static final double VISUAL_SCALE = 1.2;

    // Configuración del SpriteSheet
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;
    private static final int COLUMNS_IN_SHEET = 10;

    // Índices de filas en el SpriteSheet
    private static final int ROW_HEAD = 0;
    private static final int ROW_WALK_DOWN = 1;
    private static final int ROW_WALK_SIDE = 2;
    private static final int ROW_WALK_UP = 3;

    // Índices de columnas para la cabeza (Aiming vs Shooting)
    private static final int HEAD_IDX_AIM_DOWN   = 0;
    private static final int HEAD_IDX_SHOOT_DOWN = 1;
    private static final int HEAD_IDX_AIM_SIDE   = 2;
    private static final int HEAD_IDX_SHOOT_SIDE = 3;
    private static final int HEAD_IDX_AIM_UP     = 4;
    private static final int HEAD_IDX_SHOOT_UP   = 5;

    // Dimensiones lógicas y visuales
    private static final double HITBOX_SIZE = 24.0 * VISUAL_SCALE;
    private static final double SPRITE_SIZE = 48.0 * VISUAL_SCALE;

    // Ajustes de posición para centrar el sprite sobre la hitbox
    private static final double CENTER_OFFSET = (HITBOX_SIZE - SPRITE_SIZE) / 2.0;
    private static final double HEAD_OFFSET_Y = -12.0 * VISUAL_SCALE;

    // =========================================================
    // Estado de Animación
    // =========================================================

    /** Tiempo que la cara de "disparo" se mantiene visible tras dejar de disparar. */
    private static final double SHOOT_FACE_COOLDOWN = 0.25;

    private double lastShootTime = 99.0;
    private int latchedShootDir = -1; // Dirección de disparo "bloqueada" visualmente
    private int currentShootDir = -1;

    // Animación de la cabeza al disparar
    private boolean isShootingFrame = false;
    private double shootFrameTimer = 0.0;

    // =========================================================
    // Estado de Juego
    // =========================================================

    private static final double INVULN_DURATION = 1.0;
    private static final Consumer<String> NO_OP_SFX = k -> {};

    // Componentes JavaFX
    private final StackPane viewRoot = new StackPane();
    private final Rectangle debugBox = new Rectangle(HITBOX_SIZE, HITBOX_SIZE, Color.TRANSPARENT);
    private final ImageView bodyView = new ImageView();
    private final ImageView headView = new ImageView();

    private final SpriteAnimator bodyAnimator;
    private final boolean hasSprite;

    // Dependencias
    private final Supplier<double[]> moveSupplier;
    private final InputService inputService;
    private final Pane boundsPane;
    private final StatsService statsService;
    private final Consumer<String> playSfx;

    // Límites del mundo (paredes invisibles)
    private double worldInsetLeft   = 0.0;
    private double worldInsetRight  = 0.0;
    private double worldInsetTop    = 0.0;
    private double worldInsetBottom = 0.0;

    // Variables de movimiento (velocidad actual)
    private double vx;
    private double vy;
    private int moveDir = 0; // 0=Abajo, 1=Derecha, 2=Arriba, 3=Izquierda

    // Stats
    private double health = 6.0;
    private double maxHealth = 6.0;
    private boolean dead = false;
    private double invulnTimer = 0.0;

    /** Registro de la última fuente de daño para estadísticas o mensajes de muerte. */
    private String lastHitSource = null;

    /**
     * Constructor simplificado.
     *
     * @param input        Servicio de entrada.
     * @param boundsPane   Panel que delimita el mundo.
     * @param statsService Servicio de estadísticas.
     */
    public Player(InputService input, Pane boundsPane, StatsService statsService) {
        this(input, boundsPane, statsService, null);
    }

    /**
     * Constructor principal del Jugador.
     * Inicializa gráficos, animaciones y estado base.
     *
     * @param input        Servicio de entrada (teclado/gamepad).
     * @param boundsPane   Panel contenedor para calcular límites de movimiento.
     * @param statsService Servicio para consultar velocidad y vida máxima.
     * @param playSfx      Callback para reproducir efectos de sonido.
     */
    public Player(InputService input, Pane boundsPane, StatsService statsService, Consumer<String> playSfx) {
        this.inputService = Objects.requireNonNull(input, "input");
        this.moveSupplier = input::getMoveVector;
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.statsService = (statsService != null) ? statsService : com.layla.AppContext.stats();
        this.playSfx = (playSfx != null) ? playSfx : NO_OP_SFX;

        // Invulnerabilidad inicial breve al spawnear
        this.invulnTimer = 1.0;

        // Configuración de debug (hitbox visible si no hay sprite)
        debugBox.setStroke(Color.BLACK);
        debugBox.setStrokeWidth(1);
        debugBox.setFill(Color.TRANSPARENT);

        // Carga de recursos gráficos
        Image sheet = AssetsManager.loadImage("assets/images/player_sheet.png");
        if (sheet == null) {
            hasSprite = false;
            debugBox.setFill(Color.CYAN); // Fallback visual
            bodyAnimator = new SpriteAnimator(1, 1, 1, 1, 1);
        } else {
            hasSprite = true;
            debugBox.setStroke(Color.TRANSPARENT);

            setupImageView(bodyView, sheet);
            setupImageView(headView, sheet);

            // Los ImageView no son gestionados por el layout del StackPane para posicionarlos manualmente
            bodyView.setManaged(false);
            headView.setManaged(false);

            // Centrado de sprites respecto a la hitbox
            bodyView.setLayoutX(CENTER_OFFSET);
            bodyView.setLayoutY(CENTER_OFFSET);

            headView.setLayoutX(CENTER_OFFSET);
            headView.setLayoutY(CENTER_OFFSET + HEAD_OFFSET_Y);

            // Configurador del animador del cuerpo
            bodyAnimator = new SpriteAnimator(FRAME_W, FRAME_H, 8, 12, COLUMNS_IN_SHEET);

            Rectangle2D initialRect = new Rectangle2D(0, 0, FRAME_W, FRAME_H);
            bodyView.setViewport(initialRect);
            headView.setViewport(initialRect);
        }

        viewRoot.getChildren().addAll(debugBox, bodyView, headView);
        viewRoot.setLayoutX(200);
        viewRoot.setLayoutY(200);

        // Forzar tamaño del StackPane para que coincida con la hitbox lógica
        viewRoot.setMinSize(HITBOX_SIZE, HITBOX_SIZE);
        viewRoot.setMaxSize(HITBOX_SIZE, HITBOX_SIZE);

        boundsPane.getChildren().add(viewRoot);
    }

    /**
     * Configura propiedades comunes para los ImageView del jugador.
     */
    private void setupImageView(ImageView v, Image img) {
        v.setImage(img);
        v.setFitWidth(SPRITE_SIZE);
        v.setFitHeight(SPRITE_SIZE);
        v.setSmooth(false); // Pixel-art nítido
        v.setPreserveRatio(true);
    }

    // ======================
    // Getters / Setters
    // ======================

    public void setLastHitSource(String source) {
        this.lastHitSource = source;
    }

    public String getLastHitSource() {
        return lastHitSource;
    }

    /**
     * Define márgenes internos ("paredes invisibles") dentro del {@code boundsPane}.
     */
    public void setWorldInset(double left, double right, double top, double bottom) {
        this.worldInsetLeft = Math.max(0.0, left);
        this.worldInsetRight = Math.max(0.0, right);
        this.worldInsetTop = Math.max(0.0, top);
        this.worldInsetBottom = Math.max(0.0, bottom);
    }

    public double getX() { return viewRoot.getLayoutX(); }
    public double getY() { return viewRoot.getLayoutY(); }

    // Centro geométrico para cálculos de distancia
    public double getCenterX() { return getX() + HITBOX_SIZE * 0.5; }
    public double getCenterY() { return getY() + HITBOX_SIZE * 0.5; }

    // ======================
    // Bucle Principal
    // ======================

    @Override
    public void update(double dt) {
        if (dt <= 0.0 || dead) {
            return;
        }

        // Gestión de invulnerabilidad y parpadeo visual
        if (invulnTimer > 0.0) {
            invulnTimer = Math.max(0.0, invulnTimer - dt);
        }
        // Parpadeo rápido cuando es invulnerable
        viewRoot.setOpacity(invulnTimer > 0.0 && (invulnTimer % 0.15 > 0.07) ? 0.4 : 1.0);

        syncMaxHealthFromStats();
        handleMovement(dt);
        updateAnimation(dt);
    }

    /**
     * Aplica la física de movimiento.
     * Utiliza una interpolación exponencial para suavizar el inicio y fin del movimiento (inercia).
     *
     * @param dt Delta time.
     */
    private void handleMovement(double dt) {
        double[] mv = moveSupplier.get(); // Vector de input normalizado
        double maxSpeed = statsService.getStat(PlayerStatId.VELOCIDAD_DE_MOVIMIENTO);

        double targetVx = mv[0] * maxSpeed;
        double targetVy = mv[1] * maxSpeed;

        // Selección dinámica de la constante de tiempo (acelerar vs frenar vs girar)
        double tauX = pickTau(vx, targetVx);
        double tauY = pickTau(vy, targetVy);

        // Factores de interpolación
        double ax = 1.0 - Math.exp(-dt / tauX);
        double ay = 1.0 - Math.exp(-dt / tauY);

        // Aplicar velocidad
        vx += (targetVx - vx) * ax;
        vy += (targetVy - vy) * ay;

        // Limitar velocidad máxima (clamping circular) si excede por inercia
        double speed = Math.hypot(vx, vy);
        if (speed > maxSpeed && speed > 0.0) {
            double s = maxSpeed / speed;
            vx *= s;
            vy *= s;
        }

        double nextX = viewRoot.getLayoutX() + vx * dt;
        double nextY = viewRoot.getLayoutY() + vy * dt;

        // Clamp de posición dentro de los límites del mundo (insets)
        double minX = worldInsetLeft;
        double minY = worldInsetTop;

        double maxX = Math.max(minX, boundsPane.getWidth() - getWidth() - worldInsetRight);
        double maxY = Math.max(minY, boundsPane.getHeight() - getHeight() - worldInsetBottom);

        if (nextX < minX) nextX = minX; else if (nextX > maxX) nextX = maxX;
        if (nextY < minY) nextY = minY; else if (nextY > maxY) nextY = maxY;

        viewRoot.setLayoutX(nextX);
        viewRoot.setLayoutY(nextY);
    }

    /**
     * Actualiza la animación del sprite.
     * Gestiona independientemente la dirección del cuerpo (movimiento) y de la cabeza (disparo).
     *
     * @param dt Delta time.
     */
    private void updateAnimation(double dt) {
        if (!hasSprite) return;

        double[] aim = inputService.getAimArrowCardinal();
        boolean isShootingInput = (aim[0] != 0.0 || aim[1] != 0.0);

        // Determinar dirección de disparo basada en input
        int newShootDir = -1;
        if (isShootingInput) {
            if (Math.abs(aim[0]) > Math.abs(aim[1])) newShootDir = (aim[0] > 0) ? 1 : 3;
            else newShootDir = (aim[1] > 0) ? 0 : 2;
        }

        // Lógica de frames de disparo (retroceso visual de la cabeza)
        if (isShootingInput) {
            lastShootTime = 0.0;
            latchedShootDir = newShootDir;

            if (newShootDir != currentShootDir) {
                currentShootDir = newShootDir;
                isShootingFrame = true; // Iniciar "kickback" de animación
                shootFrameTimer = 0.15;
            } else {
                shootFrameTimer -= dt;
                if (shootFrameTimer <= 0.0) {
                    isShootingFrame = !isShootingFrame; // Alternar frame
                    shootFrameTimer = 0.15;
                }
            }
        } else {
            currentShootDir = -1;
            lastShootTime += dt;
            isShootingFrame = false;
            shootFrameTimer = 0.0;
        }

        // Mantener la cara de disparo un poco después de soltar el botón
        boolean showShootFace = (lastShootTime < SHOOT_FACE_COOLDOWN);

        // Determinar dirección de movimiento
        boolean moving = Math.abs(vx) > (5.0 * VISUAL_SCALE) || Math.abs(vy) > (5.0 * VISUAL_SCALE);
        if (moving) {
            if (Math.abs(vx) > Math.abs(vy)) moveDir = (vx > 0) ? 1 : 3;
            else moveDir = (vy > 0) ? 0 : 2;
        }

        // La cabeza mira a donde disparas, o a donde caminas si no disparas
        int headDir = showShootFace ? latchedShootDir : moveDir;
        if (headDir == -1) headDir = moveDir;

        // ---- Renderizado del Cuerpo (Body) ----
        int bodyRowTarget = ROW_WALK_DOWN;
        int bodyFrames = 8;
        boolean bodyFlip = false;

        switch (moveDir) {
            case 0 -> { bodyRowTarget = ROW_WALK_DOWN; bodyFrames = 8; }
            case 2 -> { bodyRowTarget = ROW_WALK_UP;   bodyFrames = 2; } // Animación de espalda simplificada
            case 1 -> { bodyRowTarget = ROW_WALK_SIDE; bodyFrames = 8; bodyFlip = false; }
            case 3 -> { bodyRowTarget = ROW_WALK_SIDE; bodyFrames = 8; bodyFlip = true; } // Espejo horizontal
        }

        if (moving) {
            bodyAnimator.setAnimationConfig(bodyRowTarget, 0, bodyFrames, true);
            bodyAnimator.update(dt);
        } else {
            bodyAnimator.setAnimationConfig(bodyRowTarget, 0, 1, true); // Frame estático (Idle)
        }

        bodyView.setScaleX(bodyFlip ? -1 : 1);
        bodyView.setViewport(bodyAnimator.getCurrentViewport());

        // ---- Renderizado de la Cabeza (Head) ----
        int headFrameIdx = HEAD_IDX_AIM_DOWN;
        boolean headFlip = false;

        boolean useShootFrame = isShootingInput && isShootingFrame;

        switch (headDir) {
            case 0 -> headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_DOWN : HEAD_IDX_AIM_DOWN;
            case 2 -> headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_UP : HEAD_IDX_AIM_UP;
            case 1 -> { headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_SIDE : HEAD_IDX_AIM_SIDE; headFlip = false; }
            case 3 -> { headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_SIDE : HEAD_IDX_AIM_SIDE; headFlip = true; }
        }

        headView.setScaleX(headFlip ? -1 : 1);
        headView.setViewport(new Rectangle2D(headFrameIdx * FRAME_W, ROW_HEAD * FRAME_H, FRAME_W, FRAME_H));
    }

    /**
     * Elige la constante de tiempo (Tau) para el suavizado de movimiento.
     * Permite tener aceleración, frenado y cambio de dirección con distintas sensaciones ("feel").
     */
    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL; // Frenando
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE; // Cambio de sentido rápido
        return TAU_ACCEL; // Acelerando normal
    }

    @Override
    public Node getView() {
        return viewRoot;
    }

    @Override
    public Bounds getBounds() {
        return new BoundingBox(viewRoot.getLayoutX(), viewRoot.getLayoutY(), HITBOX_SIZE, HITBOX_SIZE);
    }

    public void setPosition(double x, double y) {
        viewRoot.setLayoutX(x);
        viewRoot.setLayoutY(y);
    }

    public double getWidth()  { return HITBOX_SIZE; }
    public double getHeight() { return HITBOX_SIZE; }

    // ======================
    // Salud y Vida
    // ======================

    public double getHealth() { return health; }
    public double getMaxHealth() { return maxHealth; }
    public boolean isDead() { return dead; }

    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(health, maxHealth));
        if (this.health <= 0.0) die();
    }

    public void addHealth(double delta) {
        setHealth(this.health + delta);
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = Math.max(0.0, maxHealth);
        if (health > this.maxHealth) health = this.maxHealth;
    }

    /**
     * Aplica daño al jugador si no está invulnerable.
     * Inicia el temporizador de invulnerabilidad y reproduce sonido.
     */
    public void takeDamage(double amount) {
        if (dead || amount <= 0.0) return;
        if (invulnTimer > 0.0) return;

        health = Math.max(0.0, health - amount);
        if (health <= 0.0) {
            die();
        } else {
            invulnTimer = INVULN_DURATION;
            playSfx.accept("hurt");
        }
    }

    /**
     * Maneja la muerte del jugador.
     * Rota el sprite y notifica (sonido y flag).
     */
    private void die() {
        if (dead) return;

        dead = true;
        health = 0.0;

        playSfx.accept("player_death");

        // Efecto visual de muerte
        viewRoot.setOpacity(0.5);
        viewRoot.setRotate(90.0);
    }

    /**
     * Sincroniza la vida máxima local con la del StatsService.
     * Necesario si algún ítem aumenta la vida máxima dinámicamente.
     */
    private void syncMaxHealthFromStats() {
        double desiredMax = statsService.getStat(PlayerStatId.VIDA_MAXIMA);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            setMaxHealth(desiredMax);
        }
    }
}
