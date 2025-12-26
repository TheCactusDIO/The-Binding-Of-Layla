package com.layla.ui;

import java.util.List;

import com.layla.AppContext;
import com.layla.core.AssetsManager;
import com.layla.items.ItemDefinition;
import com.layla.items.ItemPoolType;
import com.layla.items.ItemRegistry;
import com.layla.model.AchievementDefinition;
import com.layla.model.StatModifier;
import com.layla.services.AchievementService;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Pantalla "Items" dentro de Collection.
 *
 * Objetivo:
 *  - Mostrar un grid de items (desbloqueados / bloqueados).
 *  - Al hacer click, mostrar un panel de detalles.
 *
 * Nota de limpieza:
 *  - Evitamos inline-style duplicado usando constantes.
 *  - Arreglamos un detalle importante de layout: si el panel está invisible pero "managed",
 *    sigue ocupando espacio. Aquí lo gestionamos con setManaged(false/true).
 */
public class ItemsController implements ViewLifecycle {

    // =========================
    // FXML
    // =========================
    @FXML private FlowPane gridPane;
    @FXML private VBox detailsPanel;

    // Panel detalles
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label detailDescription;
    @FXML private Label detailStats;
    @FXML private Label detailPool;

    // =========================
    // Servicios
    // =========================
    private final AchievementService achievements = AppContext.achievements();

    // =========================
    // Constantes UI
    // =========================
    private static final double ICON_BOX_SIZE = 64.0;
    private static final double ICON_IMAGE_SIZE = 48.0;

    private static final String STYLE_ICON_NORMAL =
            "-fx-background-color: #333; -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8; -fx-cursor: hand;";
    private static final String STYLE_ICON_HOVER =
            "-fx-background-color: #444; -fx-background-radius: 8; -fx-border-color: #ffd54f; -fx-border-radius: 8; -fx-cursor: hand;";

    private static final String STYLE_UNKNOWN_LABEL =
            "-fx-text-fill: #555; -fx-font-size: 24px; -fx-font-weight: bold;";

    // Estilos del panel detalle (temporal, luego lo pasarás a CSS)
    private static final String STYLE_TITLE_UNLOCKED =
            "-fx-text-fill: #ffd54f; -fx-font-family: 'Upheaval TT (BRK)'; -fx-font-size: 24px;";
    private static final String STYLE_TITLE_LOCKED =
            "-fx-text-fill: #aa0000; -fx-font-family: 'Upheaval TT (BRK)'; -fx-font-size: 24px;";

    private static final String TXT_LOCKED_ITEM = "OBJETO BLOQUEADO";
    private static final String TXT_POOL_UNKNOWN = "ORIGEN: ???";

    // Comparación segura para multiplicadores
    private static final double EPS = 1e-9;

    /**
     * Lifecycle: se llama al entrar en la pestaña.
     * Recarga el grid por si han cambiado logros/desbloqueos.
     */
    @Override
    public void onEnter() {
        reloadGrid();
        hideDetailsPanel();
    }

    /**
     * Reconstruye el grid completo de items.
     * Mantenerlo simple reduce estados raros (items antiguos, listeners duplicados, etc.).
     */
    private void reloadGrid() {
        if (gridPane == null) return;

        gridPane.getChildren().clear();

        List<ItemDefinition> allItems = ItemRegistry.allDefinitions();
        if (allItems == null || allItems.isEmpty()) {
            return;
        }

        for (ItemDefinition def : allItems) {
            if (def == null) continue;
            gridPane.getChildren().add(createItemIcon(def));
        }
    }

    /**
     * Crea un icono clicable para el item:
     *  - Si está desbloqueado: muestra imagen.
     *  - Si está bloqueado: muestra "?".
     *  - Hover común.
     *  - Click abre detalles (tanto locked como unlocked).
     */
    private StackPane createItemIcon(ItemDefinition def) {
        boolean unlocked = isUnlocked(def);

        StackPane iconRoot = new StackPane();
        iconRoot.setPrefSize(ICON_BOX_SIZE, ICON_BOX_SIZE);
        iconRoot.setStyle(STYLE_ICON_NORMAL);

        if (unlocked) {
            Image img = loadItemIcon(def);
            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(ICON_IMAGE_SIZE);
                iv.setFitHeight(ICON_IMAGE_SIZE);
                iv.setPreserveRatio(true);
                iconRoot.getChildren().add(iv);
            } else {
                // Fallback: si no carga icono, mostramos "?"
                iconRoot.getChildren().add(createUnknownLabel());
            }
        } else {
            iconRoot.getChildren().add(createUnknownLabel());
        }

        // Hover (sin tocar CSS todavía)
        iconRoot.setOnMouseEntered(e -> iconRoot.setStyle(STYLE_ICON_HOVER));
        iconRoot.setOnMouseExited(e -> iconRoot.setStyle(STYLE_ICON_NORMAL));

        // Click -> detalles
        iconRoot.setOnMouseClicked(e -> showDetails(def));

