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
 * Entidad del jugador.
 * <ul>
 *   <li>Movimiento suavizado (aceleración/deceleración) con velocidad máxima desde {@link StatsService}.</li>
 *   <li>Render con sprite sheet (cuerpo + cabeza) y animación.</li>
 *   <li>Hitbox separada del tamaño visual del sprite.</li>
 *   <li>Vida, invulnerabilidad temporal tras recibir daño y feedback visual (parpadeo).</li>
 * </ul>
 */
public final class Player implements GameEntity {

    private static final double TAU_ACCEL   = 0.035;
    private static final double TAU_DECEL   = 0.090;
    private static final double TAU_REVERSE = 0.045;

    /** Escala global del jugador (sprite + hitbox). */
    private static final double VISUAL_SCALE = 1.2;

    // Sprite sheet config
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;
    private static final int COLUMNS_IN_SHEET = 10;

    // Filas
    private static final int ROW_HEAD = 0;
    private static final int ROW_WALK_DOWN = 1;
    private static final int ROW_WALK_SIDE = 2;
    private static final int ROW_WALK_UP = 3;

    // Frames cabeza
    private static final int HEAD_IDX_AIM_DOWN   = 0;
    private static final int HEAD_IDX_SHOOT_DOWN = 1;
    private static final int HEAD_IDX_AIM_SIDE   = 2;
    private static final int HEAD_IDX_SHOOT_SIDE = 3;
    private static final int HEAD_IDX_AIM_UP     = 4;
    private static final int HEAD_IDX_SHOOT_UP   = 5;

    // Hitbox y sprite
    private static final double HITBOX_SIZE = 24.0 * VISUAL_SCALE;
    private static final double SPRITE_SIZE = 48.0 * VISUAL_SCALE;
    private static final double CENTER_OFFSET = (HITBOX_SIZE - SPRITE_SIZE) / 2.0;
    private static final double HEAD_OFFSET_Y = -12.0 * VISUAL_SCALE;

    // Shooting face
    private static final double SHOOT_FACE_COOLDOWN = 0.25;
    private double lastShootTime = 99.0;
    private int latchedShootDir = -1;
    private int currentShootDir = -1;

    private static final double INVULN_DURATION = 1.0;
    private static final Consumer<String> NO_OP_SFX = k -> {};

    // View
    private final StackPane viewRoot = new StackPane();
    private final Rectangle debugBox = new Rectangle(HITBOX_SIZE, HITBOX_SIZE, Color.TRANSPARENT);
    private final ImageView bodyView = new ImageView();
    private final ImageView headView = new ImageView();

    private final SpriteAnimator bodyAnimator;
    private final boolean hasSprite;

    // Inputs / deps
    private final Supplier<double[]> moveSupplier;
    private final InputService inputService;
    private final Pane boundsPane;
    private final StatsService statsService;
    private final Consumer<String> playSfx;

    // Insets de mundo (paredes invisibles)
    private double worldInsetLeft   = 0.0;
    private double worldInsetRight  = 0.0;
    private double worldInsetTop    = 0.0;
    private double worldInsetBottom = 0.0;

    // Movement state
    private double vx;
    private double vy;
    private int moveDir = 0; // 0=Abajo, 1=Derecha, 2=Arriba, 3=Izquierda

    // Head animation state
    private boolean isShootingFrame = false;
    private double shootFrameTimer = 0.0;

    // Health
    private double health = 6.0;
    private double maxHealth = 6.0;
    private boolean dead = false;
    private double invulnTimer = 0.0;

    private String lastHitSource = null;

    public Player(InputService input, Pane boundsPane, StatsService statsService) {
        this(input, boundsPane, statsService, null);
    }

