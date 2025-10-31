# Issue 21 – Hilo de carga (assets en segundo plano)

## Objetivo
Cargar recursos (sprites/sonidos) en **segundo plano** mientras `loading.fxml` muestra barra de progreso.

## Checklist
- [ ] Añadir `ExecutorService` o `CompletableFuture` en `AssetsManager`
- [ ] Cargar imágenes/sonidos de forma asíncrona
- [ ] Reportar progreso mediante callback/Property y reflejarlo en `LoadingController`
- [ ] Al terminar la carga, navegar automáticamente a `main_menu.fxml`

## Prompt Copilot
"Load assets asynchronously using an `ExecutorService` or `CompletableFuture`, report progress to `LoadingController` (bindings/properties), and automatically navigate to the main menu when done."

## Criterios de aceptación
- [ ] La UI no se congela durante la carga
- [ ] La barra de progreso avanza de 0 a 100%
- [ ] La navegación post-carga funciona de forma fiable

## Notas de prueba
- [ ] Añadir `Thread.sleep` pequeños para simular carga y observar el progreso