        return iconRoot;
    }

    /**
     * Determina si un item está desbloqueado:
     *  - Si se desbloquea por defecto => true.
     *  - Si requiere logro => consultamos AchievementService.
     *
     * IMPORTANTE: requiredAchievementId puede ser null; lo tratamos como "bloqueado"
     * (o si prefieres, podrías tratarlo como "desbloqueado" según tu diseño).
     */
    private boolean isUnlocked(ItemDefinition def) {
        if (def == null) return false;
        if (def.isUnlockedByDefault()) return true;

        String achId = def.getRequiredAchievementId();
        if (achId == null || achId.isBlank()) return false;

        return achievements.isUnlocked(achId);
    }

    /**
     * Muestra el panel de detalles del item:
     *  - Si unlocked: muestra nombre, descripción, pool, imagen, stats.
     *  - Si locked: oculta datos sensibles y muestra condición basada en el logro requerido.
     */
    private void showDetails(ItemDefinition def) {
        if (def == null) return;

        showDetailsPanel();

        boolean unlocked = isUnlocked(def);
        if (unlocked) {
            renderUnlockedDetails(def);
        } else {
            renderLockedDetails(def);
        }
    }

    /**
     * Render del panel de detalles para item desbloqueado.
     */
    private void renderUnlockedDetails(ItemDefinition def) {
        // Título
        if (detailName != null) {
            detailName.setText(safe(def.getName()));
            detailName.setStyle(STYLE_TITLE_UNLOCKED);
        }

        // Descripción
        if (detailDescription != null) {
            detailDescription.setText(safe(def.getDescription()));
            detailDescription.setStyle("-fx-text-fill: white; -fx-font-style: italic;");
        }

        // Pool
        if (detailPool != null) {
            String pool = poolLabel(def.getPoolType());
            detailPool.setText("ORIGEN: " + pool);
        }

        // Imagen
        if (detailImage != null) {
            Image img = loadItemIcon(def);
            detailImage.setImage(img);
        }

        // Stats
        if (detailStats != null) {
            detailStats.setText(buildStatsText(def));
        }
    }

    /**
     * Render del panel de detalles para item bloqueado.
     * Aquí intentamos NO filtrar información del item y centrarnos en la condición.
     */
    private void renderLockedDetails(ItemDefinition def) {
        if (detailName != null) {
            detailName.setText(TXT_LOCKED_ITEM);
            detailName.setStyle(STYLE_TITLE_LOCKED);
        }

        if (detailPool != null) {
            detailPool.setText(TXT_POOL_UNKNOWN);
        }

        if (detailImage != null) {
            detailImage.setImage(null);
        }

        if (detailStats != null) {
            detailStats.setText("");
        }

        if (detailDescription != null) {
            detailDescription.setText(buildLockedConditionText(def));
            detailDescription.setStyle("-fx-text-fill: #aaaaaa;");
        }
    }

    /**
     * Construye el texto de stats a partir de los modifiers del item.
     * Si no hay stats, devolvemos un string amigable.
     */
    private String buildStatsText(ItemDefinition def) {
        List<StatModifier> mods = def.getModifiers();
        if (mods == null || mods.isEmpty()) return "-";

        StringBuilder sb = new StringBuilder();
        for (StatModifier mod : mods) {
            if (mod == null || mod.getStatId() == null) continue;

            // Aditivo
            double add = mod.getAdditive();
            if (Math.abs(add) > EPS) {
                String sign = (add > 0) ? "+" : "";
                sb.append(String.format("%s%.1f %s%n", sign, add, mod.getStatId().name()));
            }

            // Multiplicativo (si es distinto de 1.0)
            double mul = mod.getMultiplicative();
            if (Math.abs(mul - 1.0) > EPS) {
                sb.append(String.format("x%.2f %s%n", mul, mod.getStatId().name()));
            }
        }

        String out = sb.toString().trim();
        return out.isEmpty() ? "-" : out;
    }

    /**
     * Construye el texto de condición para un item bloqueado.
     * Si existe un logro requerido, muestra nombre y descripción del logro.
     */
    private String buildLockedConditionText(ItemDefinition def) {
        String achId = def.getRequiredAchievementId();
        if (achId == null || achId.isBlank()) {
            return "Este objeto está bloqueado por causas misteriosas.";
        }

        AchievementDefinition achDef = achievements.getDefinition(achId);
        if (achDef == null) {
            return "Condición de desbloqueo desconocida.";
        }

        return "CONDICIÓN DE DESBLOQUEO:\n\n"
                + "Logro: " + safe(achDef.getName()) + "\n"
                + "Requisito: " + safe(achDef.getDescription());
    }

    private static String poolLabel(ItemPoolType poolType) {
        if (poolType == null) return "???";
        return switch (poolType) {
            case SHOP -> "TIENDA";
            case BOSS -> "JEFE";
            default -> poolType.name();
        };
    }

    /**
     * Carga el icono del item desde ItemRegistry/AssetsManager.
     * Devuelve null si no existe o falla.
     */
    private Image loadItemIcon(ItemDefinition def) {
        try {
            if (def == null) return null;

            String path = ItemRegistry.getIconPath(def.getId());
            if (path == null || path.isBlank()) return null;

            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            return AssetsManager.loadImage(cleanPath);

        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Oculta el panel de detalles y lo saca del layout para que NO reserve espacio.
     * (Esto arregla el comportamiento de “panel fantasma” a la derecha.)
     */
    private void hideDetailsPanel() {
        if (detailsPanel == null) return;
        detailsPanel.setVisible(false);
        detailsPanel.setManaged(false);
    }

    /**
     * Muestra el panel de detalles y lo vuelve a incluir en el layout.
     */
    private void showDetailsPanel() {
        if (detailsPanel == null) return;
        detailsPanel.setManaged(true);
        detailsPanel.setVisible(true);
    }

    /**
     * Crea el label "?" reutilizable para items bloqueados/fallback.
     */
    private Label createUnknownLabel() {
        Label q = new Label("?");
        q.setStyle(STYLE_UNKNOWN_LABEL);
        return q;
    }

    /**
     * Evita nulls en textos (UI más robusta).
     */
    private static String safe(String s) {
        return (s != null) ? s : "";
    }
}
