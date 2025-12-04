package com.layla.services;

import static java.lang.Math.hypot;
import static java.lang.Math.max;
import java.util.Objects;
import java.util.function.Consumer;

import com.layla.AppContext;
import com.layla.core.GameEntity;
import com.layla.core.GameLoop;
import com.layla.entities.Projectile;
import com.layla.model.PlayerStatId;

import javafx.scene.layout.Pane;

/**
 * Gestiona disparos del jugador leyendo las estadísticas centralizadas.
 * Añade "owner" para ignorar autocolisión inicial con el jugador.
 */
public final class ShootingService {

    private final StatsService statsService; // asignado en ctor
    private double timer = 0.0;

    // Última dirección válida (por defecto, arriba)
    private double aimX = 0.0;
    private double aimY = -1.0;

    public ShootingService(StatsService statsService) {
        // si te pasan null, usa el global
        this.statsService = (statsService != null) ? statsService : AppContext.stats();
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

        double ax = aimX, ay = aimY;
        double alen = hypot(ax, ay);
        if (alen < 1e-6) { ax = 0.0; ay = -1.0; }
        else { ax /= alen; ay /= alen; }

        // Jugador dispara → fromEnemy = false y owner = player
        Projectile p = new Projectile(
            ax, ay,
            projectileSpeed,
            lifetime,
            damage,
            /*fromEnemy*/ false,
            gameArea,
            loop::removeEntity,
            owner
        );
        p.getView().setLayoutX(originX - 4.0);
        p.getView().setLayoutY(originY - 4.0);

        loop.addEntity(p);
        if (onSpawn != null) onSpawn.accept(p);

        // Reinicia cooldown
        timer = fireCooldown;
        return true;
    }
}
