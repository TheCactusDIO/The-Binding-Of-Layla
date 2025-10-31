# Issue 2 – Creación del proyecto base (The Binding of Layla)

## 🎯 Objetivo
Configurar el proyecto **Maven JavaFX** listo para ejecutar con el nombre *The Binding of Layla*, asegurando que:
- Java 21 y Maven 3.9.11 funcionan correctamente.
- La estructura de carpetas está creada.
- `App.java` lanza una ventana JavaFX con el título correcto.
- Copilot puede autocompletar código dentro del proyecto.
- Scene Builder abre los `.fxml` sin problemas.

---

## ✅ Checklist de prerrequisitos
- [ ] **Issue 1 completado correctamente**  
- [ ] `java -version` muestra Java 21  
- [ ] `mvn -v` muestra Maven 3.9.11  
- [ ] VS Code detecta automáticamente el SDK de Java  
- [ ] Carpeta del repositorio lista (`The-Binding-Of-Layla`)  
- [ ] Rama de trabajo: `dev` (no main)

---

## ⚙️ Checklist de configuración y creación
- [ ] Ejecutado el comando Maven para crear el proyecto:
  ```bash
  mvn -q archetype:generate -DgroupId=com.layla -DartifactId=The-Binding-Of-Layla -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
