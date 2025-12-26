package com.layla.ui;

import java.util.List;
import java.util.Map;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.db.DatabaseService;
import com.layla.model.EnemyProfile;
import com.layla.model.EnemyType;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Controlador de la vista "Bestiario".
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Cargar estadísticas de enemigos del perfil actual desde {@link DatabaseService}.</li>
 *   <li>Pintar una cuadrícula de iconos (enemigos normales + jefes) y permitir selección.</li>
 *   <li>Mostrar un panel de detalles con estadísticas y atributos (si aplica).</li>
 *   <li>Ocultar detalles cuando no hay selección.</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>No se cambian nombres de métodos ni el comportamiento.</li>
 *   <li>Los jefes se manejan por ID (String) y no tienen {@link EnemyType} asociado, por eso sus atributos se muestran como "-".</li>
 *   <li>Las imágenes se buscan con el patrón: {@code assets/images/enemies/<ID>.png}.</li>
 * </ul>
 */
public class BestiaryController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================

    /** Contenedor de iconos del bestiario. */
    @FXML private FlowPane gridPane;

    /** Panel lateral de detalles del enemigo seleccionado. */
    @FXML private VBox detailsPanel;

    /** Imagen del enemigo seleccionado. */
    @FXML private ImageView detailImage;

    /** Nombre del enemigo seleccionado. */
    @FXML private Label detailName;

    /** Estadísticas persistidas en BD: visto, kills, muertes provocadas. */
    @FXML private Label statSeen, statKills, statDeaths;

    /** Atributos del balance (solo para enemigos normales). */
    @FXML private Label attrHp, attrDmg, attrSpeed, attrProjDmg;

    // =========================
    // ESTADO
    // =========================

    /** Mapa de estadísticas por ID de enemigo (cargado de BD para el perfil activo). */
    private Map<String, DatabaseService.EnemyStatEntry> statsMap;

    /** Icono actualmente seleccionado (para resetear estilos y mantener selección). */
    private StackPane selectedIcon = null;

    // =========================
    // CONSTANTES UI
    // =========================

    private static final double ICON_BOX_SIZE = 64.0;
    private static final double ICON_IMAGE_SIZE = 48.0;
    private static final double FALLBACK_RECT_SIZE = 40.0;

    private static final double DETAIL_WIDTH = 380.0;

    private static final String STYLE_ICON_NORMAL =
            "-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;";
    private static final String STYLE_ICON_HOVER =
            "-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;";
    private static final String STYLE_ICON_SELECTED =
            "-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-width: 2; -fx-border-radius: 8; -fx-cursor: hand;";

    private static final String STYLE_UNKNOWN_LABEL =
            "-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;";

    private static final String TXT_NO_DESCUBIERTO = "Sin descubrir";

    /** IDs de jefes (no necesariamente mapean a {@link EnemyType}). */
    private static final List<String> BOSS_IDS = List.of(
            "BOSS_FLOOR_1", "BOSS_FLOOR_2", "BOSS_FLOOR_3", "BOSS_FLOOR_4", "BOSS_FLOOR_5"
    );

    /**
     * Hook del ciclo de vida: se ejecuta al entrar en la vista.
     * Carga datos del perfil actual y repinta la cuadrícula.
     */
    @Override
    public void onEnter() {
        loadData();
    }

    /**
     * Carga datos del perfil y reconstruye el contenido de la vista.
     *
     * <p>Incluye:</p>
     * <ul>
     *   <li>Lectura de stats desde BD (por perfil activo).</li>
     *   <li>Reset de selección y panel de detalles.</li>
     *   <li>Creación de iconos de enemigos normales ({@link EnemyType#values()}).</li>
     *   <li>Creación de iconos de jefes por ID.</li>
     * </ul>
     */
    private void loadData() {
        int profileId = AppContext.getProfileId();
        statsMap = AppContext.db().getAllEnemyStats(profileId);

        gridPane.getChildren().clear();
        selectedIcon = null;
        hideDetailsPanelImmediately();

        // Enemigos normales
        for (EnemyType type : EnemyType.values()) {
            createEnemyIcon(type.name(), false);
        }

        // Jefes
        for (String bossId : BOSS_IDS) {
            createEnemyIcon(bossId, true);
        }
    }

    /**
     * Crea y añade un icono al grid para el enemigo indicado.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Si el enemigo está "visto" (seen &gt; 0), se muestra imagen (si existe) o un fallback de color.</li>
     *   <li>Si no está "visto", se muestra "?" con tooltip "Sin descubrir" y no es clicable para detalles.</li>
     * </ul>
     *
     * @param id identificador del enemigo (para normales coincide con {@link EnemyType#name()}; para jefes es un String)
     * @param isBoss {@code true} si el ID corresponde a un jefe
     */
    private void createEnemyIcon(String id, boolean isBoss) {
        DatabaseService.EnemyStatEntry entry = (statsMap != null) ? statsMap.get(id) : null;

        int seenCount = (entry != null) ? entry.seen() : 0;
        int killedCount = (entry != null) ? entry.killed() : 0;
        int deathsBy = (entry != null) ? entry.killedBy() : 0;

        boolean seen = seenCount > 0;

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(ICON_BOX_SIZE, ICON_BOX_SIZE);
        iconRoot.setStyle(STYLE_ICON_NORMAL);

        if (seen) {
            Image img = loadEnemyImage(id);

            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(ICON_IMAGE_SIZE);
                iv.setFitHeight(ICON_IMAGE_SIZE);
                iv.setPreserveRatio(true);
                iconRoot.getChildren().add(iv);
            } else {
                Color c = isBoss ? Color.DARKRED : getColorForType(id);
                iconRoot.getChildren().add(new Rectangle(FALLBACK_RECT_SIZE, FALLBACK_RECT_SIZE, c));
            }

            // Hover (sin pisar selección)
            iconRoot.setOnMouseEntered(e -> {
                if (iconRoot != selectedIcon) iconRoot.setStyle(STYLE_ICON_HOVER);
            });
            iconRoot.setOnMouseExited(e -> {
                if (iconRoot != selectedIcon) iconRoot.setStyle(STYLE_ICON_NORMAL);
            });

            // Click -> detalles
            iconRoot.setOnMouseClicked(e -> {
                selectIcon(iconRoot);
                showDetails(id, isBoss, seenCount, killedCount, deathsBy);
                openDetailsPanelAnimated();
            });

        } else {
            Label q = new Label("?");
            q.setStyle(STYLE_UNKNOWN_LABEL);
            iconRoot.getChildren().add(q);
            Tooltip.install(iconRoot, new Tooltip(TXT_NO_DESCUBIERTO));
        }

        gridPane.getChildren().add(iconRoot);
    }

    /**
     * Actualiza visualmente el icono seleccionado.
     *
     * <p>Restaura el estilo del icono anterior y marca el nuevo como seleccionado.</p>
     *
     * @param icon icono a seleccionar
     */
    private void selectIcon(StackPane icon) {
        if (selectedIcon != null) {
            selectedIcon.setStyle(STYLE_ICON_NORMAL);
        }
        selectedIcon = icon;
        selectedIcon.setStyle(STYLE_ICON_SELECTED);
    }

    /**
     * Muestra los detalles del enemigo seleccionado.
     *
     * <p>Incluye:</p>
     * <ul>
     *   <li>Nombre basado en el ID (reemplaza '_' por espacio).</li>
     *   <li>Imagen (si existe), o vacío si no hay recurso.</li>
     *   <li>Estadísticas del perfil desde BD.</li>
     *   <li>Atributos del balance (solo si no es jefe).</li>
     * </ul>
     *
     * <p>Nota: para jefes, no se asumen atributos porque no hay {@link EnemyType} directo.</p>
     *
     * @param id identificador del enemigo
     * @param isBoss indica si es jefe
     * @param seen número de veces visto
     * @param kills número de veces derrotado por el jugador
     * @param deathsBy número de muertes del jugador causadas por este enemigo
     */
    private void showDetails(String id, boolean isBoss, int seen, int kills, int deathsBy) {
        detailName.setText(displayNameFor(id, isBoss));

        // Imagen detalle
        Image img = loadEnemyImage(id);
        if (img != null && !img.isError()) {
            detailImage.setImage(img);
        } else {
            detailImage.setImage(null);
        }

        // Stats DB
        statSeen.setText(String.valueOf(seen));
        statKills.setText(String.valueOf(kills));
        statDeaths.setText(String.valueOf(deathsBy));

        // Atributos balance
        if (isBoss) {
            // No inventamos stats si no tenemos perfil por ID
            setAttrs("-", "-", "-", "-");
            return;
        }

        try {
            EnemyProfile profile = AppContext.balance().profile(EnemyType.valueOf(id));
            if (profile != null) {
                attrHp.setText(String.format("%.0f", profile.baseHp));
                attrDmg.setText(String.format("%.1f", profile.contactDmg));
                attrSpeed.setText(String.format("%.0f", profile.speed));
                attrProjDmg.setText(String.format("%.1f", profile.projDamage));
            } else {
                setAttrs("-", "-", "-", "-");
            }
        } catch (Exception ignore) {
            setAttrs("-", "-", "-", "-");
        }
    }

    private static String displayNameFor(String id, boolean isBoss) {
        if (id == null || id.isBlank()) return "-";
        if (isBoss) {
            return switch (id) {
                case "BOSS_FLOOR_1" -> "JEFE DEL PISO 1";
                case "BOSS_FLOOR_2" -> "JEFE DEL PISO 2";
                case "BOSS_FLOOR_3" -> "JEFE DEL PISO 3";
                case "BOSS_FLOOR_4" -> "JEFE DEL PISO 4";
                case "BOSS_FLOOR_5" -> "JEFE DEL PISO 5";
                default -> "JEFE";
            };
        }

        return switch (id) {
            case "SHOOTER" -> "TIRADOR";
            case "MELEE" -> "CUERPO A CUERPO";
            case "TURRET" -> "TORRETA";
            case "TANK" -> "TANQUE";
            case "KAMIKAZE" -> "KAMIKAZE";
            default -> id.replace("_", " ");
        };
    }

    /**
     * Asigna los textos de atributos en el panel de detalles.
     *
     * @param hp vida base
     * @param dmg daño de contacto
     * @param proj daño del proyectil
     * @param speed velocidad
     */
    private void setAttrs(String hp, String dmg, String proj, String speed) {
        attrHp.setText(hp);
        attrDmg.setText(dmg);
        attrProjDmg.setText(proj);
        attrSpeed.setText(speed);
    }

    /**
     * Carga la imagen del enemigo desde assets.
     *
     * <p>Se mantiene el naming indicado: {@code assets/images/enemies/<ID>.png}</p>
     *
     * @param id identificador del enemigo
     * @return imagen cargada o {@code null} si no existe o falla la carga
     */
    private Image loadEnemyImage(String id) {
        String path = "assets/images/enemies/" + id + ".png";
        try {
            return AssetsManager.loadImage(path);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Devuelve un color de fallback para enemigos normales según su {@link EnemyType}.
     *
     * <p>Si el ID no corresponde a ningún {@link EnemyType}, se devuelve gris.</p>
     *
     * @param id identificador del enemigo
     * @return color representativo del tipo o gris por defecto
     */
    private Color getColorForType(String id) {
        try {
            return switch (EnemyType.valueOf(id)) {
                case SHOOTER -> Color.ORANGE;
                case MELEE -> Color.CRIMSON;
                case TURRET -> Color.DODGERBLUE;
                case TANK -> Color.DARKOLIVEGREEN;
                case KAMIKAZE -> Color.MAGENTA;
            };
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    /**
     * Abre el panel de detalles con animación (ancho + opacidad).
     */
    private void openDetailsPanelAnimated() {
        if (detailsPanel.isVisible() && detailsPanel.getPrefWidth() >= DETAIL_WIDTH) return;

        detailsPanel.setVisible(true);
        detailsPanel.setManaged(true);

        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(220),
                        new KeyValue(detailsPanel.prefWidthProperty(), DETAIL_WIDTH),
                        new KeyValue(detailsPanel.opacityProperty(), 1.0)
                )
        );
        t.play();
    }

    /**
     * Cierra el panel de detalles con animación y resetea selección al finalizar.
     */
    private void closeDetailsPanelAnimated() {
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(detailsPanel.prefWidthProperty(), 0),
                        new KeyValue(detailsPanel.opacityProperty(), 0.0)
                )
        );

        t.setOnFinished(e -> {
            detailsPanel.setVisible(false);
            detailsPanel.setManaged(false);

            if (selectedIcon != null) {
                selectedIcon.setStyle(STYLE_ICON_NORMAL);
                selectedIcon = null;
            }
        });

        t.play();
    }

    /**
     * Oculta el panel de detalles inmediatamente (sin animación) y resetea valores visibles.
     *
     * <p>Se usa al entrar en la vista o al recargar datos para asegurar estado consistente.</p>
     */
    private void hideDetailsPanelImmediately() {
        detailsPanel.setVisible(false);
        detailsPanel.setManaged(false);
        detailsPanel.setPrefWidth(0);
        detailsPanel.setOpacity(0.0);

        detailImage.setImage(null);
        detailName.setText("SELECCIONA UN ENEMIGO");
        statSeen.setText("0");
        statKills.setText("0");
        statDeaths.setText("0");
        setAttrs("-", "-", "-", "-");
    }

    /**
     * Evento FXML: cierra el panel de detalles.
     */
    @FXML
    private void onCloseDetails() {
        closeDetailsPanelAnimated();
    }

    /**
     * Evento FXML: vuelve al menú principal.
     */
    @FXML
    private void onBack() {
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
