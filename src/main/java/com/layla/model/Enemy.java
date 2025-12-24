package com.layla.model;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.core.GameEntity;
import com.layla.core.SpriteAnimator;
import com.layla.entities.Player;
import com.layla.entities.Projectile;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Entidad enemigo con movimiento, disparo, colisiones y animación por sprites.
 *
 * <p>
 * Esta clase implementa varios {@link EnemyType} en un solo archivo (shooter, melee, tank, turret y kamikaze)
 * para reducir el riesgo de integración y evitar repartir lógica por muchos ficheros.
 * </p>
 *
 * <p>
 * Si en el futuro tienes tiempo, el refactor natural sería extraer comportamiento/animación por tipo,
 * pero esta versión está hecha de forma monolítica a propósito.
 * </p>
 */
public final class Enemy implements GameEntity {

    // =========================================================
    // Tamaño base / escala
    // =========================================================

    /**
     * Escala visual global aplicada al render del sprite y a algunos umbrales (deadzone, márgenes, etc.).
     */
    private static final double VISUAL_SCALE = 1.5;

    /**
     * Tamaño lógico de la hitbox (no necesariamente coincide con el tamaño en píxeles del sprite).
     */
    private static final double WIDTH = 22.0 * VISUAL_SCALE;
    private static final double HEIGHT = 22.0 * VISUAL_SCALE;

    /** Umbral pequeño para evitar divisiones por cero y direcciones sin longitud. */
    private static final double EPSILON = 1e-6;

    // =========================================================
    // Anti-stuck (misma lógica, más legible)
    // =========================================================

    /** Si se mueve menos que esta distancia por segundo, se considera que está "casi parado". */
    private static final double STUCK_MIN_DISTANCE_PER_SEC = 30.0;

    /** Segundos de movimiento insuficiente necesarios para activar un empujón aleatorio. */
    private static final double STUCK_TRIGGER_SECONDS = 0.5;

    /** Duración del empujón aleatorio (se guarda como valor negativo en {@code stuckTimer}). */
    private static final double STUCK_PUSH_SECONDS = 0.5;

    // =========================================================
    // Identidad
    // =========================================================

    private final EnemyType type;

    // =========================================================
    // Componentes JavaFX
    // =========================================================

    private final StackPane viewRoot = new StackPane();
    private final Rectangle debugBox = new Rectangle(WIDTH, HEIGHT);

    /** Sprite principal (piernas/cuerpo según el tipo). */
    private final ImageView spriteView = new ImageView();

    /** Capa "cabeza" para humanoides y tanque (oculta en tipos que no la usan). */
    private final ImageView headView = new ImageView();

    /** Animador compartido (se usa sobre todo para las piernas del shooter). */
    private final SpriteAnimator animator;

    private final boolean hasSprite;

    // =========================================================
    // Dependencias / callbacks
    // =========================================================

    private final Pane boundsPane;
    private final Supplier<double[]> playerCenterSupplier;
    private final Supplier<List<GameEntity>> obstaclesSupplier;

    private final Consumer<GameEntity> onRemove;
    private final Consumer<GameEntity> onSpawn;

    /** Reproduce SFX; garantizado no-null (no-op si el caller pasa null). */
    private final Consumer<String> playSfx;

    // =========================================================
    // Multiplicadores (balance)
    // =========================================================

    private final double hpMultiplier;
    private final double speedMultiplier;
    private final double damageMultiplier;

    // =========================================================
    // Estado runtime
    // =========================================================

    private double hp;
    private double maxHealth;
    private boolean dead = false;

    // =========================================================
    // Timers (IA/disparo)
    // =========================================================

    private double timeSinceShot = 0.0;
    private double aiTime = 0.0;

    // Turret: estado/tiempos del burst
    private int burstShotsRemaining = 0;
    private double burstShotTimer = 0.0;
    private double burstCooldownTimer = 0.0;

    // =========================================================
    // Anti-stuck (estado)
    // =========================================================

    private double stuckTimer = 0.0;
    private final double[] stuckDirection = { 0.0, 0.0 };
    private double lastX;
    private double lastY;

    // =========================================================
    // Direcciones temporales (reutilización)
    // =========================================================

    private final double[] tmpDir = new double[2];
    private final double collisionRadius = Math.min(WIDTH, HEIGHT) * 0.5;

    // =========================================================
    // Feedback de impacto
    // =========================================================

    private PauseTransition hitFlashTimer;
    private final ColorAdjust hitFlashEffect = new ColorAdjust(0, 0, 0.5, 0);

    // =========================================================
    // SHOOTER (32x32)
    // =========================================================

    private static final int SHOOTER_FRAME_W = 32;
    private static final int SHOOTER_FRAME_H = 32;
    private static final int SHOOTER_COLUMNS = 8;
    private static final int SHOOTER_WALK_FRAMES = 6;

    private static final int SHOOTER_ROW_HEAD = 0;
    private static final int SHOOTER_ROW_WALK_DOWN = 1;
    private static final int SHOOTER_ROW_WALK_UP = 2;
    private static final int SHOOTER_ROW_WALK_SIDE = 3;

    private static final double SHOOTER_HEAD_Y_OFFSET = -18.0 * VISUAL_SCALE;
    private static final double SHOOTER_HEAD_X_OFFSET = -5.0 * VISUAL_SCALE;

    // =========================================================
    // MELEE (melee.png)
    // =========================================================

    private static final int MELEE_FRAME_W = 31;
    private static final int MELEE_HEAD_H = 25;
    private static final int MELEE_LEGS_H = 15;

    private static final int MELEE_Y_HEAD = 1;
    private static final int MELEE_Y_LEGS_DOWN = 30;
    private static final int MELEE_Y_LEGS_SIDE = 46;

    private static final int MELEE_WALK_FRAMES = 6;
    private static final int MELEE_WALK_DOWN_START_COL = 0;
    private static final int MELEE_WALK_SIDE_START_COL = 2;

    private static final double MELEE_HEAD_Y_OFFSET = -14.0 * VISUAL_SCALE;
    private static final double MELEE_HEAD_X_OFFSET = 0.0 * VISUAL_SCALE;

    private static final double MELEE_FPS = 10.0;

    // =========================================================
    // KAMIKAZE (kamikaze.png)
    // =========================================================

    private static final int KAMIKAZE_FRAME_W = 48;
    private static final int KAMIKAZE_FRAME_H = 48;

    private static final int KAMIKAZE_ROW_DOWN_UP = 0;
    private static final int KAMIKAZE_ROW_RIGHT_LEFT = 1;

