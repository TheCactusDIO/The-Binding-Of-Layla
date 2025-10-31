# Issue 11 – SQLite base (DBHelper + HighScoreService)

## Objetivo
Conectar SQLite y preparar tablas para **scores** (y reutilizable para futuros datos).

## Checklist
- [ ] Añadir dependencia **SQLite JDBC** al `pom.xml`
- [ ] Crear `src/main/java/com/layla/db/DBHelper.java` (conexión, `CREATE TABLE IF NOT EXISTS`)
- [ ] Crear `src/main/java/com/layla/services/HighScoreService.java` (insertar score, leer top N)
- [ ] Gestionar ruta de la BD (por ejemplo: `./data/laila.db`) y crear carpeta si no existe

## Prompt Copilot
"Add SQLite JDBC and implement `DBHelper` for connection + table creation. Implement `HighScoreService` to insert scores and read top 5. Ensure the DB file is stored under `./data/laila.db`."

## Criterios de aceptación
- [ ] La app crea la BD y la tabla (ej.: `scores(id, player_name, score, created_at)`)
- [ ] Se puede insertar un registro y leer el top 5 sin errores

## Notas de prueba
- [ ] Probar inserción de score falso desde un método temporal y leerlos en consola
