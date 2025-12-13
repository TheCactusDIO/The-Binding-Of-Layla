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

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public final class Player implements GameEntity {

    private static final double TAU_ACCEL    = 0.035;
    private static final double TAU_DECEL    = 0.090;
    private static final double TAU_REVERSE  = 0.045;

    // --- CONFIGURACIÓN DEL SPRITE ---
    private static final int FRAME_W = 32;
    private static final int FRAME_H = 32;
    private static final int COLUMNS_IN_SHEET = 10;

    // Filas
    private static final int ROW_HEAD = 0;
    private static final int ROW_WALK_DOWN = 1;
    private static final int ROW_WALK_SIDE = 2;
    private static final int ROW_WALK_UP = 3;

    // Frames Cabeza
    private static final int HEAD_IDX_AIM_DOWN   = 0;
    private static final int HEAD_IDX_SHOOT_DOWN = 1;
    private static final int HEAD_IDX_AIM_SIDE   = 2;
    private static final int HEAD_IDX_SHOOT_SIDE = 3;
    private static final int HEAD_IDX_AIM_UP     = 4;
    private static final int HEAD_IDX_SHOOT_UP   = 5;

    // --- AJUSTES VISUALES ---
    // Hitbox lógica (física) reducida para evitar colisiones "falsas" con el aire del sprite
    private static final double HITBOX_SIZE = 24.0;
    // Tamaño visual del sprite
    private static final double SPRITE_SIZE = 48.0;

    // Offset para centrar el sprite (48px) sobre la hitbox (24px)
    // (24 - 48) / 2 = -12
    private static final double CENTER_OFFSET = (HITBOX_SIZE - SPRITE_SIZE) / 2.0;

    // Offset vertical específico de la cabeza respecto al cuerpo
    private static final double HEAD_OFFSET_Y = -12.0;

    // Lógica de disparo
    private static final double SHOOT_FACE_COOLDOWN = 0.25;
    private double lastShootTime = 99.0;
    private int latchedShootDir = -1;
    private int currentShootDir = -1;

    // View Components
    private final StackPane viewRoot = new StackPane();
    // La caja física (Hitbox) - Transparente pero define el tamaño del GameEntity
    private final Rectangle debugBox = new Rectangle(HITBOX_SIZE, HITBOX_SIZE, Color.TRANSPARENT);

    private final ImageView bodyView = new ImageView();
    private final ImageView headView = new ImageView();

    private final SpriteAnimator bodyAnimator;
    private final boolean hasSprite;

    private final Supplier<double[]> moveSupplier;
    private final InputService inputService;
    private final Pane boundsPane;
    private final StatsService statsService;

    private double vx;
    private double vy;

    private int moveDir = 0; // 0=Abajo, 1=Derecha, 2=Arriba, 3=Izquierda

    private boolean isShootingFrame = false;
    private double shootFrameTimer = 0.0;

    private double health = 6.0;
    private double maxHealth = 6.0;
    private boolean dead = false;

    private double invulnTimer = 0.0;
    private static final double INVULN_DURATION = 1.0;

    private final Consumer<String> playSfx;
    private String lastHitSource = null;

    public Player(InputService input, Pane boundsPane, StatsService statsService) {
        this(input, boundsPane, statsService, null);
    }

    private Player(InputService input, Pane boundsPane, StatsService statsService, Consumer<String> playSfx) {
        this.inputService = Objects.requireNonNull(input, "input");
        this.moveSupplier = input::getMoveVector;
        this.boundsPane   = Objects.requireNonNull(boundsPane, "boundsPane");
        this.statsService = (statsService != null) ? statsService : com.layla.AppContext.stats();
        this.playSfx      = (playSfx != null ? playSfx : k -> {});
        this.invulnTimer = 1.0;

        debugBox.setStroke(Color.BLACK);
        debugBox.setStrokeWidth(1);
        // Si quieres ver la hitbox real para depurar, cambia esto a Color.RED
        debugBox.setFill(Color.TRANSPARENT);

        Image sheet = AssetsManager.loadImage("assets/images/player_sheet.png");
        if (sheet == null) {
            hasSprite = false;
            debugBox.setFill(Color.CYAN);
            bodyAnimator = new SpriteAnimator(1, 1, 1, 1, 1);
        } else {
            hasSprite = true;
            debugBox.setStroke(Color.TRANSPARENT);

            // Configurar vistas
            setupImageView(bodyView, sheet);
            setupImageView(headView, sheet);

            // IMPORTANTE: Unmanaged para que el StackPane no crezca al tamaño de la imagen
            bodyView.setManaged(false);
            headView.setManaged(false);

            // Centrar manualmente las imágenes respecto a la hitbox (0,0 es esquina sup izq de hitbox)
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

        // Forzar tamaño del root al de la hitbox
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

    public void setLastHitSource(String source) { this.lastHitSource = source; }
    public String getLastHitSource() { return lastHitSource; }

    @Override
    public void update(double dt) {
        if (dt <= 0 || dead) return;

        if (invulnTimer > 0.0) invulnTimer = Math.max(0.0, invulnTimer - dt);
        viewRoot.setOpacity(invulnTimer > 0 && (invulnTimer % 0.15 > 0.07) ? 0.4 : 1.0);

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
        if (speed > maxSpeed && speed > 0) {
            double s = maxSpeed / speed;
            vx *= s;
            vy *= s;
        }

        double nextX = viewRoot.getLayoutX() + vx * dt;
        double nextY = viewRoot.getLayoutY() + vy * dt;

        double maxX = Math.max(0.0, boundsPane.getWidth()  - getWidth());
        double maxY = Math.max(0.0, boundsPane.getHeight() - getHeight());

        if (nextX < 0.0) nextX = 0.0; else if (nextX > maxX) nextX = maxX;
        if (nextY < 0.0) nextY = 0.0; else if (nextY > maxY) nextY = maxY;

        viewRoot.setLayoutX(nextX);
        viewRoot.setLayoutY(nextY);
    }

    private void updateAnimation(double dt) {
        if (!hasSprite) return;

        double[] aim = inputService.getAimArrowCardinal();
        boolean isShootingInput = (aim[0] != 0 || aim[1] != 0);

        int newShootDir = -1;
        if (isShootingInput) {
            if (Math.abs(aim[0]) > Math.abs(aim[1])) {
                newShootDir = (aim[0] > 0) ? 1 : 3;
            } else {
                newShootDir = (aim[1] > 0) ? 0 : 2;
            }
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
                if (shootFrameTimer <= 0) {
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

        boolean moving = Math.abs(vx) > 5.0 || Math.abs(vy) > 5.0;
        if (moving) {
            if (Math.abs(vx) > Math.abs(vy)) moveDir = (vx > 0) ? 1 : 3;
            else moveDir = (vy > 0) ? 0 : 2;
        }

        int headDir = showShootFace ? latchedShootDir : moveDir;
        if (headDir == -1) headDir = moveDir;

        // --- ANIMAR CUERPO ---
        int bodyRowTarget = ROW_WALK_DOWN;
        int bodyFrames = 8;
        boolean bodyFlip = false;

        switch (moveDir) {
            case 0: bodyRowTarget = ROW_WALK_DOWN; bodyFrames = 8; break;
            case 2: bodyRowTarget = ROW_WALK_UP;   bodyFrames = 2; break;
            case 1: bodyRowTarget = ROW_WALK_SIDE; bodyFrames = 8; bodyFlip = false; break;
            case 3: bodyRowTarget = ROW_WALK_SIDE; bodyFrames = 8; bodyFlip = true;  break;
        }

        if (moving) {
            bodyAnimator.setAnimationConfig(bodyRowTarget, 0, bodyFrames, true);
            bodyAnimator.update(dt);
        } else {
            bodyAnimator.setAnimationConfig(bodyRowTarget, 0, 1, true);
        }

        bodyView.setScaleX(bodyFlip ? -1 : 1);
        bodyView.setViewport(bodyAnimator.getCurrentViewport());

        // --- ANIMAR CABEZA ---
        int headFrameIdx = HEAD_IDX_AIM_DOWN;
        boolean headFlip = false;

        boolean useShootFrame = isShootingInput && isShootingFrame;

        switch (headDir) {
            case 0: headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_DOWN : HEAD_IDX_AIM_DOWN; break;
            case 2: headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_UP   : HEAD_IDX_AIM_UP;   break;
            case 1: headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_SIDE : HEAD_IDX_AIM_SIDE; headFlip = false; break;
            case 3: headFrameIdx = useShootFrame ? HEAD_IDX_SHOOT_SIDE : HEAD_IDX_AIM_SIDE; headFlip = true;  break;
        }

        headView.setScaleX(headFlip ? -1 : 1);
        headView.setViewport(new Rectangle2D(headFrameIdx * FRAME_W, ROW_HEAD * FRAME_H, FRAME_W, FRAME_H));
    }

    private static double pickTau(double v, double tv) {
        if (tv == 0.0) return TAU_DECEL;
        if (Math.signum(v) != Math.signum(tv) && v != 0.0) return TAU_REVERSE;
        return TAU_ACCEL;
    }

    @Override public Node getView() { return viewRoot; }
    @Override public Bounds getBounds() { return viewRoot.getBoundsInParent(); }

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
            if (playSfx != null) playSfx.accept("hurt");
        }
    }

    private void die() {
        if (dead) return;
        dead = true;
        health = 0.0;
        if (playSfx != null) playSfx.accept("dead");
        viewRoot.setOpacity(0.5);
        viewRoot.setRotate(90);
    }

    private void syncMaxHealthFromStats() {
        double desiredMax = statsService.getStat(PlayerStatId.MAX_HEALTH);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            setMaxHealth(desiredMax);
        }
    }
}
