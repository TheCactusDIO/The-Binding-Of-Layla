# Issue 22 – Guardado asíncrono (puntuación/progreso)

## Objetivo
Guardar puntuaciones y progreso a SQLite en **segundo plano** tras Game Over.

## Checklist
- [ ] En `HighScoreService`, exponer método `saveScoreAsync(...)` con `CompletableFuture.runAsync(...)`
- [ ] Manejar excepciones y loguear errores sin bloquear la UI
- [ ] UI muestra un pequeño indicador “Guardando...” (opcional) y se cierra al completarse
- [ ] Confirmar que el hilo no queda colgado al salir del juego

## Prompt Copilot
"Use `CompletableFuture` to save player progress and high scores asynchronously on Game Over. Do not block the UI, and handle exceptions properly."

## Criterios de aceptación
- [ ] Guardado sin bloquear la UI
- [ ] Datos insertados correctamente en la BD

## Notas de prueba
- [ ] Simular alta latencia (sleep) y verificar que la UI responde
