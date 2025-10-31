# Issue 19 – Enemigos especiales (Miniboss/Boss alternativos)

## Objetivo
Crear `Boss.java` con patrón de ataque único y vida alta. Añadir mínimo un **miniboss** por piso (o por lo menos en los dos primeros pisos).

## Checklist
- [ ] Crear `src/main/java/com/layla/model/Boss.java` (health alto, daño alto, fases simples)
- [ ] Añadir 1 patrón de ataque único (por ejemplo, disparo radial o ondas)
- [ ] Integrar en `GameController`/`FloorManager` para invocarlo en sala de jefe
- [ ] Indicador visual sencillo de vida del boss (barra temporal en HUD)
- [ ] Balance básico para que sea desafiante sin ser imposible

## Prompt Copilot
"Create `Boss.java` with higher health and a unique attack pattern (e.g., radial shots or wave attacks). Integrate it as a boss room encounter and show a simple boss health bar on HUD."

## Criterios de aceptación
- [ ] El boss aparece en una sala designada
- [ ] Ejecuta su patrón de ataque de forma repetible
- [ ] La barra de vida refleja correctamente el daño recibido

## Notas de prueba
- [ ] Forzar spawn del boss con un atajo de teclado o flag temporal
