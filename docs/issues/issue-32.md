# Issue 32 – (OPCIONAL) Animaciones de sprites

## Objetivo
Añadir animación básica por **sprite sheet** (idle/walk/attack) para Player y Enemigos.

## Checklist
- [ ] Clase utilitaria `SpriteAnimator` (frames, fps, loop)
- [ ] Sprite sheets para Player (idle/walk) y 1–2 enemigos
- [ ] Integración en `render()` o Node/ImageView
- [ ] Ajuste de timing según `deltaTime`

## Prompt Copilot
"Implement `SpriteAnimator` for sheet-based animations (frames/fps/loop) and use it for player and enemies (idle/walk)."

## Criterios de aceptación
- [ ] Animación fluida a fps constante
- [ ] Sin flickering ni desbordes de índice
