# Issue 4 – Menú principal (Play / Settings / Exit)

## Objetivo
Crear `main_menu.fxml` y `MainMenuController.java` con botones **Play**, **Settings**, **Exit**, y navegación desde `App.java`.

## Checklist
- [ ] Crear `src/main/resources/ui/main_menu.fxml` (VBox / botones: Play, Settings, Exit)
- [ ] Crear `src/main/java/com/layla/ui/MainMenuController.java`
- [ ] Conectar `@FXML` y handlers: Play → escena Game (placeholder), Settings → escena Settings (placeholder), Exit → cerrar app
- [ ] Añadir método en `App.java` para cambiar escenas de forma centralizada (`loadScene(String fxml, int w, int h)`)
- [ ] Cargar `main_menu.fxml` en el arranque (tras Loading Screen)

## Prompt Copilot
"Create `main_menu.fxml` and `MainMenuController.java` with Play, Settings and Exit buttons. Implement a centralized `loadScene(fxmlPath,width,height)` in `App.java` and wire handlers to switch to the Game and Settings scenes, and to exit the app."

## Criterios de aceptación
- [ ] La app muestra el **menú principal** sin errores
- [ ] Los botones existen y llaman a sus handlers
- [ ] `App.java` puede cambiar de escena con un método reutilizable

## Notas de prueba
- [ ] Cambiar a Game y volver (si no existe, usar placeholder temporal)
- [ ] Cerrar app desde Exit sin excepciones
