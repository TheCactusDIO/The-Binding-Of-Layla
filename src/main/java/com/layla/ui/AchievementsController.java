package com.layla.ui;

import java.util.List;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.model.AchievementDefinition;
import com.layla.services.AchievementService;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controlador de la vista de Logros.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Pintar la lista de logros disponibles.</li>
 *   <li>Permitir seleccionar un logro y mostrar su detalle.</li>
 *   <li>Ocultar información sensible (condición/recompensa) cuando el logro está bloqueado.</li>
 *   <li>Abrir/cerrar el panel de detalle con animación.</li>
 * </ul>
 *
 * <p>Notas:</p>
 * <ul>
 *   <li>No se cambian nombres de métodos ni el comportamiento general.</li>
 *   <li>La lista de logros se obtiene desde {@link AchievementService}.</li>
 *   <li>Los iconos se cargan a través de {@link AssetsManager} con un fallback por defecto.</li>
 * </ul>
 */
public class AchievementsController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================

    /** Contenedor donde se pintan los items de la lista de logros. */
    @FXML private VBox achievementListContainer;

    /** Panel lateral con el detalle del logro seleccionado. */
    @FXML private VBox detailPanel;

    /** Contenedor principal (no se usa aquí, pero es parte del FXML). */
    @FXML private HBox mainContentBox;

    /** Scroll de la lista (no se usa aquí, pero es parte del FXML). */
    @FXML private ScrollPane scrollPane;

    /** Icono del logro en el panel de detalle. */
    @FXML private ImageView detailImage;

    /** Nombre del logro en el panel de detalle. */
    @FXML private Label detailName;

    /** Descripción/condición del logro en el panel de detalle. */
    @FXML private Label detailDescription;

    /** Texto de recompensa/desbloqueo del logro en el panel de detalle. */
    @FXML private Label detailUnlockContent;

    /** Estado del logro (DESBLOQUEADO/BLOQUEADO) en el panel de detalle. */
    @FXML private Label detailStatus;

    // =========================
    // SERVICIOS / DATOS
    // =========================

    /** Servicio de logros (se toma del contexto global de la app). */
    private final AchievementService achievementService = AppContext.achievements();

    /** Lista completa de definiciones de logros (metadata para UI). */
    private final List<AchievementDefinition> allAchievements = achievementService.getAllDefinitions();

    // =========================
    // UI / ESTADO
    // =========================

    /** Nodo actualmente seleccionado en la lista (para resetear estilos). */
    private Node selectedAchievementNode = null;

    /** Ancho objetivo del panel de detalle cuando está abierto. */
    private static final double DETAIL_WIDTH = 380.0;

    /** Estilo inline para un item normal (no seleccionado). */
    private static final String ITEM_STYLE_NORMAL =
            "-fx-padding: 10 15; -fx-background-color: #222; -fx-background-radius: 8; -fx-cursor: hand;";

    /** Estilo inline para el item seleccionado. */
    private static final String ITEM_STYLE_SELECTED =
            "-fx-padding: 10 15; -fx-background-color: #444; -fx-background-radius: 8; -fx-cursor: hand;";

    // =========================
    // Textos UI
    // =========================

    private static final String TXT_DESBLOQUEADO = "DESBLOQUEADO";
    private static final String TXT_BLOQUEADO = "BLOQUEADO";
    private static final String TXT_CONDICION_OCULTA = "Bloqueado. ¡Descubre la condición!";
    private static final String TXT_RECOMPENSA_OCULTA = "Recompensa oculta.";

    // =========================
    // Efectos visuales
    // =========================

    /**
     * Efecto de "bloqueado" aplicado a iconos: blanco y negro y ligeramente más oscuro.
     * Se reutiliza para no crear nuevos objetos en cada render.
     */
    private static final ColorAdjust LOCKED_EFFECT = createLockedEffect();

    /**
     * Crea el efecto usado para representar un logro bloqueado.
     *
     * @return efecto de escala de grises con un leve ajuste de brillo
     */
    private static ColorAdjust createLockedEffect() {
        ColorAdjust ca = new ColorAdjust();
        ca.setSaturation(-1.0);   // blanco y negro
        ca.setBrightness(-0.15);  // un pelín más oscuro
        return ca;
    }

    /**
     * Hook del ciclo de vida de la vista.
     *
     * <p>Comportamiento al entrar:</p>
     * <ul>
     *   <li>Rellena la lista de logros.</li>
     *   <li>Resetea selección.</li>
     *   <li>Oculta el panel de detalle.</li>
     * </ul>
     */
    @Override
    public void onEnter() {
        populateList();
        selectedAchievementNode = null;
        hideDetailPanelImmediately();
    }

    /**
     * Pinta la lista completa de logros en el contenedor.
     */
    private void populateList() {
        achievementListContainer.getChildren().clear();
        for (AchievementDefinition def : allAchievements) {
            achievementListContainer.getChildren().add(createListItem(def));
        }
    }

    /**
     * Crea un item clickable de la lista para un logro concreto.
     *
     * @param def definición del logro
     * @return nodo visual del item (HBox)
     */
    private HBox createListItem(AchievementDefinition def) {
        boolean unlocked = achievementService.isUnlocked(def.getId());

        HBox item = new HBox(15);
        item.getStyleClass().add("achievement-list-item");
        item.setStyle(ITEM_STYLE_NORMAL);
        item.setPrefHeight(60);
        item.setAlignment(Pos.CENTER_LEFT);

        ImageView iconView = new ImageView(getAchievementIcon(def.getIconPath()));
        iconView.setFitWidth(40);
        iconView.setFitHeight(40);
        iconView.setPreserveRatio(true);

        applyLockedVisual(iconView, unlocked);

        Label nameLabel = new Label(def.getName());
        String nameColor = unlocked ? "white" : "#777";
        nameLabel.setStyle("-fx-text-fill: " + nameColor + "; -fx-font-size: 16px; -fx-font-weight: bold;");

        item.getChildren().addAll(iconView, nameLabel);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        item.setOnMouseClicked(e -> selectAchievement(item, def));

        return item;
    }

    /**
     * Aplica un estilo visual de "bloqueado" o "normal" a un icono.
     *
     * <p>Cuando está bloqueado:</p>
     * <ul>
     *   <li>Reduce opacidad.</li>
     *   <li>Aplica un efecto de escala de grises.</li>
     * </ul>
     *
     * @param iv ImageView al que aplicar el efecto
     * @param unlocked {@code true} si el logro está desbloqueado
     */
    private void applyLockedVisual(ImageView iv, boolean unlocked) {
        if (iv == null) return;

        if (unlocked) {
            iv.setOpacity(1.0);
            iv.setEffect(null);
        } else {
            iv.setOpacity(0.35);
            iv.setEffect(LOCKED_EFFECT);
        }
    }

    /**
     * Selecciona visualmente un logro de la lista y muestra su panel de detalle.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Quita el estilo de seleccionado del item anterior.</li>
     *   <li>Marca el nuevo item como seleccionado.</li>
     *   <li>Actualiza el panel de detalle con {@link #showDetails(AchievementDefinition, boolean)}.</li>
     *   <li>Abre el panel si está cerrado.</li>
     * </ul>
     *
     * @param item nodo del item clicado
     * @param def definición del logro
     */
    private void selectAchievement(Node item, AchievementDefinition def) {
        boolean unlocked = achievementService.isUnlocked(def.getId());

        if (selectedAchievementNode != null) {
            selectedAchievementNode.setStyle(ITEM_STYLE_NORMAL);
        }

        selectedAchievementNode = item;
        item.setStyle(ITEM_STYLE_SELECTED);

        showDetails(def, unlocked);

        if (!detailPanel.isVisible() || detailPanel.getPrefWidth() < DETAIL_WIDTH) {
            openDetailPanelAnimated();
        }
    }

    /**
     * Evento FXML: cerrar el panel de detalles.
     */
    @FXML
    private void onCloseDetails() {
        closeDetailPanelAnimated();
    }

    /**
     * Actualiza los campos del panel de detalle según si el logro está bloqueado o desbloqueado.
     *
     * <p>Regla de ocultación:</p>
     * <ul>
     *   <li>Si está bloqueado, no se muestran condición real ni recompensa real.</li>
     * </ul>
     *
     * @param def definición del logro
     * @param unlocked {@code true} si está desbloqueado
     */
    private void showDetails(AchievementDefinition def, boolean unlocked) {
        detailName.setText(def.getName());

        // Si está bloqueado, no spoileamos la condición.
        detailDescription.setText(unlocked ? def.getDescription() : TXT_CONDICION_OCULTA);

        // Si está bloqueado, no spoileamos la recompensa.
        detailUnlockContent.setText(unlocked ? def.getUnlockContent() : TXT_RECOMPENSA_OCULTA);

        detailStatus.setText(unlocked ? TXT_DESBLOQUEADO : TXT_BLOQUEADO);
        detailStatus.setStyle("-fx-text-fill: " + (unlocked ? "#00aa00" : "#aa0000") + ";");

        Image icon = getAchievementIcon(def.getIconPath());
        detailImage.setImage(icon);
        applyLockedVisual(detailImage, unlocked);
    }

    /**
     * Carga el icono de un logro.
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Normaliza la ruta (quita '/' inicial si existe).</li>
     *   <li>Intenta cargar con {@link AssetsManager#loadImage(String)}.</li>
     *   <li>Si falla, usa un icono por defecto dentro de resources.</li>
     * </ol>
     *
     * @param path ruta del icono (puede venir con o sin '/' al inicio)
     * @return {@link Image} cargada o {@code null} si incluso el fallback falla
     */
    private Image getAchievementIcon(String path) {
        String normalized = normalizeResourcePath(path);

        Image img = AssetsManager.loadImage(normalized);
        if (img != null) return img;

        // Fallback
        try {
            return new Image(getClass().getResourceAsStream("/assets/images/achievements/default.png"));
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Evento FXML: volver al menú principal.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Cierra el panel de detalles.</li>
     *   <li>Navega al menú principal manteniendo el tamaño (con fade).</li>
     * </ul>
     */
    @FXML
    private void onBack() {
        onCloseDetails();
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }

    /**
     * Abre el panel de detalle con animación (ancho y opacidad).
     */
    private void openDetailPanelAnimated() {
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(detailPanel.prefWidthProperty(), DETAIL_WIDTH),
                        new KeyValue(detailPanel.opacityProperty(), 1.0)
                )
        );
        timeline.play();
    }

    /**
     * Cierra el panel de detalle con animación y resetea la selección al finalizar.
     */
    private void closeDetailPanelAnimated() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(detailPanel.prefWidthProperty(), 0),
                        new KeyValue(detailPanel.opacityProperty(), 0.0)
                )
        );

        timeline.setOnFinished(e -> {
            detailPanel.setVisible(false);
            detailPanel.setManaged(false);

            if (selectedAchievementNode != null) {
                selectedAchievementNode.setStyle(ITEM_STYLE_NORMAL);
            }
            selectedAchievementNode = null;
        });

        timeline.play();
    }

    /**
     * Oculta el panel de detalle inmediatamente (sin animación).
     * Útil al entrar en la vista para garantizar estado inicial consistente.
     */
    private void hideDetailPanelImmediately() {
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        detailPanel.setPrefWidth(0);
        detailPanel.setOpacity(0);
    }

    /**
     * Normaliza una ruta de recurso para uso con {@link AssetsManager}.
     *
     * <p>Reglas:</p>
     * <ul>
     *   <li>Si es {@code null} o vacío, devuelve la ruta del icono por defecto.</li>
     *   <li>Si empieza por '/', se elimina ese primer carácter.</li>
     * </ul>
     *
     * @param path ruta original
     * @return ruta normalizada y segura
     */
    private String normalizeResourcePath(String path) {
        if (path == null) return "assets/images/achievements/default.png";
        String p = path.trim();
        if (p.isEmpty()) return "assets/images/achievements/default.png";
        return p.startsWith("/") ? p.substring(1) : p;
    }
}
