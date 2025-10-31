# Issue 9 – Disparos (Projectile + ShootingService)

## Objetivo
Permitir disparar proyectiles (tecla SPACE) y gestionarlos.

## Checklist
- [ ] Crear `src/main/java/com/layla/model/Projectile.java` (x,y, vx, vy, daño, TTL)
- [ ] Crear `src/main/java/com/layla/services/ShootingService.java` (cadencia, creación de proyectiles)
- [ ] Añadir lista de proyectiles en `GameController` (update y render)
- [ ] Los proyectiles se destruyen al salir de pantalla o al impactar

## Prompt Copilot
"Implement `Projectile` and a `ShootingService` so the player can shoot with SPACE. Handle spawn rate, update movement, and cleanup when off-screen or on hit."

## Criterios de aceptación
- [ ] Los disparos salen desde la posición del jugador
- [ ] Se eliminan correctamente al salir de pantalla

## Notas de prueba
- [ ] Probar cadencia (rate limit) para evitar spam
