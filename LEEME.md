# Hemeroteca (app Android)

App para elegir fuerza, superserie y WOD a partir del cuaderno del box.
Funciona sin internet (solo usa internet para cargar la tipografía).

## Cómo sacar el APK (una vez)
1. Crea una cuenta en github.com si no tienes.
2. Crea un repositorio nuevo, público, llamado `hemeroteca`.
3. Pulsa "uploading an existing file" y arrastra TODO el contenido de esta carpeta
   (incluida la carpeta oculta `.github`). Pulsa "Commit changes".
4. Ve a la pestaña **Actions**: se compila solo (unos 4 minutos).
5. Ve a **Releases** (columna derecha de la página del repo): ahí está `Hemeroteca.apk`.

El enlace `https://github.com/TU_USUARIO/hemeroteca/releases/latest` es el que pasas al grupo.

## Instalar en Android
Abrir el enlace en el móvil → descargar Hemeroteca.apk → abrirlo →
permitir "Instalar apps desconocidas" para Chrome o WhatsApp → Instalar.
Android avisará de que la app no viene de Play Store: es normal.

## Actualizar
Cambia `app/src/main/assets/index.html` (la app entera está ahí) y sube el cambio.
GitHub publica una versión nueva sola. Cada persona descarga e instala encima: no se borran sus RM.
