# Issue 26 – Pantalla de ranking (Top 10)

## Objetivo
Mostrar las **mejores puntuaciones** usando `TableView`.

## Checklist
- [ ] Crear `src/main/resources/ui/highscores.fxml` y `src/main/java/com/layla/ui/HighScoresController.java`
- [ ] TableView con columnas: **Nombre**, **Score**, **Fecha**
- [ ] Cargar datos desde `HighScoreService.getTopScores(10)`
- [ ] Navegación desde Menú → Ranking y volver

## Prompt Copilot
"Create `highscores.fxml` with a `TableView` showing top 10 scores (name, score, date) retrieved from `HighScoreService`. Provide navigation from the main menu."

## Criterios de aceptación
- [ ] Se muestran 0–10 registros ordenados correctamente
- [ ] Navegación a/desde Ranking funciona

## Notas de prueba
- [ ] Insertar datos dummy si la tabla está vacía (temporal)
