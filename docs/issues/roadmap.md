# 🧭 THE BINDING OF LAYLA – FULL PROJECT ROADMAP

Este documento contiene todos los **issues (1–35)** organizados por **EPIC**.  
Cada issue incluye un enlace directo al repositorio y un checkbox vacío para seguimiento manual.

---

## 🧩 EPIC 1 – Preparación e instalación

- [ ] [#1 – Instalación y configuración inicial (Entorno de desarrollo)](../../issues/1)
- [ ] [#2 – Creación del proyecto base (Maven + JavaFX)](../../issues/2)
- [ ] [#3 – Pantalla de carga (Loading Screen)](../../issues/3)

---

## 🕹️ EPIC 2 – Interfaz principal y flujo de escenas

- [ ] [#4 – Menú principal (Play / Settings / Exit)](../../issues/4)
- [ ] [#5 – Escena de juego (game.fxml + HUD)](../../issues/5)

---

## ⚙️ EPIC 3 – Núcleo del juego

- [ ] [#6 – Bucle de juego (AnimationTimer)](../../issues/6)
- [ ] [#7 – Jugador (Player + InputService)](../../issues/7)
- [ ] [#8 – Enemigos básicos (Enemy)](../../issues/8)
- [ ] [#9 – Disparos (Projectile + ShootingService)](../../issues/9)
- [ ] [#10 – Colisiones (CollisionService)](../../issues/10)

---

## 💾 EPIC 4 – Persistencia y base de datos

- [ ] [#11 – SQLite base (DBHelper + HighScoreService)](../../issues/11)
- [ ] [#12 – Guardar puntuación (HighScoreService)](../../issues/12)
- [ ] [#13 – Lectura de ítems desbloqueados (UnlockService)](../../issues/13)

---

## 🧠 EPIC 5 – Ítems y poderes

- [ ] [#14 – Sistema de objetos (Item + ItemService)](../../issues/14)
- [ ] [#15 – Tienda (shop.fxml + ShopController)](../../issues/15)
- [ ] [#16 – Monedas (Coin entity & drops)](../../issues/16)

---

## 🧱 EPIC 6 – Niveles y progreso

- [ ] [#17 – Sistema de pisos (FloorManager + layouts predefinidos)](../../issues/17)
- [ ] [#18 – Generación procedural simple (selección aleatoria de layouts)](../../issues/18)

---

## 💀 EPIC 7 – Jefes y multihilo

- [ ] [#19 – Enemigos especiales (Miniboss/Boss alternativos)](../../issues/19)
- [ ] [#20 – Jefe final (FinalBoss multi-fase)](../../issues/20)
- [ ] [#21 – Hilo de carga (assets en segundo plano)](../../issues/21)
- [ ] [#22 – Guardado asíncrono (puntuación/progreso)](../../issues/22)

---

## 🎨 EPIC 8 – UI, sonido y polish

- [ ] [#23 – Audio y música (AudioService)](../../issues/23)
- [ ] [#24 – HUD y barra de vida (bindings)](../../issues/24)
- [ ] [#25 – Pantalla de Game Over (guardar score)](../../issues/25)
- [ ] [#26 – Pantalla de ranking (Top 10)](../../issues/26)
- [ ] [#27 – Ajustes (Settings + persistencia local)](../../issues/27)

---

## 🧪 EPIC 9 – Pruebas, refactor y entrega

- [ ] [#28 – Pruebas unitarias (JUnit 5)](../../issues/28)
- [ ] [#29 – Refactor final y documentación (Javadoc)](../../issues/29)
- [ ] [#30 – Empaquetado y README (entrega final)](../../issues/30)

---

## 💎 BONUS EPIC – Opcionales / Features extra

> Estos issues están marcados con la etiqueta **`optional`** en GitHub.

- [ ] [#31 – (OPCIONAL) Power-ups visuales (partículas, screen-shake)](../../issues/31)
- [ ] [#32 – (OPCIONAL) Animaciones de sprites](../../issues/32)
- [ ] [#33 – (OPCIONAL) Pantalla de logros (Achievements)](../../issues/33)
- [ ] [#34 – (OPCIONAL) Soporte para mando (Gamepad)](../../issues/34)
- [ ] [#35 – (OPCIONAL) Easter Eggs “Laia mode”](../../issues/35)

---

## 📘 Recomendaciones de trabajo

1. Trabaja **siempre en ramas por issue** (`issue-XX-feature`).
2. Al terminar un issue, haz:
   ```bash
   git add .
   git commit -m "✅ Issue XX – completado"
   git push origin issue-XX-feature
   gh pr create --title "Issue XX – completado" --body "Close #XX"
