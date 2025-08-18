# LiveCopilot MVP

Herramienta para vendedores que realizan transmisiones en vivo en TikTok (Android).

## Funcionalidades
- Overlay flotante tipo burbuja (estilo Messenger) que no interrumpe la transmisión.
- Al tocar la burbuja, se expande mostrando un menú en forma de abanico con accesos rápidos.
- Gestión completa de productos con catálogo, imágenes y carrito de compras.
- Opciones para compartir productos con texto e imágenes durante el live.
- Integración con portapapeles para copiar información de productos rápidamente.
- La app corre en segundo plano durante el live.

## Componentes principales

### Interfaz flotante
- Burbuja persistente que flota sobre otras aplicaciones
- Menú expandible en forma de abanico con accesos directos
- Se puede colapsar a burbuja cuando no se necesita

### Gestión de productos
- Catálogo de productos para compartir durante transmisiones
- Detalles de productos incluyen nombre, precio, descripción e imágenes
- Acceso rápido a información de productos

### Galería de imágenes
- Acceso a galería de imágenes para compartir fotos de productos
- Funcionalidad de gestión de imágenes para listados de productos

### Carrito de compras
- Posibilidad de añadir productos a un carrito
- Generación de texto formateado con listas de productos
- Compartir contenido del carrito con espectadores

### Integración con portapapeles
- Copia rápida de información de productos al portapapeles
- Opciones para copiar texto, imágenes o ambos
- Integración con servicio de accesibilidad para pegado automático

## Requisitos
- Android Studio
- SDK mínimo: 26 (Android 8.0)

## Permisos necesarios
- `SYSTEM_ALERT_WINDOW` (para overlays)
- `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_SPECIAL_USE`
- `READ_MEDIA_IMAGES` y `READ_EXTERNAL_STORAGE` (para acceso a galería)
- `CAMERA` (para captura de imágenes)

## Estructura del código
- **MainActivity**: Punto de entrada de la app que gestiona permisos y navegación
- **OverlayService**: Servicio principal que gestiona la interfaz de burbuja flotante
- **Actividades adicionales**: CatalogActivity, GalleryActivity, SettingsActivity, etc.
- **Adaptadores**: CartAdapter, ProductAdapter, GalleryAdapter para RecyclerViews
- **Gestores de datos**: ProductManager, CartManager, ImageManager

## Instrucciones
1. Clona este repositorio y ábrelo en Android Studio.
2. Compila y ejecuta la app en tu dispositivo Android.
3. Otorga el permiso de "mostrar sobre otras apps" cuando se solicite.
4. Inicia el servicio desde la app: aparecerá la burbuja flotante.
5. Personaliza los shortcuts y productos en el código (`OverlayService.kt`).

---

Este MVP es una base funcional y puede ampliarse según feedback de usuarios.
