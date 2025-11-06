package com.layla.services;

import static java.lang.Math.hypot;
import static java.lang.Math.max;

import java.util.Objects;
import java.util.function.Consumer;

import com.layla.core.GameLoop;
import com.layla.entities.Projectile;
import com.layla.model.StatType;

import javafx.scene.layout.Pane;

/**
 * Gestiona disparos básicos leyendo siempre las estadísticas centralizadas.
 */
public final class ShootingService {

    private final StatsService statsService;
    private double timer = 0.0;

    // Última dirección válida de movimiento (por defecto, arriba)
    private double aimX = 0.0;
    private double aimY = -1.0;

    public ShootingService(StatsService statsService) {
        this.statsService = Objects.requireNonNull(statsService, "statsService");
    }

    /** Refresca cooldown y fija aim según movimiento (si hay). */
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

    /**
     * Intenta disparar si el cooldown ha terminado.
     * @return true si se crea un proyectil.
     */
    public boolean tryShoot(Pane gameArea, GameLoop loop, double originX, double originY,
                            Consumer<Projectile> onSpawn) {
        Objects.requireNonNull(gameArea, "gameArea");
        Objects.requireNonNull(loop, "loop");

        if (timer > 0.0) return false;

        double fireCooldown   = statsService.getStat(StatType.FIRE_COOLDOWN);
        double projectileSpeed = statsService.getStat(StatType.PROJECTILE_SPEED);
        double rangePixels     = statsService.getStat(StatType.RANGE_PIXELS);
        double damage          = statsService.getStat(StatType.DAMAGE);

        double lifetime = projectileSpeed > 0.0 ? (rangePixels / projectileSpeed) : 0.0;

        double ax = aimX, ay = aimY;
        double alen = hypot(ax, ay);
        if (alen < 1e-6) { ax = 0.0; ay = -1.0; }
        else { ax /= alen; ay /= alen; }

        // Crea el proyectil y compensa el radio (asumiendo Circle r=4)
        Projectile p = new Projectile(ax, ay, projectileSpeed, lifetime, damage, gameArea, loop::removeEntity);
        p.getView().setLayoutX(originX - 4.0);
        p.getView().setLayoutY(originY - 4.0);

        loop.addEntity(p);
        if (onSpawn != null) onSpawn.accept(p);

        // Reinicia cooldown
        timer = fireCooldown;
        return true;
    }
}
