package com.layla.ui;

import java.text.DecimalFormat;
import java.util.Objects;

import com.layla.AppContext;
import com.layla.entities.Player;
import com.layla.services.StatsService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * HUD lateral izquierdo del juego.
 *
 * <p>Muestra estadísticas del jugador (velocidad, cadencia, etc.) y la vida en formato de corazones
 * (2 HP = 1 corazón). Este componente está pensado para refrescarse periódicamente mediante
 * {@link #refresh()}.</p>
 *
 * <p><b>Nota de UI:</b> el HUD se marca como transparente al ratón para no bloquear overlays
 * (tienda, menús, etc.).</p>
 */
public final class HudView extends VBox {

    /** Tamaño del icono de corazón (en px). */
    private static final double HEART_SIZE = 16.0;

    /** Separación horizontal entre corazones. */
    private static final double HEART_SPACING = 4.0;

    /** Formateador con 1 decimal para los valores numéricos del HUD. */
    private final DecimalFormat df1 = new DecimalFormat("0.0");

    /** Fuente de estadísticas (global o inyectada). */
    private final StatsService stats;

    /** Jugador actual para consultar salud y salud máxima. */
    private final Player player;

    // Labels de stats
    private final Label lblSpeed = new Label();
    private final Label lblTears = new Label();
    private final Label lblShotSpeed = new Label();
    private final Label lblRange = new Label();
    private final Label lblDamage = new Label();

    // Corazones
    private final HBox heartsBox = new HBox(HEART_SPACING);

    /**
     * Crea el HUD.
     *
     * @param statsService servicio de estadísticas (si es null, se usa {@link AppContext#stats()})
     * @param player jugador del que se leerán vida y vida máxima (puede ser null)
     */
    public HudView(StatsService statsService, Player player) {
        this.stats = (statsService != null) ? statsService : AppContext.stats();
        this.player = player;

        configureLayout();
        buildUi();

        refresh(); // primer pintado
    }

    /**
     * Configuración visual y de interacción del contenedor del HUD.
     */
    private void configureLayout() {
        setSpacing(8);
        setPadding(new Insets(12));
        setAlignment(Pos.TOP_LEFT);

        // Hacer que el HUD sea transparente al ratón para no bloquear overlays u otros elementos.
        setMouseTransparent(true);
        setPickOnBounds(false);

        // Fondo semi-transparente.
        setBackground(new Background(
                new BackgroundFill(Color.rgb(0, 0, 0, 0.35), new CornerRadii(8), Insets.EMPTY)
        ));

        setBorder(new Border(new BorderStroke(
                Color.rgb(255, 255, 255, 0.10),
                BorderStrokeStyle.SOLID,
                new CornerRadii(8),
                new BorderWidths(1)
        )));
    }

    /**
     * Construye las filas del HUD (stats + vida).
     */
    private void buildUi() {
        getChildren().addAll(
                statLine(icon("speed_icon.png"), "Velocidad", lblSpeed),
                statLine(icon("tears_icon.png"), "Cadencia", lblTears),
                statLine(icon("shotspeed_icon.png"), "Vel. proyectil", lblShotSpeed),
                statLine(icon("range_icon.png"), "Alcance", lblRange),
                statLine(icon("damage_icon.png"), "Daño", lblDamage),
                heartLine(icon("health_icon.png"))
        );
    }

    /**
     * Crea una fila de estadística con icono + nombre + valor.
     */
    private HBox statLine(ImageView iv, String name, Label value) {
        Label title = new Label(name);
        title.getStyleClass().add("hud-title");
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        value.getStyleClass().add("hud-value");
        value.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");

        HBox row = new HBox(8, iv, title, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Crea la fila de vida (icono + texto + contenedor de corazones).
     */
    private HBox heartLine(ImageView iv) {
        Label title = new Label("Vida");
        title.getStyleClass().add("hud-title");
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        heartsBox.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(8, iv, title, heartsBox);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Carga un icono del HUD desde recursos.
     *
     * @param file nombre de archivo dentro de {@code /assets/images/}
     * @return ImageView configurado (si no se encuentra, se crea sin imagen)
     */
    private ImageView icon(String file) {
        Image img = null;
        try {
            img = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/assets/images/" + file),
                    "Recurso no encontrado: /assets/images/" + file
            ));
        } catch (Exception ignore) {
            // Fallback: icono nulo (ImageView sin imagen).
        }

        ImageView iv = new ImageView(img);
        iv.setFitWidth(18);
        iv.setFitHeight(18);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    /**
     * Refresca el contenido del HUD leyendo los valores actuales del {@link StatsService}
     * y del {@link Player}.
     */
    public void refresh() {
        double speed = stats.getMoveSpeed();
        double fireRate = stats.getFireRate();
        double shotSpeed = stats.getProjectileSpeed();
        double rangeSeconds = stats.getProjectileRange();
        double damage = stats.getProjectileDamage();

        lblSpeed.setText(df1.format(speed));
        lblTears.setText(df1.format(fireRate));
        lblShotSpeed.setText(df1.format(shotSpeed));
        lblRange.setText(df1.format(rangeSeconds));
        lblDamage.setText(df1.format(damage));

        updateHearts();
    }

    /**
     * Crea un icono de corazón “lleno”.
     */
    private Node fullHeart() {
        return createHeartIcon("full_heart_icon.png");
    }

    /**
     * Crea un icono de corazón “medio”.
     */
    private Node halfHeart() {
        return createHeartIcon("half_heart_icon.png");
    }

    /**
     * Crea un icono de corazón “vacío”.
     */
    private Node emptyHeart() {
        return createHeartIcon("empty_heart_icon.png");
    }

    /**
     * Construye un nodo de corazón a partir de un recurso de imagen.
     *
     * @param name nombre de archivo dentro de {@code /assets/images/}
     * @return nodo con el icono de corazón (si no se encuentra, se devuelve un ImageView sin imagen)
     */
    private Node createHeartIcon(String name) {
        Image img = null;
        try {
            img = new Image(getClass().getResourceAsStream("/assets/images/" + name));
        } catch (Exception ignore) {
            // Fallback: imagen nula.
        }

        ImageView iv = new ImageView(img);
        iv.setFitWidth(HEART_SIZE);
        iv.setFitHeight(HEART_SIZE);
        iv.setPreserveRatio(true);
        return iv;
    }

    /**
     * Reconstruye los corazones en función de la vida actual del jugador.
     *
     * <p>Convención del juego: 2 HP = 1 corazón.
     * Ejemplo: 5 HP => 2 corazones llenos + 1 medio.</p>
     *
     * <p>También se rellena con corazones vacíos hasta el máximo.</p>
     */
    private void updateHearts() {
        heartsBox.getChildren().clear();
        if (player == null) return;

        int hp = (int) Math.ceil(player.getHealth());
        int max = (int) Math.ceil(player.getMaxHealth());

        int slots = Math.max(1, max / 2); // cantidad de corazones totales

        int full = hp / 2;
        boolean half = (hp % 2) == 1;

        for (int i = 0; i < full && i < slots; i++) {
            heartsBox.getChildren().add(fullHeart());
        }

        if (half && full < slots) {
            heartsBox.getChildren().add(halfHeart());
        }

        while (heartsBox.getChildren().size() < slots) {
            heartsBox.getChildren().add(emptyHeart());
        }
    }
}
