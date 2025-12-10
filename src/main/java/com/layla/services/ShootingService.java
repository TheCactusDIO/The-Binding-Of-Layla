package com.layla.services;

import static java.lang.Math.hypot;
import static java.lang.Math.max;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.layla.AppContext;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.entities.Projectile;
import com.layla.model.PlayerStatId;

import javafx.scene.layout.Pane;

public final class ShootingService {

    private final StatsService statsService;
    private double timer = 0.0;

    private double aimX = 0.0;
    private double aimY = -1.0;

    // FIX: Ahora es genérico para aceptar Bosses
    private Supplier<List<GameEntity>> targetSupplier;

    public ShootingService(StatsService statsService) {
        this.statsService = (statsService != null) ? statsService : AppContext.stats();
    }

    public void setTargetSupplier(Supplier<List<GameEntity>> supplier) {
        this.targetSupplier = supplier;
    }

    public void update(double dt, double[] moveVec) {
        timer = max(0.0, timer - dt);

        if (moveVec != null) {
            double mx = moveVec.length > 0 ? moveVec[0] : 0.0;
            double my = moveVec.length > 1 ? moveVec[1] : 0.0;
            double mlen = hypot(mx, my);
            if (mlen > 1e-6) {
                aimX = mx / mlen;
                aimY = my / mlen;
            }
        }
    }

    public void setAim(double x, double y) {
        double len = hypot(x, y);
        if (len < 1e-6) {
            aimX = 0.0;
            aimY = -1.0;
        } else {
            aimX = x / len;
            aimY = y / len;
        }
    }

    public boolean tryShoot(Pane gameArea,
                            GameLoop loop,
                            double originX, double originY,
                            GameEntity owner,
                            Consumer<Projectile> onSpawn) {
        Objects.requireNonNull(gameArea, "gameArea");
        Objects.requireNonNull(loop, "loop");
        Objects.requireNonNull(owner, "owner");

        double fireRate = statsService.getStat(PlayerStatId.FIRE_RATE);
        double fireCooldown = fireRate > 0.0 ? (1.0 / fireRate) : Double.POSITIVE_INFINITY;
        if (timer > 0.0 || !Double.isFinite(fireCooldown) || fireCooldown <= 0.0) return false;

        double projectileSpeed = statsService.getStat(PlayerStatId.PROJECTILE_SPEED);
        double lifetime        = statsService.getStat(PlayerStatId.PROJECTILE_RANGE);
        double damage          = statsService.getStat(PlayerStatId.PROJECTILE_DAMAGE);

        int pierce = (int) statsService.getStat(PlayerStatId.PROJECTILE_PIERCE);
        int bounce = (int) statsService.getStat(PlayerStatId.PROJECTILE_BOUNCE);
        int count  = Math.max(1, (int) statsService.getStat(PlayerStatId.PROJECTILE_COUNT));
        boolean homing = statsService.getStat(PlayerStatId.PROJECTILE_HOMING) > 0;

        double ax = aimX, ay = aimY;
        double baseAngle = Math.atan2(ay, ax);

        double spread = Math.toRadians(15);
        if (count > 5) spread = Math.toRadians(10);

        double startAngle = baseAngle - (spread * (count - 1)) / 2.0;

        for (int i = 0; i < count; i++) {
            double currentAngle = startAngle + spread * i;
            double dirX = Math.cos(currentAngle);
            double dirY = Math.sin(currentAngle);

            Projectile p = new Projectile(
                dirX, dirY,
                projectileSpeed,
                lifetime,
                damage,
                false,
                gameArea,
                loop::removeEntity,
                owner,
                "PLAYER",
                pierce,
                bounce,
                homing,
                targetSupplier // Supplier actualizado
            );
            p.getView().setLayoutX(originX - 5.0);
            p.getView().setLayoutY(originY - 5.0);

            loop.addEntity(p);
            if (onSpawn != null) onSpawn.accept(p);
        }

        timer = fireCooldown;
        return true;
    }
}
