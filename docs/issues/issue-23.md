# Issue 23 – Audio y música (AudioService)

## Objetivo
Añadir **música de fondo** y **efectos** (disparos, recogidas), con control de volumen desde Settings.

## Checklist
- [ ] Crear `src/main/java/com/layla/services/AudioService.java`
- [ ] Reproducir música de fondo en loop (tema del menú y del juego)
- [ ] Cargar SFX: disparo, coin, daño, boss
- [ ] Métodos: `playMusic(name)`, `stopMusic()`, `playSfx(name)`, `setVolumeMusic(double)`, `setVolumeSfx(double)`
- [ ] Integración con Settings (Slider volumen → persistir preferencia)

## Prompt Copilot
"Add `AudioService` to play looping background music and sound effects (shooting, pickups). Provide volume controls and integrate with the Settings scene."

## Criterios de aceptación
- [ ] Música y efectos reproducen sin cortes
- [ ] Control de volumen funciona y persiste

## Notas de prueba
- [ ] Mutear música y dejar SFX para verificar controles independientes
