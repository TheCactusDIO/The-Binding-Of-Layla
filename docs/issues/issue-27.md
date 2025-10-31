# Issue 27 – Ajustes (Settings) con persistencia local

## Objetivo
Cambiar **volumen**, **nombre del jugador** y (opcional) **resolución/escala**, guardando en `config.json`.

## Checklist
- [ ] Crear `src/main/resources/ui/settings.fxml` y `src/main/java/com/layla/ui/SettingsController.java`
- [ ] Campos: nombre del jugador (TextField), volumen música/SFX (Sliders), escala/resolución (ChoiceBox opcional)
- [ ] Crear `src/main/java/com/layla/services/ConfigService.java` para leer/escribir `config.json`
- [ ] Aplicar volumen en tiempo real mediante `AudioService`
- [ ] Guardar al pulsar “Save” y volver al Menú

## Prompt Copilot
"Create a `settings.fxml` scene to edit player name and audio volumes (and optional scale). Persist to `config.json` via a `ConfigService`, and apply audio volume live."

## Criterios de aceptación
- [ ] Cambios se reflejan de inmediato (volumen)
- [ ] Al reabrir el juego, se cargan los ajustes desde `config.json`

## Notas de prueba
- [ ] Verificar que `config.json` se crea y actualiza correctamente
