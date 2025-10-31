# Issue 17 – Sistema de pisos (FloorManager + layouts predefinidos)

## Objetivo
Crear un sistema de **avance por pisos** usando **layouts predefinidos** (sin procedural complejo).

## Checklist
- [ ] Crear `src/main/java/com/layla/core/FloorManager.java`
- [ ] Definir 10–20 layouts en `src/main/resources/rooms/*.json` (muros, spawns, puertas)
- [ ] Cargar JSON y convertir a entidades/colisionadores
- [ ] Método `loadRoom(String id)` y `loadNextRoom()`
- [ ] Integración con Game: al tocar salida/puerta → `loadNextRoom()`

## Prompt Copilot
"Implement `FloorManager` that loads predefined room layouts from JSON files under `resources/rooms/*.json`. Provide `loadRoom(id)` and `loadNextRoom()` and integrate with the game to switch rooms when the player reaches the exit."

## Criterios de aceptación
- [ ] Carga de JSON a objetos internos sin errores
- [ ] Cambio de sala al llegar a la salida

## Notas de prueba
- [ ] Crear 2–3 JSON simples y alternar manualmente `loadRoom(...)`
