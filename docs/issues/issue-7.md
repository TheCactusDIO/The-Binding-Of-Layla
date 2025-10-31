# Issue 7 – Jugador (Player + InputService)

## Objetivo
Añadir `Player` con movimiento WASD y servicio de entradas `InputService`.

## Checklist
- [ ] Crear `src/main/java/com/layla/model/Player.java` (x,y, speed, health)
- [ ] Crear `src/main/java/com/layla/services/InputService.java` para manejar teclas (pressed/released)
- [ ] `GameController` consulta InputService y actualiza Player en `update(delta)`
- [ ] Dibujar Player (si Canvas) o actualizar nodo (si Pane con Node/ImageView)

## Prompt Copilot
"Add a `Player` model (position, speed, health) and an `InputService` that tracks WASD keys. In `GameController.update(delta)`, move the player accordingly. Provide a simple render method."

## Criterios de aceptación
- [ ] El Player se mueve con WASD con suavidad
- [ ] No hay excepciones en consola

## Notas de prueba
- [ ] Probar diagonales y límites de pantalla
