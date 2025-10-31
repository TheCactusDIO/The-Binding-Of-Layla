# Issue 30 – Empaquetado y README (entrega)

## Objetivo
Configurar **empaquetado ejecutable** y crear un **README.md** claro (features, controles, tecnologías, cómo ejecutar).

## Checklist
- [ ] Añadir en `pom.xml` objetivo `javafx-maven-plugin:jlink` para generar **runtime image** (Windows)
- [ ] Generar imagen: `mvn -q clean javafx:jlink`
- [ ] Verificar ejecución desde `target/image/bin/` (o configuración equivalente)
- [ ] Crear `README.md` con:
  - Resumen del juego y capturas (si posible)
  - Requisitos (JDK 21)
  - Cómo correr (dev: `mvn javafx:run` / entrega: runtime image)
  - Controles del juego
  - Estructura del proyecto
  - Créditos/Assets/licencias
- [ ] (Opcional) Añadir `maven-assembly-plugin` para jar con dependencias (si te interesa jar único; puede requerir flags para JavaFX)

## Prompt Copilot
"Configure packaging with `javafx-maven-plugin` jlink to produce a Windows runtime image. Add a thorough README.md describing features, controls, tech stack, how to run (dev and packaged), and credits."

## Criterios de aceptación
- [ ] Se genera una **runtime image** ejecutable en `target/.../bin/`
- [ ] README completo y entendible
- [ ] `mvn javafx:run` sigue funcionando en desarrollo
