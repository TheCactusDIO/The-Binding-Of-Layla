# Issue 25 – Pantalla de Game Over (guardar score)

## Objetivo
Mostrar pantalla de **Game Over** con resumen de puntuación y botones **Retry** / **Main Menu**. Guardar puntuación.

## Checklist
- [ ] Crear `src/main/resources/ui/game_over.fxml` y `src/main/java/com/layla/ui/GameOverController.java`
- [ ] Mostrar score final y estado (victoria/derrota)
- [ ] Botón Retry → reiniciar run (cargar primer piso/sala)
- [ ] Botón Main Menu → volver al menú principal
- [ ] Llamar a `HighScoreService.saveScoreAsync(...)` al entrar en la pantalla

## Prompt Copilot
"Create `game_over.fxml` with a controller that shows final score and victory/defeat state, with Retry and Main Menu buttons. Save the score asynchronously on enter."

## Criterios de aceptación
- [ ] Pantalla aparece tras morir o ganar
- [ ] Guardado de puntuación ocurre sin bloquear la UI

## Notas de prueba
- [ ] Forzar entrada a Game Over desde un atajo para validar la vista
