package com.freddy.logdownloaderwifi;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.PermissionResultHandler;

@DesignerComponent(
    version = 2,
    description = "Solicita conexión a una red WiFi local mediante Android 10 o posterior. Android muestra su propio diálogo de autorización.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "")
@SimpleObject(external = true)
@UsesPermissions(permissionNames =
    "android.permission.ACCESS_NETWORK_STATE, " +
    "android.permission.CHANGE_NETWORK_STATE, " +
    "android.permission.CHANGE_WIFI_STATE, " +
    "android.permission.ACCESS_WIFI_STATE, " +
    "android.permission.INTERNET, " +
    "android.permission.NEARBY_WIFI_DEVICES, " +
    "android.permission.ACCESS_FINE_LOCATION")
public class WifiConnector extends AndroidNonvisibleComponent {

  private final Form form;
  private final ConnectivityManager connectivityManager;
  private ConnectivityManager.NetworkCallback callback;
  private volatile boolean requestPending = false;

  public WifiConnector(ComponentContainer container) {
    super(container.$form());
    form = container.$form();
    connectivityManager = (ConnectivityManager) form.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  @SimpleFunction(description = "Solicita conectar a una red WiFi. Android pedirá autorización al usuario cuando corresponda.")
  public void Connect(final String ssid, final String password) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      Error("Esta extensión requiere Android 10 o posterior (API 29+).");
      return;
    }
    if (ssid == null || ssid.trim().length() == 0) {
      Error("El nombre de la red (SSID) está vacío.");
      return;
    }
    if (connectivityManager == null) {
      Error("Android no proporciona el servicio de conectividad.");
      return;
    }

    final String requiredPermission;
    if (Build.VERSION.SDK_INT >= 33) {
      requiredPermission = "android.permission.NEARBY_WIFI_DEVICES";
    } else {
      requiredPermission = Manifest.permission.ACCESS_FINE_LOCATION;
    }

    try {
      form.askPermission(requiredPermission, new PermissionResultHandler() {
        @Override
        public void HandlePermissionResponse(String permission, boolean granted) {
          if (!granted) {
            Error("Permiso denegado: " + permission + ". Autoriza el permiso y vuelve a pulsar Conectar.");
            return;
          }
          requestWifiNetwork(ssid.trim(), password);
        }
      });
    } catch (Exception e) {
      Error("No se pudo solicitar el permiso de Android: " + safeMessage(e));
    }
  }

  private void requestWifiNetwork(final String ssid, final String password) {
    Disconnect();
    try {
      WifiNetworkSpecifier.Builder wifiBuilder = new WifiNetworkSpecifier.Builder().setSsid(ssid);
      if (password != null && password.length() > 0) {
        wifiBuilder.setWpa2Passphrase(password);
      }
      WifiNetworkSpecifier specifier = wifiBuilder.build();

      NetworkRequest request = new NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          .setNetworkSpecifier(specifier)
          .build();

      callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(final Network network) {
          try {
            connectivityManager.bindProcessToNetwork(network);
            requestPending = false;
            form.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                Connected(ssid);
              }
            });
          } catch (final Exception e) {
            form.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                Error("Se conectó a la WiFi, pero no se pudo usar para la aplicación: " + safeMessage(e));
              }
            });
          }
        }

        @Override
        public void onUnavailable() {
          requestPending = false;
          form.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              ConnectionFailed("Android no pudo conectar con " + ssid + ". Comprueba la contraseña, que la red esté activa y acepta el diálogo del sistema.");
            }
          });
        }

        @Override
        public void onLost(final Network network) {
          if (Build.VERSION.SDK_INT >= 23) {
            Network current = connectivityManager.getBoundNetworkForProcess();
            if (current != null && current.equals(network)) {
              connectivityManager.bindProcessToNetwork(null);
            }
          }
          form.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Disconnected();
            }
          });
        }
      };

      requestPending = true;
      connectivityManager.requestNetwork(request, callback);
    } catch (final Exception e) {
      requestPending = false;
      form.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Error("No se pudo solicitar la conexión WiFi: " + safeMessage(e));
        }
      });
    }
  }

  @SimpleFunction(description = "Libera la conexión WiFi solicitada por esta extensión.")
  public void Disconnect() {
    try {
      if (connectivityManager != null) {
        connectivityManager.bindProcessToNetwork(null);
        if (callback != null) {
          connectivityManager.unregisterNetworkCallback(callback);
        }
      }
    } catch (Exception ignored) {
    }
    callback = null;
    requestPending = false;
  }

  @SimpleFunction(description = "Indica si hay una solicitud de conexión pendiente.")
  public boolean IsRequestPending() {
    return requestPending;
  }

  private String safeMessage(Exception e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : message;
  }

  @SimpleEvent
  public void Connected(String ssid) {
    EventDispatcher.dispatchEvent(this, "Connected", ssid);
  }

  @SimpleEvent
  public void ConnectionFailed(String reason) {
    EventDispatcher.dispatchEvent(this, "ConnectionFailed", reason);
  }

  @SimpleEvent
  public void Disconnected() {
    EventDispatcher.dispatchEvent(this, "Disconnected");
  }

  @SimpleEvent
  public void Error(String message) {
    EventDispatcher.dispatchEvent(this, "Error", message);
  }
}