    /**
     * @param input servicio de input (movimiento y dirección de disparo)
     * @param boundsPane pane usado como límites del mundo (clamp del movimiento)
     * @param statsService servicio de stats (velocidad, vida máxima, etc.). Si es null, usa AppContext.stats().
     * @param playSfx callback para reproducir SFX por key (si es null, se ignora)
     */
    public Player(InputService input, Pane boundsPane, StatsService statsService, Consumer<String> playSfx) {
        this.inputService = Objects.requireNonNull(input, "input");
        this.moveSupplier = input::getMoveVector;
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.statsService = (statsService != null) ? statsService : com.layla.AppContext.stats();
        this.playSfx = (playSfx != null) ? playSfx : NO_OP_SFX;

        // Arranca con un pelín de invulnerabilidad (como buffer de spawn)
        this.invulnTimer = 1.0;

        debugBox.setStroke(Color.BLACK);
        debugBox.setStrokeWidth(1);
        debugBox.setFill(Color.TRANSPARENT);

        Image sheet = AssetsManager.loadImage("assets/images/player_sheet.png");
        if (sheet == null) {
            hasSprite = false;
            debugBox.setFill(Color.CYAN);
            bodyAnimator = new SpriteAnimator(1, 1, 1, 1, 1);
        } else {
            hasSprite = true;
            debugBox.setStroke(Color.TRANSPARENT);

            setupImageView(bodyView, sheet);
            setupImageView(headView, sheet);

            bodyView.setManaged(false);
            headView.setManaged(false);

            bodyView.setLayoutX(CENTER_OFFSET);
            bodyView.setLayoutY(CENTER_OFFSET);

            headView.setLayoutX(CENTER_OFFSET);
            headView.setLayoutY(CENTER_OFFSET + HEAD_OFFSET_Y);

            bodyAnimator = new SpriteAnimator(FRAME_W, FRAME_H, 8, 12, COLUMNS_IN_SHEET);

            Rectangle2D initialRect = new Rectangle2D(0, 0, FRAME_W, FRAME_H);
            bodyView.setViewport(initialRect);
            headView.setViewport(initialRect);
        }

        viewRoot.getChildren().addAll(debugBox, bodyView, headView);
        viewRoot.setLayoutX(200);
        viewRoot.setLayoutY(200);

        viewRoot.setMinSize(HITBOX_SIZE, HITBOX_SIZE);
        viewRoot.setMaxSize(HITBOX_SIZE, HITBOX_SIZE);

        boundsPane.getChildren().add(viewRoot);
    }

    private void setupImageView(ImageView v, Image img) {
        v.setImage(img);
        v.setFitWidth(SPRITE_SIZE);
        v.setFitHeight(SPRITE_SIZE);
        v.setSmooth(false);
        v.setPreserveRatio(true);
    }

    public void setLastHitSource(String source) {
        this.lastHitSource = source;
    }

    public String getLastHitSource() {
        return lastHitSource;
    }

    /**
     * Mantiene al jugador dentro de un rectángulo interior, dejando "paredes" invisibles.
     * Los valores negativos se clipean a 0.
     */
    public void setWorldInset(double left, double right, double top, double bottom) {
        this.worldInsetLeft = Math.max(0.0, left);
        this.worldInsetRight = Math.max(0.0, right);
        this.worldInsetTop = Math.max(0.0, top);
        this.worldInsetBottom = Math.max(0.0, bottom);
    }

    public double getX() { return viewRoot.getLayoutX(); }
    public double getY() { return viewRoot.getLayoutY(); }
    public double getCenterX() { return getX() + HITBOX_SIZE * 0.5; }
    public double getCenterY() { return getY() + HITBOX_SIZE * 0.5; }

    @Override
    public void update(double dt) {
        if (dt <= 0.0 || dead) {
            return;
        }

        if (invulnTimer > 0.0) {
            invulnTimer = Math.max(0.0, invulnTimer - dt);
        }
        viewRoot.setOpacity(invulnTimer > 0.0 && (invulnTimer % 0.15 > 0.07) ? 0.4 : 1.0);

        syncMaxHealthFromStats();
        handleMovement(dt);
        updateAnimation(dt);
    }

