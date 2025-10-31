# Issue 34 – (OPCIONAL) Soporte para mando (gamepad)

## Objetivo
Permitir movimiento/disparo con mando (gamepad) además del teclado.

## Checklist
- [ ] Investigar librería viable para Windows (p. ej., JInput/Jamepad/hid4java)
- [ ] Crear `GamepadInputService` mapeando sticks/botones a acciones
- [ ] Toggle en Settings para activar/desactivar mando
- [ ] Prioridad a último dispositivo usado (teclado o mando)

## Prompt Copilot
"Add a `GamepadInputService` using a Windows-compatible Java library, mapping axes/buttons to movement and shooting, with a toggle in Settings."

## Criterios de aceptación
- [ ] El jugador se mueve/dispara con el mando
- [ ] Sin interferencias con teclado cuando no se usa
