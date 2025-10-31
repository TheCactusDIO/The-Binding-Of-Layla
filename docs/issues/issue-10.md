# Issue 10 – Colisiones (CollisionService)

## Objetivo
Detectar colisiones AABB (axis-aligned bounding box) y resolverlas.

## Checklist
- [ ] Crear `src/main/java/com/layla/services/CollisionService.java` (AABB)
- [ ] Colisión Projectile–Enemy → aplicar daño y eliminar si corresponde
- [ ] Colisión Enemy–Player → aplicar daño al jugador y activar invulnerabilidad breve (i-frames)
- [ ] Evitar *tunneling* básico: disminuir step o aumentar bounding box si delta grande

## Prompt Copilot
"Create a `CollisionService` with AABB checks for Projectile–Enemy and Enemy–Player. Apply damage, handle enemy death, and add brief i-frames to the player."

## Criterios de aceptación
- [ ] Colisiones consistentes (sin falsos negativos notables)
- [ ] Jugador no recibe daño continuo sin i-frames

## Notas de prueba
- [ ] Testear bordes de sprites y diferentes tamaños
