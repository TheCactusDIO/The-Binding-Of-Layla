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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class Enemy implements GameEntity {

    // ===== Size/scale (OPTION 2: visual + hitbox) =====
    private static final double VISUAL_SCALE = 1.5;

    private static final double WIDTH  = 22.0 * VISUAL_SCALE;
    private static final double HEIGHT = 22.0 * VISUAL_SCALE;
    private static final double EPSILON = 1e-6;

    private final EnemyType type;

    // View components
    private final StackPane viewRoot = new StackPane();
    private final Rectangle debugBox = new Rectangle(WIDTH, HEIGHT);
    private final ImageView spriteView = new ImageView();
    private final ImageView headView = new ImageView();
    private final SpriteAnimator animator;
    private final boolean hasSprite;

    private final Pane boundsPane;
    private final Supplier<double[]> playerCenterSupplier;
    private final Supplier<List<GameEntity>> obstaclesSupplier;
    private final Consumer<GameEntity> onRemove;
    private final Consumer<GameEntity> onSpawn;
    private final Consumer<String> playSfx;

    private final double hpMultiplier;
    private final double speedMultiplier;
    private final double damageMultiplier;

    private double hp;
    private double maxHealth;
    private double timeSinceShot = 0.0;
    private double aiTime = 0.0;
    private boolean dead = false;
    private PauseTransition hitFlashTimer;

    private int burstShotsRemaining = 0;
    private double burstShotTimer = 0.0;
    private double burstCooldownTimer = 0.0;

    // Anti-Stuck Logic
    private double stuckTimer = 0.0;
    private double[] stuckDirection = {0,0};
    private double lastX, lastY;

    private final double[] tmpDir = new double[2];
    private final double collisionRadius = Math.min(WIDTH, HEIGHT) * 0.5;

    // ===================== SHOOTER (32x32) =====================
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

    // ===================== MELEE (melee.png) =====================
    private static final int MELEE_FRAME_W = 31;
    private static final int MELEE_HEAD_H = 25;
    private static final int MELEE_LEGS_H = 15;

    private static final int MELEE_Y_HEAD = 1;
    private static final int MELEE_Y_LEGS_DOWN = 30;
    private static final int MELEE_Y_LEGS_SIDE = 46;

    private static final int MELEE_WALK_FRAMES = 6;
    private static final int MELEE_WALK_DOWN_START_COL = 0; // cols 1..6 => 0..5
    private static final int MELEE_WALK_SIDE_START_COL = 2; // cols 3..8 => 2..7

    private static final double MELEE_HEAD_Y_OFFSET = -14.0 * VISUAL_SCALE;
    private static final double MELEE_HEAD_X_OFFSET = -4.0 * VISUAL_SCALE;

    private static final double MELEE_FPS = 10.0;

    // ===================== KAMIKAZE (kamikaze.png) =====================
    private static final int KAMIKAZE_FRAME_W = 48;
    private static final int KAMIKAZE_FRAME_H = 48;
    private static final int KAMIKAZE_X0 = 0;
    private static final int KAMIKAZE_Y0 = 0;

    private static final int KAMIKAZE_ROW_DOWN_UP = 0;
    private static final int KAMIKAZE_ROW_RIGHT_LEFT = 1;

    private static final int KAMIKAZE_COL_1 = 0; // Cara 1 (1-based) => 0
    private static final int KAMIKAZE_COL_4 = 3; // Cara 4 (1-based) => 3

    private int kamikazeActiveRow = -1;
    private int kamikazeActiveCol = -1;

    // ===================== TANK (tank.png) =====================
    // IMPORTANTE: el cuerpo REAL del tank ocupa 2x2 tiles de 32px => 64x64 por frame.
    // Head: row 0 col 0 (32x32)
    // Body right: top-left en row 1, x = frame*64, y = 1*32, w=64, h=64  (usa rows 1 y 2)
    // Body down:  top-left en row 3, x = frame*64, y = 3*32, w=64, h=64  (usa rows 3 y 4)
    private static final int TANK_TILE = 32;

    private static final int TANK_HEAD_W = 32;
    private static final int TANK_HEAD_H = 32;

    private static final int TANK_BODY_W = 64; // 2 tiles
    private static final int TANK_BODY_H = 64; // 2 tiles (para que salgan las “piernas”)

    private static final int TANK_ROW_HEAD = 0;
    private static final int TANK_ROW_BODY_RIGHT_TOP = 1;
    private static final int TANK_ROW_BODY_DOWN_TOP  = 3;

    private static final int TANK_WALK_FRAMES = 6;
    private static final double TANK_FPS = 10.0;

    // Ajustes que tú puedes tocar:
    // - BODY_X/Y mueve el cuerpo entero.
    // - HEAD_X/Y ajusta la cabeza encima del cuerpo.
    // Nota: la cabeza la coloco relativa al cuerpo, así no “flota”.
    private static final double TANK_BODY_X_OFFSET = 0.0 * VISUAL_SCALE;
    private static final double TANK_BODY_Y_OFFSET = 0.0 * VISUAL_SCALE;

    // separación vertical entre body y head (si la cabeza queda muy alta/baja, toca esto)
    private static final double TANK_HEAD_GAP_Y = 18.0 * VISUAL_SCALE;

    private static final double TANK_HEAD_X_OFFSET = 0.0 * VISUAL_SCALE;
    private static final double TANK_HEAD_Y_OFFSET = 20.0 * VISUAL_SCALE;

    private double tankFrameTimer = 0.0;
    private int tankFrameIdx = 0;
    private int tankActiveBodyRow = -1;
    private boolean tankActiveFlip = false;

    // ===================== Animation timers/caches =====================
    private double meleeFrameTimer = 0.0;
    private int meleeFrameIdx = 0;
    private int meleeActiveLegsY = -1;
    private int meleeActiveStartCol = -1;
    private boolean meleeActiveFlip = false;
    private int meleeActiveHeadCol = -1;

    // ===================== Shared humanoid facing smoothing =====================
    private enum Facing { DOWN, UP, RIGHT, LEFT }
    private Facing facing = Facing.DOWN;

    private boolean movingAnim = false;
    private static final double MOVE_START = 0.35;
    private static final double MOVE_STOP  = 0.20;

    private static final double FACE_DEADZONE = 6.0 * VISUAL_SCALE;
    private static final double FACING_SWITCH_DELAY = 0.06;
    private Facing pendingFacing = Facing.DOWN;
    private double pendingFacingTime = 0.0;

    private double lastAnimCx = Double.NaN;
    private double lastAnimCy = Double.NaN;

    // Shooter caches
    private int activeLegsRow = -1;
    private int activeLegsFrames = -1;
    private boolean activeLoop = false;
    private boolean activeFlip = false;
    private int activeHeadCol = -1;

    public Enemy(EnemyType type,
                 Pane boundsPane,
                 Supplier<double[]> playerCenterSupplier,
                 Supplier<List<GameEntity>> obstaclesSupplier,
                 Consumer<GameEntity> onRemove,
                 Consumer<GameEntity> onSpawn,
                 Consumer<String> playSfx,
                 double hpMultiplier,
                 double speedMultiplier,
                 double damageMultiplier) {

        this.type = Objects.requireNonNull(type, "type");
        this.boundsPane = Objects.requireNonNull(boundsPane, "boundsPane");
        this.playerCenterSupplier = Objects.requireNonNull(playerCenterSupplier, "playerCenterSupplier");
        this.obstaclesSupplier = obstaclesSupplier;
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
        this.onSpawn = Objects.requireNonNull(onSpawn, "onSpawn");
        this.playSfx = (playSfx != null ? playSfx : k -> {});

        this.hpMultiplier = Math.max(0.0, hpMultiplier);
        this.speedMultiplier = Math.max(0.0, speedMultiplier);
        this.damageMultiplier = Math.max(0.0, damageMultiplier);

        debugBox.setStroke(Color.BLACK);
        debugBox.setFill(getColorForType(type));

        Image sheet;

        if (type == EnemyType.SHOOTER) {
            sheet = AssetsManager.loadImage("assets/images/shooter.png");
        } else if (type == EnemyType.MELEE) {
            sheet = AssetsManager.loadImage("assets/images/melee.png");
        } else if (type == EnemyType.KAMIKAZE) {
            sheet = AssetsManager.loadImage("assets/images/kamikaze.png");
        } else if (type == EnemyType.TANK) {
            sheet = AssetsManager.loadImage("assets/images/tank.png");
        } else {
            sheet = AssetsManager.loadImage("assets/images/enemies_sheet.png");
        }

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

            debugBox.setFill(Color.TRANSPARENT);
            debugBox.setStroke(Color.TRANSPARENT);

            if (type == EnemyType.SHOOTER) {
                spriteView.setFitWidth(SHOOTER_FRAME_W * VISUAL_SCALE);
                spriteView.setFitHeight(SHOOTER_FRAME_H * VISUAL_SCALE);

                headView.setFitWidth(SHOOTER_FRAME_W * VISUAL_SCALE);
                headView.setFitHeight(SHOOTER_FRAME_H * VISUAL_SCALE);

                headView.setVisible(true);

                headView.setTranslateY(SHOOTER_HEAD_Y_OFFSET);
                applyShooterHeadOffsets(false);
                headView.setViewport(new Rectangle2D(0, 0, SHOOTER_FRAME_W, SHOOTER_FRAME_H));

            } else if (type == EnemyType.MELEE) {
                spriteView.setFitWidth(MELEE_FRAME_W * VISUAL_SCALE);
                spriteView.setFitHeight(MELEE_LEGS_H * VISUAL_SCALE);

                headView.setFitWidth(MELEE_FRAME_W * VISUAL_SCALE);
                headView.setFitHeight(MELEE_HEAD_H * VISUAL_SCALE);

                headView.setVisible(true);

                headView.setTranslateY(MELEE_HEAD_Y_OFFSET);
                applyMeleeHeadOffsets(false);

                headView.setViewport(new Rectangle2D(0, MELEE_Y_HEAD, MELEE_FRAME_W, MELEE_HEAD_H));
                spriteView.setViewport(new Rectangle2D(0, MELEE_Y_LEGS_DOWN, MELEE_FRAME_W, MELEE_LEGS_H));

            } else if (type == EnemyType.TANK) {
                // Para el TANK colocamos body/head manualmente (sin depender del StackPane),
                // así no se descuadra y siempre ves el cuerpo completo.
                spriteView.setManaged(false);
                headView.setManaged(false);

                // BODY = 64x64 (2x2 tiles)
                spriteView.setFitWidth(TANK_BODY_W * VISUAL_SCALE);
                spriteView.setFitHeight(TANK_BODY_H * VISUAL_SCALE);

                // HEAD = 32x32 (fijo, siempre mirando abajo)
                headView.setFitWidth(TANK_HEAD_W * VISUAL_SCALE);
                headView.setFitHeight(TANK_HEAD_H * VISUAL_SCALE);
                headView.setVisible(true);
                headView.setScaleX(1);

                // Viewports iniciales
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

                // Posicionar: body centrado sobre hitbox; head relativa al body (gap)
                positionTankParts();

            } else if (type == EnemyType.KAMIKAZE) {
                spriteView.setFitWidth(KAMIKAZE_FRAME_W * VISUAL_SCALE);
                spriteView.setFitHeight(KAMIKAZE_FRAME_H * VISUAL_SCALE);

                headView.setVisible(false);

                spriteView.setViewport(new Rectangle2D(
                        KAMIKAZE_X0 + (KAMIKAZE_COL_1 * KAMIKAZE_FRAME_W),
                        KAMIKAZE_Y0 + (KAMIKAZE_ROW_DOWN_UP * KAMIKAZE_FRAME_H),
                        KAMIKAZE_FRAME_W,
                        KAMIKAZE_FRAME_H
                ));

            } else {
                spriteView.setFitWidth(SHOOTER_FRAME_W * VISUAL_SCALE);
                spriteView.setFitHeight(SHOOTER_FRAME_H * VISUAL_SCALE);
                headView.setVisible(false);
            }

        } else {
            hasSprite = false;
            headView.setVisible(false);
        }

        animator = (type == EnemyType.SHOOTER)
                ? new SpriteAnimator(SHOOTER_FRAME_W, SHOOTER_FRAME_H, SHOOTER_WALK_FRAMES, 10, SHOOTER_COLUMNS)
                : new SpriteAnimator(SHOOTER_FRAME_W, SHOOTER_FRAME_H, 2, 6, 10);

        viewRoot.getChildren().addAll(debugBox, spriteView);
        if (type == EnemyType.SHOOTER || type == EnemyType.MELEE || type == EnemyType.TANK) viewRoot.getChildren().add(headView);

        viewRoot.setManaged(false);
        boundsPane.getChildren().add(viewRoot);

        EnemyProfile profile = AppContext.balance().profile(type);
        this.maxHealth = profile != null ? Math.max(0.0, profile.baseHp * hpMultiplier) : 0.0;
        this.hp = this.maxHealth;
    }

    private void positionTankParts() {
        // Body centrado respecto a la hitbox
        double bodyW = TANK_BODY_W * VISUAL_SCALE;
        double bodyH = TANK_BODY_H * VISUAL_SCALE;

        double bodyX = (WIDTH - bodyW) * 0.5 + TANK_BODY_X_OFFSET;
        double bodyY = (HEIGHT - bodyH) * 0.5 + TANK_BODY_Y_OFFSET;

        spriteView.setLayoutX(bodyX);
        spriteView.setLayoutY(bodyY);

        // Head centrada, pero en Y la colgamos del body (para que no flote)
        double headW = TANK_HEAD_W * VISUAL_SCALE;
        double headH = TANK_HEAD_H * VISUAL_SCALE;

        double headX = (WIDTH - headW) * 0.5 + TANK_HEAD_X_OFFSET;
        double headY = bodyY - TANK_HEAD_GAP_Y + TANK_HEAD_Y_OFFSET;

        headView.setLayoutX(headX);
        headView.setLayoutY(headY);
    }

    private Color getColorForType(EnemyType t) {
        return switch (t) {
            case SHOOTER  -> Color.ORANGE;
            case MELEE    -> Color.CRIMSON;
            case TURRET   -> Color.DODGERBLUE;
            case TANK     -> Color.DARKOLIVEGREEN;
            case KAMIKAZE -> Color.MAGENTA;
        };
    }

    public EnemyType getType() { return type; }

    @Override
    public void update(double dt) {
        if (dead || dt <= 0.0) return;

        EnemyProfile profile = AppContext.balance().profile(type);
        if (profile == null) return;

        syncHealthWithProfile(profile);

        double[] playerCenter = playerCenterSupplier.get();
        aiTime += dt;

        double distMoved = Math.hypot(viewRoot.getLayoutX() - lastX, viewRoot.getLayoutY() - lastY);
        if (distMoved < 0.5 * dt * 60) {
            stuckTimer += dt;
            if (stuckTimer > 0.5) {
                stuckDirection[0] = ThreadLocalRandom.current().nextDouble(-1, 1);
                stuckDirection[1] = ThreadLocalRandom.current().nextDouble(-1, 1);
                stuckTimer = -0.5;
            }
        } else {
            stuckTimer = 0;
            lastX = viewRoot.getLayoutX();
            lastY = viewRoot.getLayoutY();
        }

        handleMovement(profile, playerCenter, dt);
        updateAnimation(dt);

        if (type == EnemyType.TURRET) handleTurretShooting(profile, playerCenter, dt);
        else handleDefaultShooting(profile, playerCenter, dt);
    }

    private void updateAnimation(double dt) {
        if (!hasSprite) return;

        if (type == EnemyType.SHOOTER) { updateShooterAnimation(dt); return; }
        if (type == EnemyType.MELEE)  { updateMeleeAnimation(dt);  return; }
        if (type == EnemyType.KAMIKAZE){ updateKamikazeAnimation(dt); return; }
        if (type == EnemyType.TANK)   { updateTankAnimation(dt);   return; }

        animator.update(dt);
        spriteView.setViewport(animator.getCurrentViewport());

        double[] pc = playerCenterSupplier.get();
        if (pc != null && pc.length >= 1) {
            double dx = pc[0] - getCenterX();
            if (Math.abs(dx) > 1.0) spriteView.setScaleX(dx > 0 ? 1 : -1);
        }
    }

    // ===================== SHOOTER animation =====================
    private void updateShooterAnimation(double dt) {
        double cx = getCenterX();
        double cy = getCenterY();
        if (Double.isNaN(lastAnimCx)) { lastAnimCx = cx; lastAnimCy = cy; }

        double vx = cx - lastAnimCx;
        double vy = cy - lastAnimCy;
        lastAnimCx = cx;
        lastAnimCy = cy;

        double speed = Math.hypot(vx, vy);
        if (movingAnim) movingAnim = speed > MOVE_STOP;
        else movingAnim = speed > MOVE_START;

        Facing candidate = facing;

        if (movingAnim) {
            if (Math.abs(vx) > Math.abs(vy)) candidate = (vx >= 0) ? Facing.RIGHT : Facing.LEFT;
            else candidate = (vy >= 0) ? Facing.DOWN : Facing.UP;
        } else {
            double[] pc = playerCenterSupplier.get();
            if (pc != null && pc.length >= 2) {
                double dx = pc[0] - cx;
                double dy = pc[1] - cy;

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > FACE_DEADZONE) candidate = (dx >= 0) ? Facing.RIGHT : Facing.LEFT;
                } else {
                    if (Math.abs(dy) > FACE_DEADZONE) candidate = (dy >= 0) ? Facing.DOWN : Facing.UP;
                }
            }
        }

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

        int legsRow;
        boolean flip;
        switch (facing) {
            case DOWN -> { legsRow = SHOOTER_ROW_WALK_DOWN; flip = false; }
            case UP -> { legsRow = SHOOTER_ROW_WALK_UP; flip = false; }
            case RIGHT -> { legsRow = SHOOTER_ROW_WALK_SIDE; flip = false; }
            case LEFT -> { legsRow = SHOOTER_ROW_WALK_SIDE; flip = true; }
            default -> { legsRow = SHOOTER_ROW_WALK_DOWN; flip = false; }
        }

        int legsFrames = movingAnim ? SHOOTER_WALK_FRAMES : 1;
        boolean loop = movingAnim;

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

        int headCol;
        switch (facing) {
            case DOWN -> headCol = 0;
            case UP -> headCol = 2;
            case RIGHT, LEFT -> headCol = 1;
            default -> headCol = 0;
        }

        headView.setScaleX(flip ? -1 : 1);
        applyShooterHeadOffsets(flip);

        if (headCol != activeHeadCol) {
            headView.setViewport(new Rectangle2D(headCol * SHOOTER_FRAME_W, SHOOTER_ROW_HEAD * SHOOTER_FRAME_H, SHOOTER_FRAME_W, SHOOTER_FRAME_H));
            activeHeadCol = headCol;
        }
    }

    private void applyShooterHeadOffsets(boolean flip) {
        double sign = flip ? -1 : 1;
        headView.setTranslateX(sign * SHOOTER_HEAD_X_OFFSET);
        headView.setTranslateY(SHOOTER_HEAD_Y_OFFSET);
    }

    // ===================== MELEE animation =====================
    private void updateMeleeAnimation(double dt) {
        double cx = getCenterX();
        double cy = getCenterY();
        if (Double.isNaN(lastAnimCx)) { lastAnimCx = cx; lastAnimCy = cy; }

        double vx = cx - lastAnimCx;
        double vy = cy - lastAnimCy;
        lastAnimCx = cx;
        lastAnimCy = cy;

        double speed = Math.hypot(vx, vy);
        if (movingAnim) movingAnim = speed > MOVE_STOP;
        else movingAnim = speed > MOVE_START;

        Facing candidate = facing;

        if (movingAnim) {
            if (Math.abs(vx) > Math.abs(vy)) candidate = (vx >= 0) ? Facing.RIGHT : Facing.LEFT;
            else candidate = (vy >= 0) ? Facing.DOWN : Facing.UP;
        } else {
            double[] pc = playerCenterSupplier.get();
            if (pc != null && pc.length >= 2) {
                double dx = pc[0] - cx;
                double dy = pc[1] - cy;

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > FACE_DEADZONE) candidate = (dx >= 0) ? Facing.RIGHT : Facing.LEFT;
                } else {
                    if (Math.abs(dy) > FACE_DEADZONE) candidate = (dy >= 0) ? Facing.DOWN : Facing.UP;
                }
            }
        }

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

        boolean flip = (facing == Facing.LEFT);

        int legsY;
        int startCol;
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
            double step = 1.0 / MELEE_FPS;
            while (meleeFrameTimer >= step) {
                meleeFrameTimer -= step;
                meleeFrameIdx = (meleeFrameIdx + 1) % MELEE_WALK_FRAMES;
            }
        }

        if (legsY != meleeActiveLegsY || startCol != meleeActiveStartCol || flip != meleeActiveFlip || movingAnim == false) {
            meleeActiveLegsY = legsY;
            meleeActiveStartCol = startCol;
            meleeActiveFlip = flip;
        }

        spriteView.setScaleX(flip ? -1 : 1);
        int frameCol = startCol + meleeFrameIdx;
        spriteView.setViewport(new Rectangle2D(frameCol * MELEE_FRAME_W, legsY, MELEE_FRAME_W, MELEE_LEGS_H));

        int headCol;
        switch (facing) {
            case DOWN -> headCol = 0;
            case UP -> headCol = 2;
            case RIGHT, LEFT -> headCol = 1;
            default -> headCol = 0;
        }

        headView.setScaleX(flip ? -1 : 1);
        applyMeleeHeadOffsets(flip);

        if (headCol != meleeActiveHeadCol) {
            headView.setViewport(new Rectangle2D(headCol * MELEE_FRAME_W, MELEE_Y_HEAD, MELEE_FRAME_W, MELEE_HEAD_H));
            meleeActiveHeadCol = headCol;
        }
    }

    private void applyMeleeHeadOffsets(boolean flip) {
        double sign = flip ? -1 : 1;
        headView.setTranslateX(sign * MELEE_HEAD_X_OFFSET);
        headView.setTranslateY(MELEE_HEAD_Y_OFFSET);
    }

    // ===================== TANK animation =====================
    private void updateTankAnimation(double dt) {
        double cx = getCenterX();
        double cy = getCenterY();
        if (Double.isNaN(lastAnimCx)) { lastAnimCx = cx; lastAnimCy = cy; }

        double vx = cx - lastAnimCx;
        double vy = cy - lastAnimCy;
        lastAnimCx = cx;
        lastAnimCy = cy;

        double speed = Math.hypot(vx, vy);
        if (movingAnim) movingAnim = speed > MOVE_STOP;
        else movingAnim = speed > MOVE_START;

        Facing candidate = facing;

        if (movingAnim) {
            if (Math.abs(vx) > Math.abs(vy)) candidate = (vx >= 0) ? Facing.RIGHT : Facing.LEFT;
            else candidate = (vy >= 0) ? Facing.DOWN : Facing.UP;
        } else {
            candidate = facing;
        }

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

        boolean flip = (facing == Facing.LEFT);
        int bodyRowTop = (facing == Facing.RIGHT || facing == Facing.LEFT) ? TANK_ROW_BODY_RIGHT_TOP : TANK_ROW_BODY_DOWN_TOP;

        if (!movingAnim) {
            tankFrameIdx = 0;
            tankFrameTimer = 0.0;
        } else {
            tankFrameTimer += dt;
            double step = 1.0 / TANK_FPS;
            while (tankFrameTimer >= step) {
                tankFrameTimer -= step;
                tankFrameIdx = (tankFrameIdx + 1) % TANK_WALK_FRAMES;
            }
        }

        if (bodyRowTop != tankActiveBodyRow || flip != tankActiveFlip) {
            tankActiveBodyRow = bodyRowTop;
            tankActiveFlip = flip;
        }

        // Frames 0..5 => x = frame * 64
        int x = tankFrameIdx * TANK_BODY_W;

        spriteView.setScaleX(flip ? -1 : 1);
        spriteView.setViewport(new Rectangle2D(
                x,
                bodyRowTop * TANK_TILE,
                TANK_BODY_W,
                TANK_BODY_H
        ));

        // La cabeza es fija, pero si cambias offsets, re-posicionamos por si acaso.
        positionTankParts();
    }

    // ===================== KAMIKAZE animation =====================
    private void updateKamikazeAnimation(double dt) {
        double cx = getCenterX();
        double cy = getCenterY();
        if (Double.isNaN(lastAnimCx)) { lastAnimCx = cx; lastAnimCy = cy; }

        double vx = cx - lastAnimCx;
        double vy = cy - lastAnimCy;
        lastAnimCx = cx;
        lastAnimCy = cy;

        double speed = Math.hypot(vx, vy);
        if (movingAnim) movingAnim = speed > MOVE_STOP;
        else movingAnim = speed > MOVE_START;

        Facing candidate = facing;

        if (movingAnim) {
            if (Math.abs(vx) > Math.abs(vy)) candidate = (vx >= 0) ? Facing.RIGHT : Facing.LEFT;
            else candidate = (vy >= 0) ? Facing.DOWN : Facing.UP;
        } else {
            double[] pc = playerCenterSupplier.get();
            if (pc != null && pc.length >= 2) {
                double dx = pc[0] - cx;
                double dy = pc[1] - cy;

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > FACE_DEADZONE) candidate = (dx >= 0) ? Facing.RIGHT : Facing.LEFT;
                } else {
                    if (Math.abs(dy) > FACE_DEADZONE) candidate = (dy >= 0) ? Facing.DOWN : Facing.UP;
                }
            }
        }

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

        int row;
        int col;

        switch (facing) {
            case DOWN -> { row = KAMIKAZE_ROW_DOWN_UP; col = KAMIKAZE_COL_1; }
            case UP -> { row = KAMIKAZE_ROW_DOWN_UP; col = KAMIKAZE_COL_4; }
            case RIGHT -> { row = KAMIKAZE_ROW_RIGHT_LEFT; col = KAMIKAZE_COL_1; }
            case LEFT -> { row = KAMIKAZE_ROW_RIGHT_LEFT; col = KAMIKAZE_COL_4; }
            default -> { row = KAMIKAZE_ROW_DOWN_UP; col = KAMIKAZE_COL_1; }
        }

        spriteView.setScaleX(1);

        if (row != kamikazeActiveRow || col != kamikazeActiveCol) {
            spriteView.setViewport(new Rectangle2D(
                    KAMIKAZE_X0 + (col * KAMIKAZE_FRAME_W),
                    KAMIKAZE_Y0 + (row * KAMIKAZE_FRAME_H),
                    KAMIKAZE_FRAME_W,
                    KAMIKAZE_FRAME_H
            ));
            kamikazeActiveRow = row;
            kamikazeActiveCol = col;
        }
    }

    // --- MOVIMIENTO ---
    private void handleMovement(EnemyProfile profile, double[] playerCenter, double dt) {
        boolean isStat = profile.stationary && type != EnemyType.SHOOTER && type != EnemyType.MELEE;

        if (stuckTimer < 0) {
            stuckTimer += dt;
            moveWithSlide(stuckDirection, profile.speed * speedMultiplier * dt);
            return;
        }

        if (type == EnemyType.TURRET || isStat || !hasValidTarget(playerCenter)) return;

        switch (type) {
            case MELEE    -> moveMeleeZigZag(profile, playerCenter, dt);
            case SHOOTER  -> moveShooterKiting(profile, playerCenter, dt);
            case TANK     -> moveTank(profile, playerCenter, dt);
            case KAMIKAZE -> moveKamikaze(profile, playerCenter, dt);
            default       -> moveChasingPlayer(profile, playerCenter, dt);
        }
    }

    private void moveChasingPlayer(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        applyJitter(dir, profile.jitter);
        moveWithSlide(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveMeleeZigZag(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        double px = -dir[1]; double py = dir[0];
        double wave = Math.sin(aiTime * 6.0);
        double sideFactor = 0.45;
        double dx = dir[0] + px * wave * sideFactor;
        double dy = dir[1] + py * wave * sideFactor;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return;
        dir[0] = dx / len; dir[1] = dy / len;
        applyJitter(dir, profile.jitter * 0.5);
        moveWithSlide(dir, profile.speed * speedMultiplier * dt);
    }

    private void moveShooterKiting(EnemyProfile profile, double[] playerCenter, double dt) {
        double dx = playerCenter[0] - getCenterX();
        double dy = playerCenter[1] - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;
        double dirX = dx / dist; double dirY = dy / dist;
        double minRange = 150.0; double maxRange = 250.0;
        double baseSpeed = (profile.speed < 10.0) ? 100.0 : profile.speed;
        double speed = baseSpeed * speedMultiplier;

        if (dist < minRange) { tmpDir[0] = -dirX; tmpDir[1] = -dirY; }
        else if (dist > maxRange) { tmpDir[0] = dirX; tmpDir[1] = dirY; }
        else {
            tmpDir[0] = -dirY; tmpDir[1] = dirX;
            speed *= 0.6;
            applyJitter(tmpDir, profile.jitter * 0.25);
            moveWithSlide(tmpDir, speed * dt);
            return;
        }
        applyJitter(tmpDir, profile.jitter);
        moveWithSlide(tmpDir, speed * dt);
    }

    private void moveTank(EnemyProfile profile, double[] playerCenter, double dt) {
        double[] dir = directionTo(playerCenter);
        if (dir == null) return;
        double hpRatio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
        double speed = profile.speed * speedMultiplier;
        if (hpRatio <= 0.5) speed *= 1.4;
        applyJitter(dir, profile.jitter);
        moveWithSlide(dir, speed * dt);
    }

    private void moveKamikaze(EnemyProfile profile, double[] playerCenter, double dt) {
        double dx = playerCenter[0] - getCenterX();
        double dy = playerCenter[1] - getCenterY();
        double dist = Math.hypot(dx, dy);
        if (dist < EPSILON) return;
        double dirX = dx / dist; double dirY = dy / dist;
        double nearDist = 120.0; double farDist = 260.0;
        double factor;
        if (dist <= nearDist) factor = 1.6;
        else if (dist >= farDist) factor = 0.8;
        else { double t = (dist - nearDist) / (farDist - nearDist); factor = 1.6 + (0.8 - 1.6) * t; }
        double speed = profile.speed * speedMultiplier * factor;
        tmpDir[0] = dirX; tmpDir[1] = dirY;
        applyJitter(tmpDir, profile.jitter);
        moveWithSlide(tmpDir, speed * dt);
    }

    private void moveWithSlide(double[] dir, double distance) {
        if (distance <= 0.0) return;
        double deltaX = dir[0] * distance;
        double deltaY = dir[1] * distance;
        double currX = viewRoot.getLayoutX();
        double currY = viewRoot.getLayoutY();

        if (!checkCollision(currX + deltaX, currY)) currX += deltaX;
        if (!checkCollision(currX, currY + deltaY)) currY += deltaY;

        double maxX = Math.max(0.0, boundsPane.getWidth() - WIDTH);
        double maxY = Math.max(0.0, boundsPane.getHeight() - HEIGHT);
        viewRoot.setLayoutX(clamp(currX, 0.0, maxX));
        viewRoot.setLayoutY(clamp(currY, 0.0, maxY));
    }

    private boolean checkCollision(double x, double y) {
        if (obstaclesSupplier == null) return false;
        List<GameEntity> obstacles = obstaclesSupplier.get();
        if (obstacles == null || obstacles.isEmpty()) return false;

        double margin = 2.0 * VISUAL_SCALE;
        BoundingBox checkBounds = new BoundingBox(x + margin, y + margin, WIDTH - margin*2, HEIGHT - margin*2);

        for (GameEntity obs : obstacles) {
            if (obs.getBounds().intersects(checkBounds)) return true;
        }
        return false;
    }

    // --- DISPARO ---
    private void handleDefaultShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        if (profile.fireRate > 0.0 && profile.projSpeed > 0.0 && profile.projRange > 0.0) {
            timeSinceShot += dt;
            double interval = (1.0 / profile.fireRate);
            if (timeSinceShot >= interval) {
                if (hasValidTarget(playerCenter)) {
                    timeSinceShot = 0.0;
                    shootTowards(playerCenter, profile);
                } else timeSinceShot = interval;
            }
        } else timeSinceShot = 0.0;
    }

    private void handleTurretShooting(EnemyProfile profile, double[] playerCenter, double dt) {
        double projSpeed = Math.max(0.0, profile.projSpeed);
        if (projSpeed <= 0.0) { burstShotsRemaining = 0; return; }
        double burstInterval = (1.0 / profile.fireRate);
        final double perShotDelay = 0.1;
        final int burstSize = 3;

        if (burstShotsRemaining > 0) {
            burstShotTimer += dt;
            if (burstShotTimer >= perShotDelay) {
                burstShotTimer = 0.0;
                fireBurstShot(playerCenter, profile);
            }
        } else if (Double.isFinite(burstInterval)) {
            burstCooldownTimer += dt;
            if (burstCooldownTimer >= burstInterval && hasValidTarget(playerCenter)) {
                burstCooldownTimer = 0.0;
                burstShotsRemaining = burstSize;
                burstShotTimer = 0.0;
                fireBurstShot(playerCenter, profile);
            }
        }
    }

    private void fireBurstShot(double[] playerCenter, EnemyProfile profile) {
        if (burstShotsRemaining <= 0) return;
        burstShotsRemaining--;
        if (hasValidTarget(playerCenter)) shootTowards(playerCenter, profile);
        if (burstShotsRemaining <= 0) burstShotTimer = 0.0;
    }

    private void shootTowards(double[] target, EnemyProfile profile) {
        double[] dir = directionTo(target);
        if (dir == null) return;
        double projSpeed = Math.max(0.0, profile.projSpeed);
        double projRange = Math.max(0.0, profile.projRange);
        double projDamage = Math.max(0.0, profile.projDamage * damageMultiplier);
        if (projSpeed <= 0.0 || projRange <= 0.0 || projDamage <= 0.0) return;
        double lifetime = projRange / projSpeed;

        Projectile projectile = new Projectile(dir[0], dir[1], projSpeed, lifetime, projDamage,
                true, boundsPane, onRemove, this, type.name());

        double projOffset = 4.0 * VISUAL_SCALE;
        projectile.getView().setLayoutX(getCenterX() - projOffset);
        projectile.getView().setLayoutY(getCenterY() - projOffset);

        onSpawn.accept(projectile);
    }

    // --- UTILS ---
    private void syncHealthWithProfile(EnemyProfile profile) {
        double desiredMax = Math.max(0.0, profile.baseHp * hpMultiplier);
        if (Math.abs(desiredMax - maxHealth) > 1e-6) {
            double ratio = maxHealth > 0.0 ? hp / maxHealth : 1.0;
            maxHealth = desiredMax;
            hp = Math.min(maxHealth, Math.max(0.0, ratio * maxHealth));
            if (hp <= 0.0) die();
        } else if (hp > maxHealth) hp = maxHealth;
    }

    private double[] directionTo(double[] target) {
        double cx = getCenterX(); double cy = getCenterY();
        double dx = target[0] - cx; double dy = target[1] - cy;
        double len = Math.hypot(dx, dy);
        if (len < EPSILON) return null;
        tmpDir[0] = dx / len; tmpDir[1] = dy / len;
        return tmpDir;
    }

    private void applyJitter(double[] dir, double jitterPercent) {
        double magnitude = Math.max(0.0, Math.min(1.0, jitterPercent * 0.01));
        if (magnitude <= 0.0) return;
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        dir[0] += Math.cos(angle) * magnitude;
        dir[1] += Math.sin(angle) * magnitude;
        double len = Math.hypot(dir[0], dir[1]);
        if (len < EPSILON) { dir[0] = 0.0; dir[1] = 1.0; }
        else { dir[0] /= len; dir[1] /= len; }
    }

    @Override public Node getView() { return viewRoot; }

    @Override
    public Bounds getBounds() {
        return new BoundingBox(viewRoot.getLayoutX(), viewRoot.getLayoutY(), WIDTH, HEIGHT);
    }

    @Override
    public void onCollision(GameEntity other) {
        if (dead) return;
        if (other instanceof Player player) {
            EnemyProfile profile = AppContext.balance().profile(type);
            double dmg = (profile != null ? profile.contactDmg : AppContext.balance().enemyContactDamage);
            dmg *= damageMultiplier;
            if (dmg > 0.0) {
                player.setLastHitSource(type.name());
                player.takeDamage(dmg);
                playSfx.accept("hurt");
            }
        }
    }

    public double getWidth() { return WIDTH; }
    public double getHeight() { return HEIGHT; }
    public double getCollisionRadius() { return collisionRadius; }
    public double getCenterX() { return viewRoot.getLayoutX() + getWidth() * 0.5; }
    public double getCenterY() { return viewRoot.getLayoutY() + getHeight() * 0.5; }

    public double getX() { return viewRoot.getLayoutX(); }
    public double getY() { return viewRoot.getLayoutY(); }

    public void setPosition(double x, double y) {
        viewRoot.setLayoutX(x);
        viewRoot.setLayoutY(y);
    }

    public void applyDamage(double dmg) {
        if (dead || dmg <= 0.0) return;
        hp -= dmg;
        if (hp <= 0.0) die();
        else {
            flashHit();
            playSfx.accept("hit");
        }
    }

    public boolean isDead() { return dead; }

    private void die() {
        if (dead) return;
        dead = true;
        hp = 0.0;
        spawnDeathFx();
        onRemove.accept(this);
    }

    private boolean hasValidTarget(double[] playerCenter) {
        return playerCenter != null && playerCenter.length >= 2;
    }

    private void flashHit() {
        if (hitFlashTimer == null) {
            hitFlashTimer = new PauseTransition(Duration.millis(120));
            hitFlashTimer.setOnFinished(e -> {
                debugBox.setStroke(Color.TRANSPARENT);
                spriteView.setEffect(null);
            });
        } else hitFlashTimer.stop();

        debugBox.setStroke(Color.WHITE);
        spriteView.setEffect(new javafx.scene.effect.ColorAdjust(0, 0, 0.5, 0));
        hitFlashTimer.playFromStart();
    }

    private void spawnDeathFx() {
        Circle fx = new Circle(6 * VISUAL_SCALE, Color.ORANGERED);
        fx.setManaged(false);
        fx.setLayoutX(getCenterX()); fx.setLayoutY(getCenterY());

        GameEntity fxEntity = new GameEntity() {
            @Override public void update(double dt) {}
            @Override public Node getView() { return fx; }
        };

        onSpawn.accept(fxEntity);

        FadeTransition fade = new FadeTransition(Duration.millis(250), fx);
        fade.setFromValue(1.0); fade.setToValue(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(250), fx);
        scale.setFromX(1.0); scale.setFromY(1.0);
        scale.setToX(1.8); scale.setToY(1.8);

        ParallelTransition pt = new ParallelTransition(fx, fade, scale);
        pt.setOnFinished(e -> onRemove.accept(fxEntity));
        pt.play();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }
}
