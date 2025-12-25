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

/**
 * Servicio responsable de gestionar la lógica de disparo del jugador.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *   <li>Gestionar el cooldown entre disparos en función del {@link PlayerStatId#CADENCIA}.</li>
 *   <li>Guardar y actualizar la dirección de apuntado (aim) a partir del vector de movimiento o un aim explícito.</li>
 *   <li>Spawnear proyectiles con sus parámetros (velocidad, vida, daño, etc.) según las stats actuales.</li>
 *   <li>Soportar disparo múltiple (spread), pierce, bounce y homing.</li>
 * </ul>
 *
 * <p>Notas de diseño:</p>
 * <ul>
 *   <li>No cambia nombres ni firma de métodos: se limita a limpieza y documentación.</li>
 *   <li>El {@code targetSupplier} es genérico ({@code List<GameEntity>}) para permitir homing contra enemigos y bosses.</li>
 * </ul>
 */
public final class ShootingService {

    /** Servicio de stats del jugador (base + modificadores + items). */
    private final StatsService statsService;

    /** Temporizador interno de cooldown (segundos restantes). */
    private double timer = 0.0;

    /** Dirección de apuntado normalizada (por defecto: arriba). */
    private double aimX = 0.0;
    private double aimY = -1.0;

    /**
     * Proveedor de posibles objetivos para proyectiles con homing.
     * <p>Se deja como {@code Supplier<List<GameEntity>>} para aceptar distintos tipos de entidades (enemigos/bosses).</p>
     */
    private Supplier<List<GameEntity>> targetSupplier;

    /**
     * Crea el servicio de disparo.
     *
     * @param statsService servicio de stats a usar. Si es {@code null}, se usa {@link AppContext#stats()}.
     */
    public ShootingService(StatsService statsService) {
        this.statsService = (statsService != null) ? statsService : AppContext.stats();
    }

    /**
     * Define el proveedor de objetivos para proyectiles con homing.
     *
     * @param supplier proveedor de lista de entidades objetivo (puede ser {@code null})
     */
    public void setTargetSupplier(Supplier<List<GameEntity>> supplier) {
        this.targetSupplier = supplier;
    }

    /**
     * Actualiza el cooldown del disparo y, si se aporta, actualiza la dirección de apuntado
     * a partir del vector de movimiento.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Reduce el cooldown interno ({@link #timer}) sin bajar de 0.</li>
     *   <li>Si {@code moveVec} tiene longitud y magnitud suficiente, se normaliza y se usa como nuevo aim.</li>
     * </ul>
     *
     * @param dt delta time en segundos
     * @param moveVec vector de movimiento (esperado {x,y}); puede ser {@code null}
     */
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

    /**
     * Fija manualmente el aim (dirección de disparo).
     * <p>El vector se normaliza. Si su longitud es casi 0, se deja apuntando hacia arriba.</p>
     *
     * @param x componente X del aim
     * @param y componente Y del aim
     */
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
     * Intenta disparar proyectiles desde un punto de origen.
     *
     * <p>Reglas:</p>
     * <ul>
     *   <li>Si el cooldown interno ({@link #timer}) sigue activo, no dispara.</li>
     *   <li>El cooldown se calcula como {@code 1 / CADENCIA}. Si {@code CADENCIA <= 0}, no dispara.</li>
     *   <li>El número de proyectiles por disparo se toma de {@link PlayerStatId#NUMERO_DE_PROYECTILES} (mínimo 1).</li>
     *   <li>Aplica una dispersión (spread) fija según el número de proyectiles.</li>
     * </ul>
     *
     * @param gameArea pane del juego donde vive el proyectil
     * @param loop loop del juego (para add/remove de entidades)
     * @param originX coordenada X de salida
     * @param originY coordenada Y de salida
     * @param owner entidad propietaria del proyectil (normalmente el jugador)
     * @param onSpawn callback opcional al crear cada proyectil
     * @return {@code true} si se disparó, {@code false} si no (cooldown o stats inválidas)
     */
    public boolean tryShoot(
            Pane gameArea,
            GameLoop loop,
            double originX, double originY,
            GameEntity owner,
            Consumer<Projectile> onSpawn
    ) {
        Objects.requireNonNull(gameArea, "gameArea");
        Objects.requireNonNull(loop, "loop");
        Objects.requireNonNull(owner, "owner");

        double fireRate = statsService.getStat(PlayerStatId.CADENCIA);
        double fireCooldown = fireRate > 0.0 ? (1.0 / fireRate) : Double.POSITIVE_INFINITY;
        if (timer > 0.0 || !Double.isFinite(fireCooldown) || fireCooldown <= 0.0) return false;

        double projectileSpeed = statsService.getStat(PlayerStatId.VELOCIDAD_DEL_PROYECTIL);
        double lifetime = statsService.getStat(PlayerStatId.RANGO_DEL_PROYECTIL);
        double damage = statsService.getStat(PlayerStatId.DAÑO_DEL_PROYECTIL);

        int pierce = (int) statsService.getStat(PlayerStatId.PENETRACIÓN_DEL_PROYECTIL);
        int bounce = (int) statsService.getStat(PlayerStatId.REBOTE_DEL_PROYECTIL);
        int count = Math.max(1, (int) statsService.getStat(PlayerStatId.NUMERO_DE_PROYECTILES));
        boolean homing = statsService.getStat(PlayerStatId.AUTOAPUNTADO_DEL_PROYECTIL) > 0;

        double ax = aimX;
        double ay = aimY;
        double baseAngle = Math.atan2(ay, ax);

        // Dispersión: se mantiene el comportamiento actual, solo se documenta.
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
                    targetSupplier
            );

            // Offset visual de spawn (se mantiene igual).
            p.getView().setLayoutX(originX - 5.0);
            p.getView().setLayoutY(originY - 5.0);

            loop.addEntity(p);
            if (onSpawn != null) onSpawn.accept(p);
        }

        timer = fireCooldown;
        return true;
    }
}