    private static final int KAMIKAZE_COL_1 = 0;
    private static final int KAMIKAZE_COL_4 = 3;

    private int kamikazeActiveRow = -1;
    private int kamikazeActiveCol = -1;

    // =========================================================
    // TURRET (turret.png)
    // =========================================================

    private static final int TURRET_FRAME_W = 32;
    private static final int TURRET_FRAME_H = 32;
    private static final int TURRET_COL = 4;

    private static final int TURRET_ROW_DOWN = 0;
    private static final int TURRET_ROW_UP = 1;
    private static final int TURRET_ROW_RIGHT = 2;
    private static final int TURRET_ROW_LEFT = 3;

    /** Pequeño padding para evitar "cortes" al recortar del spritesheet. */
    private static final int TURRET_X_PAD = 8;
    private static final int TURRET_Y_PAD = 0;

    private int turretActiveRow = -1;

    // =========================================================
    // TANK (tank.png)
    // =========================================================

    private static final int TANK_TILE = 32;

    private static final int TANK_HEAD_W = 32;
    private static final int TANK_HEAD_H = 32;

    /** El cuerpo del tanque son 2x2 tiles => 64x64 por frame. */
    private static final int TANK_BODY_W = 64;
    private static final int TANK_BODY_H = 64;

    private static final int TANK_ROW_HEAD = 0;
    private static final int TANK_ROW_BODY_RIGHT_TOP = 1;
    private static final int TANK_ROW_BODY_DOWN_TOP = 3;

    private static final int TANK_WALK_FRAMES = 6;
    private static final double TANK_FPS = 10.0;

    private static final double TANK_BODY_X_OFFSET = 0.0 * VISUAL_SCALE;
    private static final double TANK_BODY_Y_OFFSET = 0.0 * VISUAL_SCALE;

    private static final double TANK_HEAD_GAP_Y = 18.0 * VISUAL_SCALE;

    private static final double TANK_HEAD_X_OFFSET = 0.0 * VISUAL_SCALE;
    private static final double TANK_HEAD_Y_OFFSET = 20.0 * VISUAL_SCALE;

    private double tankFrameTimer = 0.0;
    private int tankFrameIdx = 0;

    // =========================================================
    // Estado animación MELEE
    // =========================================================

    private double meleeFrameTimer = 0.0;
    private int meleeFrameIdx = 0;
    private int meleeActiveHeadCol = -1;

    // =========================================================
    // Suavizado de "facing" común para humanoides
    // =========================================================

    private enum Facing { DOWN, UP, RIGHT, LEFT }

    private Facing facing = Facing.DOWN;
    private Facing pendingFacing = Facing.DOWN;
    private double pendingFacingTime = 0.0;

    private boolean movingAnim = false;
    private static final double MOVE_START = 0.35;
    private static final double MOVE_STOP = 0.20;

    private static final double FACE_DEADZONE = 6.0 * VISUAL_SCALE;
    private static final double FACING_SWITCH_DELAY = 0.06;

    private double lastAnimCx = Double.NaN;
    private double lastAnimCy = Double.NaN;

    // Cachés shooter (reduce cambios de viewport)
    private int activeLegsRow = -1;
    private int activeLegsFrames = -1;
    private boolean activeLoop = false;
    private boolean activeFlip = false;
    private int activeHeadCol = -1;

    /**
     * Crea un enemigo.
     *
     * @param type tipo de enemigo
     * @param boundsPane pane donde se añade la vista del enemigo
     * @param playerCenterSupplier proveedor del centro del jugador {x,y}
     * @param obstaclesSupplier proveedor de obstáculos bloqueantes (puede ser null)
     * @param onRemove callback cuando esta entidad debe eliminarse del mundo
     * @param onSpawn callback para spawnear nuevas entidades (proyectiles/fx)
     * @param playSfx callback para reproducir SFX (puede ser null; se convierte en no-op)
     * @param hpMultiplier multiplicador aplicado a la vida base
     * @param speedMultiplier multiplicador aplicado a la velocidad base
     * @param damageMultiplier multiplicador aplicado al daño (contacto/proyectil)
     */
    public Enemy(
            EnemyType type,
            Pane boundsPane,
            Supplier<double[]> playerCenterSupplier,
            Supplier<List<GameEntity>> obstaclesSupplier,
            Consumer<GameEntity> onRemove,
            Consumer<GameEntity> onSpawn,
            Consumer<String> playSfx,
            double hpMultiplier,
            double speedMultiplier,
            double damageMultiplier
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.obstaclesSupplier = obstaclesSupplier;
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = playSfx != null ? playSfx : k -> { };

        this.hpMultiplier = Math.max(0.0, hpMultiplier);
        this.speedMultiplier = Math.max(0.0, speedMultiplier);
        this.damageMultiplier = Math.max(0.0, damageMultiplier);

        debugBox.setStroke(Color.BLACK);
        debugBox.setFill(colorForType(type));

        Image sheet = loadSheetForType(type);
        if (sheet == null) {
            sheet = AssetsManager.loadImage("assets/images/enemies/" + type.name() + ".png");
        }

        if (sheet != null) {
            hasSprite = true;

            spriteView.setImage(sheet);
            spriteView.setSmooth(false);

            headView.setImage(sheet);
            headView.setSmooth(false);
            headView.setMouseTransparent(true);

            // Si hay sprite, ocultamos el debugBox.
            debugBox.setFill(Color.TRANSPARENT);
            debugBox.setStroke(Color.TRANSPARENT);

            configureSpriteViewsForType(type);
        } else {
            hasSprite = false;
            headView.setVisible(false);
        }

        animator = (type == EnemyType.SHOOTER)
                ? new SpriteAnimator(SHOOTER_FRAME_W, SHOOTER_FRAME_H, SHOOTER_WALK_FRAMES, 10, SHOOTER_COLUMNS)
                : new SpriteAnimator(SHOOTER_FRAME_W, SHOOTER_FRAME_H, 2, 6, 10);

        viewRoot.getChildren().addAll(debugBox, spriteView);
        if (type == EnemyType.SHOOTER || type == EnemyType.MELEE || type == EnemyType.TANK) {
            viewRoot.getChildren().add(headView);
        }

        viewRoot.setManaged(false);
        boundsPane.getChildren().add(viewRoot);

        EnemyProfile profile = AppContext.balance().profile(type);
        this.maxHealth = profile != null ? Math.max(0.0, profile.baseHp * this.hpMultiplier) : 0.0;
        this.hp = this.maxHealth;
    }

