LogDownloader WiFi Connector - código fuente de extensión para MIT App Inventor 2

IMPORTANTE
- Este paquete contiene el código fuente Java y la receta de compilación; NO es todavía un archivo .aix instalable.
- Android 10 (API 29) o posterior muestra un diálogo del sistema. La aplicación no puede conectarse silenciosamente ni aceptar el diálogo por el usuario.
- La extensión usa WifiNetworkSpecifier y vincula el proceso a la red solicitada para permitir el acceso a dispositivos locales sin Internet, como 192.168.4.1.
- La solicitud de red puede tener duración limitada por Android y el comportamiento exacto depende del fabricante y versión.

Bloques previstos en App Inventor:
  call WifiConnector1.Connect("live_6s_Movi", "Opo2026Imas*")
  when WifiConnector1.Connected(ssid) -> abrir http://192.168.4.1/download.html
  when WifiConnector1.ConnectionFailed(reason) -> mostrar aviso
  call WifiConnector1.Disconnect() al terminar

Compilación del .aix
El workflow de GitHub Actions incluido compila el .aix usando el proyecto de MIT App Inventor. Sube esta carpeta a un repositorio GitHub y ejecuta Actions; descarga el artefacto generado.

Permisos declarados por la extensión: ACCESS_NETWORK_STATE, CHANGE_WIFI_STATE, ACCESS_WIFI_STATE, INTERNET y ACCESS_FINE_LOCATION.
