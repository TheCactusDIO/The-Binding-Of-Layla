# Issue 12 – Guardar puntuación (HighScoreService)

## Objetivo
Guardar **nombre del jugador** y **puntuación final** en SQLite y leer el **Top 5** para la pantalla de ranking.

## Checklist
- [ ] Asegurar dependencia SQLite JDBC en `pom.xml`
- [ ] Crear/actualizar `src/main/java/com/layla/services/HighScoreService.java`
- [ ] Tabla `scores(id INTEGER PK AUTOINCREMENT, player_name TEXT, score INTEGER, created_at TEXT)`
- [ ] Método `saveScore(String playerName, int score)`
- [ ] Método `List<Score> getTopScores(int limit)` (orden desc)
- [ ] Clase modelo `Score` (id, name, score, createdAt)
- [ ] Ruta BD: `./data/laila.db` (crear carpeta si no existe)

## Prompt Copilot
"Implement `HighScoreService` that persists player name and score to SQLite (`./data/laila.db`) and returns Top-5 scores. Create table if not exists: scores(id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT, score INTEGER, created_at TEXT). Provide a `Score` model."

## Criterios de aceptación
- [ ] Se inserta una puntuación sin errores
- [ ] `getTopScores(5)` devuelve 5 o menos registros ordenados correctamente
- [ ] La BD se guarda en `./data/laila.db`

## Notas de prueba
- [ ] Añadir un método temporal (o test) que llame a `saveScore("Test", 1234)` y loguee `getTopScores(5)`