    /** @return tipo de enemigo. */
    public EnemyType getType() {
        return type;
    }

    /**
     * Actualiza movimiento, animación y disparo.
     *
     * @param dt delta time en segundos
     */
    @Override
    public void update(double dt) {
        if (dead || dt <= 0.0) return;

        EnemyProfile profile = AppContext.balance().profile(type);
        if (profile == null) return;

        syncHealthWithProfile(profile);

        final double[] playerCenter = playerCenterSupplier.get();
        aiTime += dt;

        updateAntiStuck(dt);

        handleMovement(profile, playerCenter, dt);
        updateAnimation(dt, playerCenter);

        if (type == EnemyType.TURRET) {
            handleTurretShooting(profile, playerCenter, dt);
        } else {
            handleDefaultShooting(profile, playerCenter, dt);
        }
    }

    /** @return nodo JavaFX que renderiza esta entidad. */
    @Override
    public Node getView() {
        return viewRoot;
    }

    /** @return bounds de colisión usados por el mundo. */
    @Override
    public Bounds getBounds() {
        return new BoundingBox(viewRoot.getLayoutX(), viewRoot.getLayoutY(), WIDTH, HEIGHT);
    }

    /**
     * Gestiona colisiones con otras entidades.
     *
     * @param other otra entidad
     */
    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;

