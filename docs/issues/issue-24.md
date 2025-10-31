# Issue 24 – HUD y barra de vida (bindings)

## Objetivo
Mostrar **vida** (corazones/barra), **monedas** y **puntuación** con **bindings** para actualizar automáticamente.

## Checklist
- [ ] En `Player`: usar `IntegerProperty`/`DoubleProperty` para health/coins
- [ ] En `GameController`: `Label`s/`ProgressBar` enlazados a propiedades del Player
- [ ] Actualizar puntuación en el HUD (score Property) conforme avance la partida
- [ ] Sincronizar cambios sin `Platform.runLater` (si se actualiza en JavaFX thread)

## Prompt Copilot
"Add a health bar (or hearts), coin counter, and score label to the HUD, binding them to player properties so the UI auto-updates."

## Criterios de aceptación
- [ ] El HUD refleja cambios en tiempo real
- [ ] No hay flickering ni actualización manual innecesaria

## Notas de prueba
- [ ] Modificar health/coins en tiempo real para ver los cambios en el HUD
