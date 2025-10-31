# Issue 33 – (OPCIONAL) Pantalla de logros (Achievements)

## Objetivo
Añadir logros simples (ej.: derrotar miniboss, acumular X monedas, no recibir daño en una sala) y una pantalla de consulta.

## Checklist
- [ ] Tabla/archivo para logros conseguidos (SQLite o `achievements.json`)
- [ ] API: `unlockAchievement(id)`, `isUnlocked(id)`, `listAchievements()`
- [ ] Vista `achievements.fxml` con lista y estados
- [ ] Hook en eventos del juego para desbloqueos

## Prompt Copilot
"Add a simple achievements system (SQLite or JSON) with unlock/check/list APIs and `achievements.fxml` to display them."

## Criterios de aceptación
- [ ] Logros se desbloquean y persisten
- [ ] Pantalla muestra estados correctamente
