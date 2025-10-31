# Issue 28 – Pruebas unitarias (JUnit 5)

## Objetivo
Añadir pruebas unitarias para **colisiones**, **efectos de ítems** y **inserción en base de datos**.

## Checklist
- [ ] Añadir dependencias JUnit 5 (junit-jupiter) al `pom.xml` (scope `test`)
- [ ] Habilitar `maven-surefire-plugin` para JUnit 5
- [ ] Tests de `CollisionService`: casos positivos/negativos (AABB)
- [ ] Tests de `ItemService.applyItemEffect`: modifica salud/daño/velocidad correctamente
- [ ] Tests de BD: inserción y lectura con BD temporal (usar `@TempDir` o DB en `target/test-db`)
- [ ] Comando de prueba: `mvn -q -Dtest=* test`

## Prompt Copilot
"Add JUnit5 (junit-jupiter) and surefire config. Create tests for CollisionService (AABB), ItemService.applyItemEffect (player stats changes), and SQLite insertion/selection using a temporary DB path via JUnit @TempDir."

## Criterios de aceptación
- [ ] `mvn test` pasa con éxito
- [ ] Cobertura mínima razonable en las clases clave
- [ ] Los tests no crean archivos sueltos fuera de `target/`
