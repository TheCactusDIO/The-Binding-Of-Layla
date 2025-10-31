# Issue 8 – Enemigos básicos (Enemy)

## Objetivo
Crear `Enemy` con movimiento hacia el jugador y vida.

## Checklist
- [ ] Crear `src/main/java/com/layla/model/Enemy.java` (posición, speed, health)
- [ ] Sistema para mantener una lista de enemigos en `GameController`
- [ ] Spawnear varios enemigos (posición aleatoria válida)
- [ ] Movimiento básico hacia Player (vector dirección normalizado)

## Prompt Copilot
"Create an `Enemy` entity that chases the player. Manage a list of enemies in `GameController`, spawn them at random valid positions, and move them toward the player each update."

## Criterios de aceptación
- [ ] Enemigos aparecen y se desplazan hacia el jugador
- [ ] Sin bloqueos de UI ni tirones

## Notas de prueba
- [ ] Ajustar velocidad y evitar ‘teletransportes’
