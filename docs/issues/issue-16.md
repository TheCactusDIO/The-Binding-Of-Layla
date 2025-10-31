# Issue 16 – Monedas (Coin entity & drops)

## Objetivo
Añadir **monedas** que suelten los enemigos al morir y que el jugador pueda recoger.

## Checklist
- [ ] Crear `src/main/java/com/layla/model/Coin.java` (posición, valor)
- [ ] Al morir un enemigo, probabilidad pequeña de soltar `Coin`
- [ ] `GameController` mantiene lista de `Coin`, update/render
- [ ] Colisión Player–Coin: aumentar `player.coins` y eliminar `Coin`
- [ ] Sonido/feedback visual simple (opcional)

## Prompt Copilot
"Add a `Coin` entity dropped by enemies on death with a small random chance. Manage a coin list, render/update them, and on Player–Coin collision increment player coins and remove the coin."

## Criterios de aceptación
- [ ] Se generan monedas al morir enemigos
- [ ] El jugador recoge monedas y aumenta el contador

## Notas de prueba
- [ ] Forzar drop al 100% temporalmente para probar recogida
