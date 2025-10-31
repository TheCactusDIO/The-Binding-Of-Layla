# Issue 5 – Escena de juego (game.fxml + HUD)

## Objetivo
Crear `game.fxml` con contenedor de juego (Pane/Canvas) y HUD (score, floor, health). Añadir `GameController.java`.

## Checklist
- [ ] Crear `src/main/resources/ui/game.fxml` con contenedor de juego (Pane o Canvas) y HUD (Labels: scoreLabel, floorLabel, healthLabel)
- [ ] Crear `src/main/java/com/layla/ui/GameController.java` con `@FXML` a HUD y root
- [ ] Preparar `initialize()` y métodos `onEnter()` / `onExit()` (opcional) para ciclo de vida
- [ ] Integrar con `App.java` para poder cargar esta escena

## Prompt Copilot
"Create `game.fxml` with a Pane (or Canvas) for the play area and a HUD bar with labels for score, floor, and health. Implement `GameController.java` with @FXML bindings and basic lifecycle methods."

## Criterios de aceptación
- [ ] `game.fxml` carga sin errores y los nodos HUD son accesibles
- [ ] `App.java` puede navegar a la escena Game

## Notas de prueba
- [ ] Probar navegación Menú → Game y back
