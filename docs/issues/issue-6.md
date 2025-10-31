# Issue 6 – Bucle de juego (AnimationTimer)

## Objetivo
Implementar un `GameLoop` con `AnimationTimer` para `update(delta)` y `render()` (si usas Canvas). Integrarlo desde `GameController`.

## Checklist
- [ ] Crear `src/main/java/com/layla/core/GameLoop.java` con `AnimationTimer`
- [ ] Calcular `deltaTime` (nanosegundos → segundos)
- [ ] Exponer callbacks: `onUpdate(double delta)` y `onRender(GraphicsContext g)` (si Canvas), o solo `onUpdate` si se usa escena con nodos
- [ ] Desde `GameController`, iniciar el loop al entrar y pararlo al salir

## Prompt Copilot
"Implement a `GameLoop` using `AnimationTimer` that computes `deltaTime` and calls `onUpdate(delta)` (and `onRender(g)` if Canvas). Start/stop the loop from `GameController`."

## Criterios de aceptación
- [ ] El loop corre sin bloquear la UI
- [ ] Se observa el `deltaTime` en logs o variable de depuración

## Notas de prueba
- [ ] Loggear cada 1s un contador de frames para verificar que corre