        if (other instanceof Player player) {
            EnemyProfile profile = AppContext.balance().profile(type);
            double dmg = profile != null ? profile.contactDmg : AppContext.balance().enemyContactDamage;
            dmg *= damageMultiplier;

            if (dmg <= 0.0) return;

            player.setLastHitSource(type.name());

            final double before = player.getHealth();
            player.takeDamage(dmg);

            // Solo reproducir "hurt" si el daño realmente reduce vida (sin invulnerabilidad).
            if (player.getHealth() < before) {
                playSfx.accept("hurt");
            }
        }
    }

    /**
     * Aplica daño al enemigo.
     *
     * @param dmg daño a aplicar (debe ser > 0)
     */
    public void applyDamage(double dmg) {
        if (dead || dmg <= 0.0) return;

        hp -= dmg;
        if (hp <= 0.0) {
            die();
            return;
        }

        flashHit();
        playSfx.accept("hit");
    }

    /** @return si el enemigo está muerto y debería eliminarse. */
    public boolean isDead() {
        return dead;
    }

    /** @return ancho de la hitbox. */
    public double getWidth() {
        return WIDTH;
    }

    /** @return alto de la hitbox. */
    public double getHeight() {
        return HEIGHT;
    }

    /** @return radio de colisión (para lógicas radiales). */
    public double getCollisionRadius() {
        return collisionRadius;
    }

    /** @return centro X calculado con layout y hitbox. */
    public double getCenterX() {
        return viewRoot.getLayoutX() + WIDTH * 0.5;
    }

    /** @return centro Y calculado con layout y hitbox. */
    public double getCenterY() {
        return viewRoot.getLayoutY() + HEIGHT * 0.5;
    }

    /** @return X del layout. */
    public double getX() {
        return viewRoot.getLayoutX();
    }

    /** @return Y del layout. */
    public double getY() {
        return viewRoot.getLayoutY();
    }

    /**
     * Establece la posición del enemigo en el mundo.
     *
     * @param x coordenada X
     * @param y coordenada Y
     */
    public void setPosition(double x, double y) {
        viewRoot.setLayoutX(x);
        viewRoot.setLayoutY(y);
    }

    // =========================================================
    // Helpers de inicialización
    // =========================================================

    /**
     * Color usado para el modo debug (cuando no hay sprite).
     *
     * @param t tipo de enemigo
     * @return color debug
     */
    private static Color colorForType(EnemyType t) {
        return switch (t) {
            case SHOOTER -> Color.ORANGE;
            case MELEE -> Color.CRIMSON;
            case TURRET -> Color.DODGERBLUE;
            case TANK -> Color.DARKOLIVEGREEN;
            case KAMIKAZE -> Color.MAGENTA;
        };
    }

    /**
     * Carga el spritesheet por defecto para cada tipo.
     *
     * @param type tipo de enemigo
     * @return imagen del spritesheet (o null si no existe)
     */
    private static Image loadSheetForType(EnemyType type) {
        return switch (type) {
            case SHOOTER -> AssetsManager.loadImage("assets/images/shooter.png");
            case MELEE -> AssetsManager.loadImage("assets/images/melee.png");
            case KAMIKAZE -> AssetsManager.loadImage("assets/images/kamikaze.png");
            case TANK -> AssetsManager.loadImage("assets/images/tank.png");
            case TURRET -> AssetsManager.loadImage("assets/images/turret.png");
        };
    }

    /**
     * Configura tamaños, viewports y capas (sprite/cabeza) según el tipo.
     * <p>
     * También inicializa estados "activos" para evitar refrescos de viewport innecesarios
     * en algunos tipos.
     * </p>
     *
     * @param type tipo de enemigo
     */
    private void configureSpriteViewsForType(EnemyType type) {
        if (type == EnemyType.SHOOTER) {
            spriteView.setFitWidth(SHOOTER_FRAME_W * VISUAL_SCALE);
            spriteView.setFitHeight(SHOOTER_FRAME_H * VISUAL_SCALE);

            headView.setFitWidth(SHOOTER_FRAME_W * VISUAL_SCALE);
            headView.setFitHeight(SHOOTER_FRAME_H * VISUAL_SCALE);
            headView.setVisible(true);

            applyShooterHeadOffsets(false);
            headView.setViewport(new Rectangle2D(0, 0, SHOOTER_FRAME_W, SHOOTER_FRAME_H));
            return;
        }

        if (type == EnemyType.MELEE) {
            spriteView.setFitWidth(MELEE_FRAME_W * VISUAL_SCALE);
            spriteView.setFitHeight(MELEE_LEGS_H * VISUAL_SCALE);

            headView.setFitWidth(MELEE_FRAME_W * VISUAL_SCALE);
            headView.setFitHeight(MELEE_HEAD_H * VISUAL_SCALE);
            headView.setVisible(true);

            applyMeleeHeadOffsets(false);

            headView.setViewport(new Rectangle2D(0, MELEE_Y_HEAD, MELEE_FRAME_W, MELEE_HEAD_H));
            spriteView.setViewport(new Rectangle2D(0, MELEE_Y_LEGS_DOWN, MELEE_FRAME_W, MELEE_LEGS_H));
            return;
        }

        if (type == EnemyType.TANK) {
            spriteView.setManaged(false);
            headView.setManaged(false);

            spriteView.setFitWidth(TANK_BODY_W * VISUAL_SCALE);
            spriteView.setFitHeight(TANK_BODY_H * VISUAL_SCALE);

            headView.setFitWidth(TANK_HEAD_W * VISUAL_SCALE);
            headView.setFitHeight(TANK_HEAD_H * VISUAL_SCALE);
            headView.setVisible(true);
            headView.setScaleX(1);

            headView.setViewport(new Rectangle2D(
                    0,
                    TANK_ROW_HEAD * TANK_TILE,
                    TANK_HEAD_W,
                    TANK_HEAD_H
            ));

            spriteView.setScaleX(1);
            spriteView.setViewport(new Rectangle2D(
                    0,
                    TANK_ROW_BODY_DOWN_TOP * TANK_TILE,
                    TANK_BODY_W,
                    TANK_BODY_H
            ));

            positionTankParts();
            return;
        }

        if (type == EnemyType.KAMIKAZE) {
            spriteView.setFitWidth(KAMIKAZE_FRAME_W * VISUAL_SCALE);
            spriteView.setFitHeight(KAMIKAZE_FRAME_H * VISUAL_SCALE);
            headView.setVisible(false);

            spriteView.setViewport(new Rectangle2D(
                    KAMIKAZE_COL_1 * KAMIKAZE_FRAME_W,
                    KAMIKAZE_ROW_DOWN_UP * KAMIKAZE_FRAME_H,
                    KAMIKAZE_FRAME_W,
                    KAMIKAZE_FRAME_H
            ));

            kamikazeActiveRow = KAMIKAZE_ROW_DOWN_UP;
            kamikazeActiveCol = KAMIKAZE_COL_1;
            return;
        }

        if (type == EnemyType.TURRET) {
            spriteView.setFitWidth(TURRET_FRAME_W * VISUAL_SCALE);
            spriteView.setFitHeight(TURRET_FRAME_H * VISUAL_SCALE);
            headView.setVisible(false);

            // Por defecto mirando abajo (row 0, col 4).
            spriteView.setScaleX(1);
            spriteView.setViewport(new Rectangle2D(
                    TURRET_X_PAD + TURRET_COL * TURRET_FRAME_W,
                    TURRET_Y_PAD + TURRET_ROW_DOWN * TURRET_FRAME_H,
                    TURRET_FRAME_W,
                    TURRET_FRAME_H
            ));
            turretActiveRow = TURRET_ROW_DOWN;
        }
    }

    /**
     * Reposiciona el cuerpo y la cabeza del tanque dentro de la hitbox.
     * <p>
     * El tanque usa dos {@link ImageView}: el cuerpo (64x64) y la cabeza (32x32).
     * </p>
     */
    private void positionTankParts() {
        final double bodyW = TANK_BODY_W * VISUAL_SCALE;
        final double bodyH = TANK_BODY_H * VISUAL_SCALE;

        final double bodyX = (WIDTH - bodyW) * 0.5 + TANK_BODY_X_OFFSET;
        final double bodyY = (HEIGHT - bodyH) * 0.5 + TANK_BODY_Y_OFFSET;

        spriteView.setLayoutX(bodyX);
        spriteView.setLayoutY(bodyY);

        final double headW = TANK_HEAD_W * VISUAL_SCALE;
        final double headH = TANK_HEAD_H * VISUAL_SCALE;

        final double headX = (WIDTH - headW) * 0.5 + TANK_HEAD_X_OFFSET;
        final double headY = bodyY - TANK_HEAD_GAP_Y + TANK_HEAD_Y_OFFSET;

        headView.setLayoutX(headX);
        headView.setLayoutY(headY);
    }

    // =========================================================
    // Animación
    // =========================================================

    /**
     * Enruta la actualización de animación según el tipo.
     *
     * @param dt delta time (segundos)
     * @param playerCenter centro del jugador {x,y}
     */
    private void updateAnimation(double dt, double[] playerCenter) {
        if (!hasSprite) return;

        switch (type) {
            case SHOOTER -> updateShooterAnimation(dt, playerCenter);
            case MELEE -> updateMeleeAnimation(dt, playerCenter);
            case KAMIKAZE -> updateKamikazeAnimation(dt, playerCenter);
            case TANK -> updateTankAnimation(dt);
            case TURRET -> updateTurretAnimation(playerCenter);
        }
    }

    private void updateTurretAnimation(double[] playerCenter) {
        if (!isValidTarget(playerCenter)) return;

        final double cx = getCenterX();
        final double cy = getCenterY();

        final double dx = playerCenter[0] - cx;
        final double dy = playerCenter[1] - cy;

        final int row;
        if (Math.abs(dx) > Math.abs(dy)) {
            row = dx >= 0 ? TURRET_ROW_RIGHT : TURRET_ROW_LEFT;
        } else {
            row = dy >= 0 ? TURRET_ROW_DOWN : TURRET_ROW_UP;
        }

        if (row == turretActiveRow) return;

        spriteView.setScaleX(1);
        spriteView.setViewport(new Rectangle2D(
                TURRET_X_PAD + TURRET_COL * TURRET_FRAME_W,
                TURRET_Y_PAD + row * TURRET_FRAME_H,
                TURRET_FRAME_W,
                TURRET_FRAME_H
        ));
        turretActiveRow = row;
    }

    private void updateShooterAnimation(double dt, double[] playerCenter) {
        updateMovingAndFacing(dt, playerCenter, true);

        final int legsRow;
        final boolean flip;

        switch (facing) {
            case DOWN -> { legsRow = SHOOTER_ROW_WALK_DOWN; flip = false; }
            case UP -> { legsRow = SHOOTER_ROW_WALK_UP; flip = false; }
            case RIGHT -> { legsRow = SHOOTER_ROW_WALK_SIDE; flip = false; }
            case LEFT -> { legsRow = SHOOTER_ROW_WALK_SIDE; flip = true; }
            default -> { legsRow = SHOOTER_ROW_WALK_DOWN; flip = false; }
        }

        final int legsFrames = movingAnim ? SHOOTER_WALK_FRAMES : 1;
        final boolean loop = movingAnim;

        if (legsRow != activeLegsRow || legsFrames != activeLegsFrames || loop != activeLoop) {
            animator.setAnimationConfig(legsRow, 0, legsFrames, loop);
            activeLegsRow = legsRow;
            activeLegsFrames = legsFrames;
            activeLoop = loop;
        }

        if (flip != activeFlip) {
            spriteView.setScaleX(flip ? -1 : 1);
            activeFlip = flip;
        }

        if (movingAnim) animator.update(dt);
        spriteView.setViewport(animator.getCurrentViewport());

        final int headCol = headColFor(facing);

        headView.setScaleX(flip ? -1 : 1);
        applyShooterHeadOffsets(flip);

        if (headCol != activeHeadCol) {
            headView.setViewport(new Rectangle2D(
                    headCol * SHOOTER_FRAME_W,
                    SHOOTER_ROW_HEAD * SHOOTER_FRAME_H,
                    SHOOTER_FRAME_W,
                    SHOOTER_FRAME_H
            ));
            activeHeadCol = headCol;
        }
    }

    private void updateMeleeAnimation(double dt, double[] playerCenter) {
        updateMovingAndFacing(dt, playerCenter, true);

        final boolean flip = (facing == Facing.LEFT);

        final int legsY;
        final int startCol;
        switch (facing) {
            case RIGHT, LEFT -> { legsY = MELEE_Y_LEGS_SIDE; startCol = MELEE_WALK_SIDE_START_COL; }
            case UP, DOWN -> { legsY = MELEE_Y_LEGS_DOWN; startCol = MELEE_WALK_DOWN_START_COL; }
            default -> { legsY = MELEE_Y_LEGS_DOWN; startCol = MELEE_WALK_DOWN_START_COL; }
        }

        if (!movingAnim) {
            meleeFrameIdx = 0;
            meleeFrameTimer = 0.0;
        } else {
            meleeFrameTimer += dt;
            final double step = 1.0 / MELEE_FPS;
            while (meleeFrameTimer >= step) {
                meleeFrameTimer -= step;
                meleeFrameIdx = (meleeFrameIdx + 1) % MELEE_WALK_FRAMES;
            }
        }

        spriteView.setScaleX(flip ? -1 : 1);
        final int frameCol = startCol + meleeFrameIdx;

        spriteView.setViewport(new Rectangle2D(
                frameCol * MELEE_FRAME_W,
                legsY,
                MELEE_FRAME_W,
                MELEE_LEGS_H
        ));

        final int headCol = headColFor(facing);

        headView.setScaleX(flip ? -1 : 1);
        applyMeleeHeadOffsets(flip);

        if (headCol != meleeActiveHeadCol) {
            headView.setViewport(new Rectangle2D(
                    headCol * MELEE_FRAME_W,
                    MELEE_Y_HEAD,
                    MELEE_FRAME_W,
                    MELEE_HEAD_H
            ));
            meleeActiveHeadCol = headCol;
        }
    }

    private void updateTankAnimation(double dt) {
        // El tanque no “mira al jugador” si está idle; mantiene la orientación actual.
        updateMovingAndFacing(dt, null, false);

        final boolean flip = (facing == Facing.LEFT);
        final int bodyRowTop = (facing == Facing.RIGHT || facing == Facing.LEFT)
                ? TANK_ROW_BODY_RIGHT_TOP
                : TANK_ROW_BODY_DOWN_TOP;

        if (!movingAnim) {
            tankFrameIdx = 0;
            tankFrameTimer = 0.0;
        } else {
            tankFrameTimer += dt;
            final double step = 1.0 / TANK_FPS;
            while (tankFrameTimer >= step) {
                tankFrameTimer -= step;
                tankFrameIdx = (tankFrameIdx + 1) % TANK_WALK_FRAMES;
            }
        }

        final int x = tankFrameIdx * TANK_BODY_W;

        spriteView.setScaleX(flip ? -1 : 1);
        spriteView.setViewport(new Rectangle2D(
                x,
                bodyRowTop * TANK_TILE,
                TANK_BODY_W,
                TANK_BODY_H
        ));

        positionTankParts();
    }

    private void updateKamikazeAnimation(double dt, double[] playerCenter) {
        updateMovingAndFacing(dt, playerCenter, true);

        final int row;
        final int col;

        switch (facing) {
            case DOWN -> { row = KAMIKAZE_ROW_DOWN_UP; col = KAMIKAZE_COL_1; }
            case UP -> { row = KAMIKAZE_ROW_DOWN_UP; col = KAMIKAZE_COL_4; }
            case RIGHT -> { row = KAMIKAZE_ROW_RIGHT_LEFT; col = KAMIKAZE_COL_1; }
            case LEFT -> { row = KAMIKAZE_ROW_RIGHT_LEFT; col = KAMIKAZE_COL_4; }
            default -> { row = KAMIKAZE_ROW_DOWN_UP; col = KAMIKAZE_COL_1; }
        }

        if (row == kamikazeActiveRow && col == kamikazeActiveCol) return;

        spriteView.setScaleX(1);
        spriteView.setViewport(new Rectangle2D(
                col * KAMIKAZE_FRAME_W,
                row * KAMIKAZE_FRAME_H,
                KAMIKAZE_FRAME_W,
                KAMIKAZE_FRAME_H
        ));

        kamikazeActiveRow = row;
        kamikazeActiveCol = col;
    }

    /**
     * Actualiza el estado de movimiento/idle ({@code movingAnim}) y la orientación suavizada (facing).
     * <p>
     * - Si el enemigo se está moviendo, la orientación se calcula por el vector de desplazamiento.
     * - Si está idle y {@code allowLookAtPlayerWhenIdle} es true, puede “mirar” al jugador con un deadzone.
     * - El cambio de facing tiene un pequeño retardo para evitar “flickering” entre direcciones.
     * </p>
     *
     * @param dt delta time en segundos
     * @param playerCenter centro del jugador {x,y} (opcional; puede ser null)
     * @param allowLookAtPlayerWhenIdle si true, puede orientar hacia el jugador cuando está idle
     */
    private void updateMovingAndFacing(double dt, double[] playerCenter, boolean allowLookAtPlayerWhenIdle) {
        final double cx = getCenterX();
        final double cy = getCenterY();

        if (Double.isNaN(lastAnimCx)) {
            lastAnimCx = cx;
            lastAnimCy = cy;
        }

        final double vx = cx - lastAnimCx;
        final double vy = cy - lastAnimCy;

        lastAnimCx = cx;
        lastAnimCy = cy;

        final double speed = Math.hypot(vx, vy);
        movingAnim = movingAnim ? speed > MOVE_STOP : speed > MOVE_START;

        Facing candidate = facing;

        if (movingAnim) {
            if (Math.abs(vx) > Math.abs(vy)) {
                candidate = vx >= 0 ? Facing.RIGHT : Facing.LEFT;
            } else {
                candidate = vy >= 0 ? Facing.DOWN : Facing.UP;
            }
        } else if (allowLookAtPlayerWhenIdle && isValidTarget(playerCenter)) {
            final double dx = playerCenter[0] - cx;
            final double dy = playerCenter[1] - cy;

            if (Math.abs(dx) > Math.abs(dy)) {
                if (Math.abs(dx) > FACE_DEADZONE) candidate = dx >= 0 ? Facing.RIGHT : Facing.LEFT;
            } else {
                if (Math.abs(dy) > FACE_DEADZONE) candidate = dy >= 0 ? Facing.DOWN : Facing.UP;
            }
        }

        applyFacingSmoothing(candidate, dt);
    }

    /**
     * Aplica el “suavizado” del cambio de orientación.
     * <p>
     * Requiere que el candidato se mantenga durante {@link #FACING_SWITCH_DELAY} segundos antes de cambiar
     * realmente {@code facing}. Evita cambios rápidos cuando la dirección oscila.
     * </p>
     *
     * @param candidate orientación candidata
     * @param dt delta time
     */
    private void applyFacingSmoothing(Facing candidate, double dt) {
        if (candidate != facing) {
            if (candidate != pendingFacing) {
                pendingFacing = candidate;
                pendingFacingTime = 0.0;
            } else {
                pendingFacingTime += dt;
                if (pendingFacingTime >= FACING_SWITCH_DELAY) {
                    facing = candidate;
                    pendingFacingTime = 0.0;
                }
            }
        } else {
            pendingFacing = candidate;
            pendingFacingTime = 0.0;
        }
    }

    /**
     * Devuelve la columna de la cabeza (humanoides) según la orientación.
     *
     * @param facing orientación actual
     * @return columna en el spritesheet (0=abajo, 1=lados, 2=arriba)
     */
    private static int headColFor(Facing facing) {
        return switch (facing) {
            case DOWN -> 0;
            case UP -> 2;
            case RIGHT, LEFT -> 1;
        };
    }

    /**
     * Aplica offsets de render para colocar la “cabeza” del shooter sobre el cuerpo.
     *
     * @param flip si true, invierte el signo del offset X
     */
    private void applyShooterHeadOffsets(boolean flip) {
        final double sign = flip ? -1 : 1;
        headView.setTranslateX(sign * SHOOTER_HEAD_X_OFFSET);
        headView.setTranslateY(SHOOTER_HEAD_Y_OFFSET);
    }

    /**
     * Aplica offsets de render para colocar la “cabeza” del melee sobre las piernas.
     *
     * @param flip si true, invierte el signo del offset X
     */
    private void applyMeleeHeadOffsets(boolean flip) {
        final double sign = flip ? -1 : 1;
        headView.setTranslateX(sign * MELEE_HEAD_X_OFFSET);
        headView.setTranslateY(MELEE_HEAD_Y_OFFSET);
    }

    // =========================================================
    // Movimiento
    // =========================================================

    /**
     * Anti-stuck: detecta si el enemigo se queda “pegado” y aplica un empujón aleatorio temporal.
     * <p>
     * Conserva la lógica original:
     * - Si se mueve menos de un umbral durante {@link #STUCK_TRIGGER_SECONDS}, se elige una dirección aleatoria.
     * - Se fuerza un movimiento en esa dirección durante {@link #STUCK_PUSH_SECONDS}.
     * </p>
     *
     * @param dt delta time en segundos
     */
    private void updateAntiStuck(double dt) {
        final double distMoved = Math.hypot(viewRoot.getLayoutX() - lastX, viewRoot.getLayoutY() - lastY);

        if (distMoved < STUCK_MIN_DISTANCE_PER_SEC * dt) {
            stuckTimer += dt;

            if (stuckTimer > STUCK_TRIGGER_SECONDS) {
                stuckDirection[0] = ThreadLocalRandom.current().nextDouble(-1, 1);
                stuckDirection[1] = ThreadLocalRandom.current().nextDouble(-1, 1);
                stuckTimer = -STUCK_PUSH_SECONDS;
            }
        } else {
            stuckTimer = 0.0;
            lastX = viewRoot.getLayoutX();
            lastY = viewRoot.getLayoutY();
        }
    }

    /**
     * Decide y aplica el movimiento según el tipo y el perfil.
     * <p>
     * - Respeta enemigos estáticos (turret o profiles stationary salvo shooter/melee).
     * - Si está en modo “empujón anti-stuck”, se mueve en {@code stuckDirection} y sale.
     * - Si no hay objetivo válido, no se mueve.
     * </p>
     *
     * @param profile perfil de balance del enemigo
     * @param playerCenter centro del jugador
     * @param dt delta time
     */
    private void handleMovement(EnemyProfile profile, double[] playerCenter, double dt) {
        final boolean isStationary = profile.stationary && type != EnemyType.SHOOTER && type != EnemyType.MELEE;

        if (stuckTimer < 0) {
            stuckTimer += dt;
            moveWithSlide(stuckDirection, profile.speed * speedMultiplier * dt);
            return;
        }

        if (type == EnemyType.TURRET || isStationary || !isValidTarget(playerCenter)) return;

        switch (type) {
            case MELEE -> moveMeleeZigZag(profile, playerCenter, dt);
            case SHOOTER -> moveShooterKiting(profile, playerCenter, dt);
            case TANK -> moveTank(profile, playerCenter, dt);
            case KAMIKAZE -> moveKamikaze(profile, playerCenter, dt);
            default -> moveChasingPlayer(profile, playerCenter, dt);
        }
    }

    private void moveChasingPlayer(EnemyProfile profile, double[] playerCenter, double dt) {
        final double[] dir = directionTo(playerCenter);
        if (dir == null) return;

        applyJitter(dir, profile.jitter);
        moveWithSlide(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveMeleeZigZag(EnemyProfile profile, double[] playerCenter, double dt) {
        final double[] dir = directionTo(playerCenter);
        if (dir == null) return;

        final double px = -dir[1];
        final double py = dir[0];

        final double wave = Math.sin(aiTime * 6.0);
        final double sideFactor = 0.45;

        final double dx = dir[0] + px * wave * sideFactor;
        final double dy = dir[1] + py * wave * sideFactor;

        final double len = Math.hypot(dx, dy);
        if (len < EPSILON) return;

        dir[0] = dx / len;
        dir[1] = dy / len;

        applyJitter(dir, profile.jitter * 0.5);
        moveWithSlide(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveShooterKiting(EnemyProfile profile, double[] playerCenter, double dt) {
        final double dx = playerCenter[0] - getCenterX();
        final double dy = playerCenter[1] - getCenterY();

        final double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;

        final double dirX = dx / dist;
        final double dirY = dy / dist;

        final double minRange = 150.0;
        final double maxRange = 250.0;

        final double baseSpeed = (profile.speed < 10.0) ? 100.0 : profile.speed;
        double speed = baseSpeed * speedMultiplier;

        if (dist < minRange) {
            tmpDir[0] = -dirX;
            tmpDir[1] = -dirY;
        } else if (dist > maxRange) {
            tmpDir[0] = dirX;
            tmpDir[1] = dirY;
        } else {
            // Strafe lateral
            tmpDir[0] = -dirY;
            tmpDir[1] = dirX;
            speed *= 0.6;

            applyJitter(tmpDir, profile.jitter * 0.25);
            moveWithSlide(tmpDir, speed * dt);
            return;
        }

        applyJitter(tmpDir, profile.jitter);
        moveWithSlide(tmpDir, speed * dt);
    }

    private void moveTank(EnemyProfile profile, double[] playerCenter, double dt) {
        final double[] dir = directionTo(playerCenter);
        if (dir == null) return;

        final double hpRatio = maxHealth > 0.0 ? (hp / maxHealth) : 1.0;
        double speed = profile.speed * speedMultiplier;

        // Cuando baja de vida, acelera.
        if (hpRatio <= 0.5) speed *= 1.4;

        applyJitter(dir, profile.jitter);
        moveWithSlide(dir, speed * dt);
    }

    private void moveKamikaze(EnemyProfile profile, double[] playerCenter, double dt) {
        final double dx = playerCenter[0] - getCenterX();
        final double dy = playerCenter[1] - getCenterY();

        final double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;

        final double dirX = dx / dist;
        final double dirY = dy / dist;

        final double nearDist = 120.0;
        final double farDist = 260.0;

        final double factor;
        if (dist <= nearDist) factor = 1.6;
        else if (dist >= farDist) factor = 0.8;
        else {
            final double t = (dist - nearDist) / (farDist - nearDist);
            factor = 1.6 + (0.8 - 1.6) * t;
        }

        final double speed = profile.speed * speedMultiplier * factor;

        tmpDir[0] = dirX;
        tmpDir[1] = dirY;

        applyJitter(tmpDir, profile.jitter);
        moveWithSlide(tmpDir, speed * dt);
    }

    /**
     * Mueve aplicando “slide” sencillo: intenta mover X y luego Y, evitando intersecciones.
     * <p>
     * Mantiene el comportamiento original:
     * - Si colisiona en X, no aplica X.
     * - Si colisiona en Y, no aplica Y.
     * - Después clampa dentro de los límites del {@code boundsPane}.
     * </p>
     *
     * @param dir dirección normalizada (x,y)
     * @param distance distancia a avanzar este frame
     */
    private void moveWithSlide(double[] dir, double distance) {
        if (distance <= 0.0) return;

        final double deltaX = dir[0] * distance;
        final double deltaY = dir[1] * distance;

        double currX = viewRoot.getLayoutX();
        double currY = viewRoot.getLayoutY();

        if (!checkCollision(currX + deltaX, currY)) currX += deltaX;
        if (!checkCollision(currX, currY + deltaY)) currY += deltaY;

        final double maxX = Math.max(0.0, boundsPane.getWidth() - WIDTH);
        final double maxY = Math.max(0.0, boundsPane.getHeight() - HEIGHT);

        viewRoot.setLayoutX(clamp(currX, 0.0, maxX));
        viewRoot.setLayoutY(clamp(currY, 0.0, maxY));
    }

    /**
     * Comprueba colisión contra obstáculos (si existen).
     *
     * @param x posible X del enemigo
     * @param y posible Y del enemigo
     * @return true si colisionaría con algún obstáculo
     */
    private boolean checkCollision(double x, double y) {
        if (obstaclesSupplier == null) return false;

        final List<GameEntity> obstacles = obstaclesSupplier.get();
        if (obstacles == null || obstacles.isEmpty()) return false;

        final double margin = 2.0 * VISUAL_SCALE;
        final BoundingBox checkBounds = new BoundingBox(
                x + margin,
                y + margin,
                WIDTH - margin * 2,
                HEIGHT - margin * 2
        );

        for (GameEntity obs : obstacles) {
            if (obs.getBounds().intersects(checkBounds)) return true;
        }
        return false;
    }

    // =========================================================
    // Disparo
    // =========================================================

    private void handleDefaultShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        if (profile.fireRate <= 0.0 || profile.projSpeed <= 0.0 || profile.projRange <= 0.0) {
            timeSinceShot = 0.0;
            return;
        }

        timeSinceShot += dt;
        final double interval = 1.0 / profile.fireRate;

        if (timeSinceShot < interval) return;

        if (isValidTarget(playerCenter)) {
            timeSinceShot = 0.0;
            shootTowards(playerCenter, profile);
        } else {
            // Evita que crezca indefinidamente.
            timeSinceShot = interval;
        }
    }

    /**
     * Disparo especial del turret en ráfagas (burst).
     * <p>
     * Mantiene el comportamiento:
     * - Cada ráfaga tiene {@code burstSize} tiros, separados por {@code perShotDelay}.
     * - El cooldown entre ráfagas depende de {@code fireRate}.
     * - Si {@code fireRate} es 0, el intervalo es infinito y no disparará.
     * </p>
     *
     * @param profile perfil del enemigo
     * @param playerCenter centro del jugador
     * @param dt delta time
     */
    private void handleTurretShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        final double projSpeed = Math.max(0.0, profile.projSpeed);
        if (projSpeed <= 0.0) {
            burstShotsRemaining = 0;
            return;
        }

        final double burstInterval = 1.0 / profile.fireRate;
        final double perShotDelay = 0.1;
        final int burstSize = 3;

        if (burstShotsRemaining > 0) {
            burstShotTimer += dt;
            if (burstShotTimer >= perShotDelay) {
                burstShotTimer = 0.0;
                fireBurstShot(playerCenter, profile);
            }
            return;
        }

        if (!Double.isFinite(burstInterval)) return;

        burstCooldownTimer += dt;
        if (burstCooldownTimer >= burstInterval && isValidTarget(playerCenter)) {
            burstCooldownTimer = 0.0;
            burstShotsRemaining = burstSize;
            burstShotTimer = 0.0;
            fireBurstShot(playerCenter, profile);
        }
    }

    private void fireBurstShot(double[] playerCenter, EnemyProfile profile) {
        if (burstShotsRemaining <= 0) return;

        burstShotsRemaining--;

        if (isValidTarget(playerCenter)) {
            shootTowards(playerCenter, profile);
        }

        if (burstShotsRemaining <= 0) {
            burstShotTimer = 0.0;
        }
    }

    private void shootTowards(double[] target, EnemyProfile profile) {
        final double[] dir = directionTo(target);
        if (dir == null) return;

        final double projSpeed = Math.max(0.0, profile.projSpeed);
        final double projRange = Math.max(0.0, profile.projRange);
        final double projDamage = Math.max(0.0, profile.projDamage * damageMultiplier);

        if (projSpeed <= 0.0 || projRange <= 0.0 || projDamage <= 0.0) return;

        final double lifetime = projRange / projSpeed;

        Projectile projectile = new Projectile(
                dir[0], dir[1],
                projSpeed, lifetime, projDamage,
                true,
                boundsPane,
                onRemove,
                this,
                type.name()
        );

        final double projOffset = 4.0 * VISUAL_SCALE;
        projectile.getView().setLayoutX(getCenterX() - projOffset);
        projectile.getView().setLayoutY(getCenterY() - projOffset);

        onSpawn.accept(projectile);
    }

    // =========================================================
    // Utilidades / vida / FX
    // =========================================================

    /**
     * Sincroniza {@code maxHealth} y {@code hp} con el perfil actual.
     * <p>
     * Mantiene el comportamiento original:
     * - Recalcula el máximo si cambia el perfil o el multiplicador.
     * - Conserva el ratio de vida actual al ajustar el máximo.
     * - Si la vida resultante cae a 0, ejecuta {@link #die()}.
     * </p>
     *
     * @param profile perfil actual del enemigo
     */
    private void syncHealthWithProfile(EnemyProfile profile) {
        final double desiredMax = Math.max(0.0, profile.baseHp * hpMultiplier);

        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            final double ratio = maxHealth > 0.0 ? (hp / maxHealth) : 1.0;
            maxHealth = desiredMax;
            hp = Math.min(maxHealth, Math.max(0.0, ratio * maxHealth));
            if (hp <= 0.0) die();
        } else if (hp > maxHealth) {
            hp = maxHealth;
        }
    }

    /**
     * Calcula una dirección normalizada desde el enemigo hacia un objetivo.
     * Reutiliza {@link #tmpDir} para evitar allocs.
     *
     * @param target objetivo {x,y}
     * @return dirección normalizada {x,y} o null si no es válida
     */
    private double[] directionTo(double[] target) {
        if (!isValidTarget(target)) return null;

        final double cx = getCenterX();
        final double cy = getCenterY();

        final double dx = target[0] - cx;
        final double dy = target[1] - cy;

        final double len = Math.hypot(dx, dy);
        if (len < EPSILON) return null;

        tmpDir[0] = dx / len;
        tmpDir[1] = dy / len;
        return tmpDir;
    }

    /**
     * Aplica “jitter” a una dirección normalizada.
     * <p>
     * {@code jitterPercent} se interpreta como porcentaje (0..100), pero se clampa a 1.0 como máximo.
     * Al final se re-normaliza el vector.
     * </p>
     *
     * @param dir dirección a modificar (se modifica in-place)
     * @param jitterPercent intensidad del jitter (porcentaje)
     */
    private void applyJitter(double[] dir, double jitterPercent) {
        final double magnitude = Math.max(0.0, Math.min(1.0, jitterPercent * 0.01));
        if (magnitude <= 0.0) return;

        final double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        dir[0] += Math.cos(angle) * magnitude;
        dir[1] += Math.sin(angle) * magnitude;

        final double len = Math.hypot(dir[0], dir[1]);
        if (len < EPSILON) {
            dir[0] = 0.0;
            dir[1] = 1.0;
            return;
        }

        dir[0] /= len;
        dir[1] /= len;
    }

    /**
     * Valida si un “centro” tiene al menos dos componentes (x,y).
     *
     * @param playerCenter array que se espera {x,y}
     * @return true si es válido
     */
    private static boolean isValidTarget(double[] playerCenter) {
        return playerCenter != null && playerCenter.length >= 2;
    }

    /**
     * Marca el enemigo como muerto, reproduce SFX, spawnea FX y notifica el remove.
     */
    private void die() {
        if (dead) return;

        dead = true;
        hp = 0.0;

        playSfx.accept("enemy_death");
        spawnDeathFx();
        onRemove.accept(this);
    }

    /**
     * Flash visual al recibir daño.
     * Usa un {@link PauseTransition} reutilizable para limpiar el efecto tras un corto periodo.
     */
    private void flashHit() {
        if (hitFlashTimer == null) {
            hitFlashTimer = new PauseTransition(Duration.millis(120));
            hitFlashTimer.setOnFinished(e -> {
                debugBox.setStroke(Color.TRANSPARENT);
                spriteView.setEffect(null);
            });
        } else {
            hitFlashTimer.stop();
        }

        debugBox.setStroke(Color.WHITE);
        spriteView.setEffect(hitFlashEffect);
        hitFlashTimer.playFromStart();
    }

    /**
     * Efecto simple de muerte: círculo que aparece, se agranda y se desvanece.
     * Se crea como {@link GameEntity} temporal para que el mundo lo gestione y pueda eliminarse al finalizar.
     */
    private void spawnDeathFx() {
        Circle fx = new Circle(6 * VISUAL_SCALE, Color.ORANGERED);
        fx.setManaged(false);
        fx.setLayoutX(getCenterX());
        fx.setLayoutY(getCenterY());

        GameEntity fxEntity = new GameEntity() {
            @Override public void update(double dt) { }
            @Override public Node getView() { return fx; }
        };

        onSpawn.accept(fxEntity);

        FadeTransition fade = new FadeTransition(Duration.millis(250), fx);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(250), fx);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.8);
        scale.setToY(1.8);

        ParallelTransition pt = new ParallelTransition(fx, fade, scale);
        pt.setOnFinished(e -> onRemove.accept(fxEntity));
        pt.play();
    }

    /**
     * Clamp básico.
     *
     * @param v valor
     * @param min mínimo
     * @param max máximo
     * @return valor clamped
     */
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }
}
