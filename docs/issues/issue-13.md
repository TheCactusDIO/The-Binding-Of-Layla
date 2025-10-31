# Issue 13 – Lectura de ítems desbloqueados (UnlockService)

## Objetivo
Permitir que el juego recuerde qué **ítems están desbloqueados** y usarlos en la partida.

## Checklist
- [ ] Crear tabla `unlocks(item_id TEXT PRIMARY KEY, unlocked INTEGER NOT NULL)` (1=desbloqueado)
- [ ] Crear `src/main/java/com/layla/services/UnlockService.java`
- [ ] Método `boolean isUnlocked(String itemId)`
- [ ] Método `void unlock(String itemId)`
- [ ] Método `Set<String> getUnlockedItems()`
- [ ] Integrar con `ItemService` (en el futuro) para filtrar ítems bloqueados
- [ ] BD en `./data/laila.db`

## Prompt Copilot
"Create `UnlockService` backed by SQLite with table `unlocks(item_id TEXT PRIMARY KEY, unlocked INTEGER NOT NULL)`. Implement `isUnlocked`, `unlock`, and `getUnlockedItems`. Integrate with future `ItemService` to only include unlocked items."

## Criterios de aceptación
- [ ] Se crea la tabla `unlocks` si no existe
- [ ] `unlock("item_basic_damage")` persiste y `isUnlocked` lo devuelve correctamente
- [ ] `getUnlockedItems()` retorna un conjunto coherente

## Notas de prueba
- [ ] Desbloquear un ítem ficticio y verificar su lectura