    private void handleMovement(double dt) {
        double[] mv = moveSupplier.get();
        double maxSpeed = statsService.getStat(PlayerStatId.MOVE_SPEED);

        double targetVx = mv[0] * maxSpeed;
        double targetVy = mv[1] * maxSpeed;

        double tauX = pickTau(vx, targetVx);
        double tauY = pickTau(vy, targetVy);

        double ax = 1.0 - Math.exp(-dt / tauX);
        double ay = 1.0 - Math.exp(-dt / tauY);

        vx += (targetVx - vx) * ax;
        vy += (targetVy - vy) * ay;

        double speed = Math.hypot(vx, vy);
        if (speed > maxSpeed && speed > 0.0) {
            double s = maxSpeed / speed;
            vx *= s;
            vy *= s;
        }

        double nextX = viewRoot.getLayoutX() + vx * dt;
        double nextY = viewRoot.getLayoutY() + vy * dt;

        // Clamp con insets
        double minX = worldInsetLeft;
        double minY = worldInsetTop;

        double maxX = Math.max(minX, boundsPane.getWidth() - getWidth() - worldInsetRight);
        double maxY = Math.max(minY, boundsPane.getHeight() - getHeight() - worldInsetBottom);

        if (nextX < minX) nextX = minX; else if (nextX > maxX) nextX = maxX;
        if (nextY < minY) nextY = minY; else if (nextY > maxY) nextY = maxY;

        viewRoot.setLayoutX(nextX);
        viewRoot.setLayoutY(nextY);
    }

    private void updateAnimation(double dt) {
        if (!hasSprite) return;

        double[] aim = inputService.getAimArrowCardinal();
        boolean isShootingInput = (aim[0] != 0.0 || aim[1] != 0.0);

        int newShootDir = -1;
        if (isShootingInput) {
            if (Math.abs(aim[0]) > Math.abs(aim[1])) newShootDir = (aim[0] > 0) ? 1 : 3;
            else newShootDir = (aim[1] > 0) ? 0 : 2;
        }

        if (isShootingInput) {
            lastShootTime = 0.0;
            latchedShootDir = newShootDir;

            if (newShootDir != currentShootDir) {
                currentShootDir = newShootDir;
                isShootingFrame = true;
                shootFrameTimer = 0.15;
            } else {
                shootFrameTimer -= dt;
                if (shootFrameTimer <= 0.0) {
                    isShootingFrame = !isShootingFrame;
                    shootFrameTimer = 0.15;
                }
            }
        } else {
            currentShootDir = -1;
            lastShootTime += dt;
            isShootingFrame = false;
            shootFrameTimer = 0.0;
        }

        boolean showShootFace = (lastShootTime < SHOOT_FACE_COOLDOWN);

        boolean moving = Math.abs(vx) > (5.0 * VISUAL_SCALE) || Math.abs(vy) > (5.0 * VISUAL_SCALE);
        if (moving) {
            if (Math.abs(vx) > Math.abs(vy)) moveDir = (vx > 0) ? 1 : 3;
            else moveDir = (vy > 0) ? 0 : 2;
        }

        int headDir = showShootFace ? latchedShootDir : moveDir;
        if (headDir == -1) headDir = moveDir;

        // ---- Cuerpo ----
        int bodyRowTarget = ROW_WALK_DOWN;
        int bodyFrames = 8;
        boolean bodyFlip = false;

        switch (moveDir) {
            case 0 -> { bodyRowTarget = ROW_WALK_DOWN; bodyFrames = 8; }
            case 2 -> { bodyRowTarget = ROW_WALK_UP;   bodyFrames = 2; }
            case 1 -> { bodyRowTarget = ROW_WALK_SIDE; bodyFrames = 8; bodyFlip = false; }
            case 3 -> { bodyRowTarget = ROW_WALK_SIDE; bodyFrames = 8; bodyFlip = true; }
        }

        if (moving) {
            bodyAnimator.setAnimationConfig(bodyRowTarget, 0, bodyFrames, true);
            bodyAnimator.update(dt);
        } else {
            bodyAnimator.setAnimationConfig(bodyRowTarget, 0, 1, true);
        }

        bodyView.setScaleX(bodyFlip ? -1 : 1);
        bodyView.setViewport(bodyAnimator.getCurrentViewport());

        // ---- Cabeza ----
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

    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE;
        return TAU_ACCEL;
    }

    @Override
    public Node getView() {
        return viewRoot;
    }

    /**
     * Hitbox lógica (no usa bounds visuales porque la cabeza sale fuera).
     */
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

    private void die() {
        if (dead) return;

        dead = true;
        health = 0.0;

        playSfx.accept("player_death");

        viewRoot.setOpacity(0.5);
        viewRoot.setRotate(90.0);
    }

    private void syncMaxHealthFromStats() {
        double desiredMax = statsService.getStat(PlayerStatId.MAX_HEALTH);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            setMaxHealth(desiredMax);
        }
    }
}
