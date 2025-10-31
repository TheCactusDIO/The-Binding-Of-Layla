# Issue 14 – Sistema de objetos (Item + ItemService)

## Objetivo
Crear `Item.java` y un sistema de **recogida/aplicación de efectos**.

## Checklist
- [ ] Crear modelo `src/main/java/com/layla/model/Item.java` (id, name, description, rarity, effectType, magnitude)
- [ ] Crear `src/main/java/com/layla/services/ItemService.java`
- [ ] Método `Item rollRandomItem(Set<String> unlocked)` (pools simples)
- [ ] Método `void applyItemEffect(Player p, Item i)` (ej. +vida, +daño, +cadencia)
- [ ] Soporte mínimo de 10 ítems, 1 especial (ej. rayo continuo tipo “brimstone-lite” para más tarde)
- [ ] Integración básica con HUD (indicador de ítem obtenido, opcional log)

## Prompt Copilot
"Create `Item` model (id, name, description, rarity, effectType, magnitude) and `ItemService` with `rollRandomItem` (respecting unlocked set) and `applyItemEffect(Player, Item)`. Provide at least 10 simple items and leave a placeholder for a special beam-like item."

## Criterios de aceptación
- [ ] Se obtiene un ítem aleatorio de un pool simple
- [ ] `applyItemEffect` modifica atributos del Player (comprobable en runtime)
- [ ] No hay NPE ni fugas al aplicar/stackear efectos simples

## Notas de prueba
- [ ] Forzar un ítem concreto y verificar su efecto (daño/velocidad/vida)
