# Issue 29 – Refactor final y documentación (Javadoc)

## Objetivo
Refactorizar paquetes para claridad, documentar controladores/servicios con **Javadoc** y limpiar imports.

## Checklist
- [ ] Revisar estructura de paquetes (`model`, `services`, `ui`, `core`, `db`) y mover clases si procede
- [ ] Añadir Javadoc a controladores (`*Controller`), servicios y clases del core
- [ ] Eliminar imports no usados, warnings del compilador
- [ ] Añadir `package-info.java` en paquetes principales con breve descripción
- [ ] Comprobar que el proyecto compila y ejecuta igual que antes

## Prompt Copilot
"Refactor packages for clarity, ensure controllers/services/core have Javadoc (class and key methods), add package-info.java summaries, and remove unused imports. Preserve behavior."

## Criterios de aceptación
- [ ] Compilación sin warnings graves
- [ ] Javadoc al menos en clases públicas principales
- [ ] Estructura de paquetes coherente y limpia
