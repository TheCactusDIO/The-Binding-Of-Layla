package com.layla.ui;

import java.text.DecimalFormat;
import java.util.Objects;

import com.layla.entities.Player;
import com.layla.services.StatsService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.ImageCursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/** HUD lateral izquierdo: stats + salud. */
public final class HudView extends VBox {

    private final StatsService stats;
    private Player player; // opcional si necesitas salud del player
    private final DecimalFormat df1 = new DecimalFormat("0.0");
    private final DecimalFormat df0 = new DecimalFormat("0");

    private final Label lblSpeed = new Label();
    private final Label lblTears = new Label();
    private final Label lblShotSpeed = new Label();
    private final Label lblRange = new Label();
    private final Label lblDamage = new Label();
    private final HBox heartsBox = new HBox(4);

    public HudView(StatsService stats, Player player) {
        this.stats = Objects.requireNonNull(stats, "stats");
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
            statLine(icon("tears_icon.png"), "Tears", lblTears),
            statLine(icon("shotspeed_icon.png"), "Shot Spd", lblShotSpeed),
            statLine(icon("range_icon.png"), "Range", lblRange),
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
        // Isaac-style:
        // - Speed: px/s (normalizado a 2 decimales)
        // - Tears: disparos por segundo = 1 / cooldown
        // - Shot Speed: projectileSpeed
        // - Range: en px
        // - Damage: stat base
        double speed = stats.getMoveSpeed();
        double cooldown = Math.max(1e-6, stats.getFireCooldown());
        double tearsPerSec = 1.0 / cooldown;
        double shotSpeed = stats.getProjectileSpeed();
        double range = stats.getRangePixels();
        double damage = stats.getDamage();

        lblSpeed.setText(df1.format(speed));
        lblTears.setText(df1.format(tearsPerSec));
        lblShotSpeed.setText(df1.format(shotSpeed));
        lblRange.setText(df0.format(range));
        lblDamage.setText(df1.format(damage));

        // Health: si Player expone health (ej. getHealth()/getMaxHealth())
        heartsBox.getChildren().clear();
        if (player != null) {
            int hp = (int)Math.round(player.getHealth());      // ajusta a tu API real
            int max = (int)Math.round(player.getMaxHealth());  // ajusta a tu API real
            int fullHearts = hp / 2;
            boolean half = (hp % 2) == 1;
            // Dibujado muy simple con heart_icon.png repetido (mejorable con sprite sheet)
            for (int i = 0; i < fullHearts; i++) heartsBox.getChildren().add(smallHeart());
            if (half) heartsBox.getChildren().add(halfHeart());
            // Opcional: añadir corazones vacíos hasta max
            // int slots = max/2; while (heartsBox.getChildren().size() < slots) heartsBox.getChildren().add(emptyHeart());
        }
    }

    private Node smallHeart() {
        Image img = new Image(getClass().getResourceAsStream("/assets/images/hearts.png"));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(12);
        iv.setFitHeight(12);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    private Node halfHeart() {
        // Por simplicidad, usa el mismo sprite (puedes recortar con ImageView.setViewport si tu sheet lo permite)
        return smallHeart();
    }
}
