# Issue 3 – Pantalla de carga (Loading Screen)

## Objetivo
Mostrar una **pantalla de carga** (`loading.fxml`) mientras se cargan recursos en **hilo en segundo plano**, con una **barra de progreso**.

## Checklist
- [ ] Crear `loading.fxml` con barra/progress indicator
- [ ] Crear `LoadingController.java` con `@FXML` a la barra
- [ ] Crear `AssetsManager` (o ampliar el existente) con métodos de carga
- [ ] Lanzar carga en un **background thread** (ExecutorService / CompletableFuture)
- [ ] Actualizar progreso en UI con `Platform.runLater` o bindings
- [ ] Al finalizar, cambiar a **Main Menu** (placeholder) o Game (temporal)

## Prompt Copilot
"Create `loading.fxml` and its controller `LoadingController.java` that displays a progress bar while assets load in a background thread. Update the progress on the JavaFX Application Thread and navigate to the next scene when done."

## Criterios de aceptación
- [ ] La app arranca mostrando `loading.fxml`
- [ ] La barra de progreso **avanza** durante la carga
- [ ] No se **congela** la UI (carga real en otro hilo)
- [ ] Al terminar, navega automáticamente a la siguiente escena
