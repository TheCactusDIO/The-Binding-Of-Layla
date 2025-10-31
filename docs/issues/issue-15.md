# Issue 15 – Tienda (shop.fxml + ShopController)

## Objetivo
Crear una **tienda** entre pisos con 3 ítems aleatorios, compra con monedas y aplicación de efectos.

## Checklist
- [ ] Crear `src/main/resources/ui/shop.fxml` (lista/grid con 3 ítems + botón comprar)
- [ ] Crear `src/main/java/com/layla/ui/ShopController.java`
- [ ] Mostrar 3 ítems aleatorios de `ItemService` (respetando desbloqueados)
- [ ] Verificar dinero del jugador (`coins`) antes de comprar
- [ ] Al comprar: deducir monedas, aplicar efecto (`ItemService.applyItemEffect`)
- [ ] Cerrar tienda y volver a la escena de juego

## Prompt Copilot
"Create `shop.fxml` and `ShopController` presenting 3 random items from `ItemService`. Implement purchase flow with player coins, apply item effect on buy, and return to the game scene."

## Criterios de aceptación
- [ ] La tienda muestra 3 ítems válidos
- [ ] Compra deduce monedas y aplica efecto sin errores
- [ ] Navegación de vuelta al juego

## Notas de prueba
- [ ] Probar compras con y sin saldo suficiente
