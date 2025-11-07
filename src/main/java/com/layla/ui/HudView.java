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

/** HUD lateral izquierdo: stats + salud. */
public final class HudView extends VBox {

    private final StatsService stats;        // <- fuente de stats (global o inyectada)
    private final Player player;             // usado para salud
    private final DecimalFormat df1 = new DecimalFormat("0.0");
    private final DecimalFormat df0 = new DecimalFormat("0");

    private final Label lblSpeed = new Label();
    private final Label lblTears = new Label();
    private final Label lblShotSpeed = new Label();
    private final Label lblRange = new Label();
    private final Label lblDamage = new Label();
    private final HBox heartsBox = new HBox(4);

    public HudView(StatsService statsService, Player player) {
        // si te pasan null, cae al servicio global
        this.stats  = (statsService != null) ? statsService : AppContext.stats();
        this.player = player;

        setSpacing(8);
        setPadding(new Insets(12));
        setAlignment(Pos.TOP_LEFT);
        setPickOnBounds(false);

        // Fondo semi-transparente tipo Repentance
        BackgroundFill fill = new BackgroundFill(Color.rgb(0, 0, 0, 0.35), new CornerRadii(8), Insets.EMPTY);
        setBackground(new Background(fill));
        setBorder(new Border(new BorderStroke(Color.rgb(255,255,255,0.10),
                BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1))));

        getChildren().addAll(
            statLine(icon("speed_icon.png"), "Speed", lblSpeed),
            statLine(icon("tears_icon.png"), "Fire Rate (shots/s)", lblTears),
            statLine(icon("shotspeed_icon.png"), "Shot Spd", lblShotSpeed),
            statLine(icon("range_icon.png"), "Proj Range (s)", lblRange),
            statLine(icon("damage_icon.png"), "Damage", lblDamage),
            heartLine(icon("health_icon.png"))
        );

        refresh(); // primer pintado
    }

    private HBox statLine(ImageView iv, String name, Label value) {
        Label title = new Label(name);
        title.getStyleClass().add("hud-title");
        value.getStyleClass().add("hud-value");

        HBox row = new HBox(8, iv, title, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox heartLine(ImageView iv) {
        Label title = new Label("Health");
        title.getStyleClass().add("hud-title");
        heartsBox.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(8, iv, title, heartsBox);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private ImageView icon(String file) {
        Image img = new Image(Objects.requireNonNull(
            getClass().getResourceAsStream("/assets/images/" + file),
            "No se encontró /assets/images/" + file
        ));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(18);
        iv.setFitHeight(18);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    /** Llamar cada X frames o en un AnimationTimer throttled. */
    public void refresh() {
        // Stats desde el servicio
        double speed        = stats.getMoveSpeed();
        double fireRate     = stats.getFireRate();
        double shotSpeed    = stats.getProjectileSpeed();
        double rangeSeconds = stats.getProjectileRange();
        double damage       = stats.getProjectileDamage();

        lblSpeed.setText(df1.format(speed));
        lblTears.setText(df1.format(fireRate));
        lblShotSpeed.setText(df1.format(shotSpeed));
        lblRange.setText(df1.format(rangeSeconds));
        lblDamage.setText(df1.format(damage));

        // Hearts:
        updateHearts();
    }

    // ==================== HEART ICONS ====================

    private static final double HEART_SIZE = 12.0;

    private Node fullHeart() {
        Image img = new Image(Objects.requireNonNull(
            getClass().getResourceAsStream("/assets/images/full_heart_icon.png"),
            "No se encontró /assets/images/full_heart_icon.png"
        ));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(HEART_SIZE);
        iv.setFitHeight(HEART_SIZE);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    private Node halfHeart() {
        Image img = new Image(Objects.requireNonNull(
            getClass().getResourceAsStream("/assets/images/half_heart_icon.png"),
            "No se encontró /assets/images/half_heart_icon.png"
        ));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(HEART_SIZE);
        iv.setFitHeight(HEART_SIZE);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    private Node emptyHeart() {
        Image img = new Image(Objects.requireNonNull(
            getClass().getResourceAsStream("/assets/images/empty_heart_icon.png"),
            "No se encontró /assets/images/empty_heart_icon.png"
        ));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(HEART_SIZE);
        iv.setFitHeight(HEART_SIZE);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    /** Actualiza los corazones del HUD según la salud actual del jugador. */
    private void updateHearts() {
        heartsBox.getChildren().clear();
        if (player == null) return;

        int hp   = (int)Math.round(player.getHealth());      // 2 HP = 1 corazón
        int max  = (int)Math.round(player.getMaxHealth());
        int slots = Math.max(1, max / 2);

        int full = hp / 2;
        boolean half = (hp % 2) == 1;

        for (int i = 0; i < full && i < slots; i++) heartsBox.getChildren().add(fullHeart());
        if (half && full < slots) heartsBox.getChildren().add(halfHeart());
        while (heartsBox.getChildren().size() < slots) heartsBox.getChildren().add(emptyHeart());
    }
}
