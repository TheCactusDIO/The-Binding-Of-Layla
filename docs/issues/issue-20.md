# Issue 20 – Jefe final (FinalBoss multi-fase)

## Objetivo
Implementar `FinalBoss.java` con **múltiples fases** y condición de **Game Over** (victoria/derrota).

## Checklist
- [ ] Crear `src/main/java/com/layla/model/FinalBoss.java` (2–3 fases simples)
- [ ] Patrones de ataque cambiantes por fase (ritmo/velocidad/balas)
- [ ] Trigger de **victoria** al derrotarlo → navegar a pantalla de Game Over (modo victoria) y guardar puntuación
- [ ] Trigger de **derrota** si vida del jugador llega a 0 → Game Over (modo derrota)
- [ ] Ajustar HUD para indicar fase (texto simple o color)

## Prompt Copilot
"Add `FinalBoss.java` with two or three simple phases. Change attack patterns on phase transitions. Trigger Game Over on win or player death, and route to the Game Over screen accordingly."

## Criterios de aceptación
- [ ] El jefe final cambia de fase bajo ciertos umbrales de vida
- [ ] La pantalla de Game Over se muestra con el modo correcto (victoria/derrota)

## Notas de prueba
- [ ] Reducir salud del jefe para probar fases rápidamente
