# LiveCopilot MVP

Herramienta para vendedores que realizan transmisiones en vivo en TikTok (Android).

## Funcionalidades
- Overlay flotante tipo burbuja (estilo Messenger) que no interrumpe la transmisión.
- Al tocar la burbuja, se expande mostrando 6 shortcuts configurables.
- Los shortcuts copian comentarios o links de pago al portapapeles.
- La burbuja vuelve a su forma original tras la acción.
- La app corre en segundo plano durante el live.

## Requisitos
- Android Studio
- SDK mínimo: 26 (Android 8.0)

## Permisos necesarios
- `SYSTEM_ALERT_WINDOW` (para overlays)
- `FOREGROUND_SERVICE`

## Instrucciones
1. Clona este repositorio y ábrelo en Android Studio.
2. Compila y ejecuta la app en tu dispositivo Android.
3. Otorga el permiso de "mostrar sobre otras apps" cuando se solicite.
4. Inicia el servicio desde la app: aparecerá la burbuja flotante.
5. Personaliza los shortcuts en el código (`OverlayService.kt`).

---

Este MVP es una base funcional y puede ampliarse según feedback de usuarios.
