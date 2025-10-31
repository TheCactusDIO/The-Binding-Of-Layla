# Issue 18 – Generación procedural simple (selección aleatoria de layouts)

## Objetivo
Dar sensación roguelike **eligiendo aleatoriamente** entre layouts predefinidos por run.

## Checklist
- [ ] En `FloorManager`, añadir **seed** de run (Random con semilla)
- [ ] Elegir aleatoriamente el orden de las salas de una lista por piso
- [ ] Evitar repetir la misma sala consecutivamente (si hay alternativas)
- [ ] Hook para “piso completado” → ir a tienda/jefe según configuración

## Prompt Copilot
"Extend `FloorManager` to randomly select the order of predefined layouts per floor using a seeded Random for reproducibility. Avoid immediate repeats and provide hooks to branch to shop or boss rooms."

## Criterios de aceptación
- [ ] En diferentes runs, el orden de salas varía
- [ ] Con la misma seed, el orden es reproducible

## Notas de prueba
- [ ] Loggear la seed y el orden elegido para verificación
