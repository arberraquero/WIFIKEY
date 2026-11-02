package com.freddy.logdownloaderwifi;

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

@DesignerComponent(
    version = 1,
    description = "Solicita conexión a una red WiFi mediante la API oficial de Android 10+ (el usuario debe aprobar el diálogo del sistema).",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "")
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.ACCESS_NETWORK_STATE, android.permission.CHANGE_WIFI_STATE, android.permission.ACCESS_WIFI_STATE, android.permission.INTERNET, android.permission.ACCESS_FINE_LOCATION")
public class WifiConnector extends AndroidNonvisibleComponent {
  private final Form form;
  private final ConnectivityManager connectivityManager;
  private ConnectivityManager.NetworkCallback callback;
  private boolean requestPending = false;

  public WifiConnector(ComponentContainer container) {
    super(container.$form());
    form = container.$form();
    connectivityManager = (ConnectivityManager) form.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  @SimpleFunction(description = "Solicita conectar a SSID con contraseña. Android muestra un diálogo del sistema para que el usuario autorice. Compatible con Android 10 (API 29) o posterior.")
  public void Connect(String ssid, String password) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      Error("Esta extensión requiere Android 10 o posterior (API 29+).");
      return;
    }
    if (ssid == null || ssid.trim().isEmpty()) {
      Error("El nombre de la red (SSID) está vacío.");
      return;
    }
    Disconnect();
    try {
      WifiNetworkSpecifier.Builder wifiBuilder = new WifiNetworkSpecifier.Builder().setSsid(ssid.trim());
      if (password != null && !password.isEmpty()) {
        wifiBuilder.setWpa2Passphrase(password);
      }
      WifiNetworkSpecifier specifier = wifiBuilder.build();
      NetworkRequest request = new NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          .setNetworkSpecifier(specifier)
          .build();
      callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
          try {
            connectivityManager.bindProcessToNetwork(network);
            requestPending = false;
            form.runOnUiThread(new Runnable() {
              @Override public void run() {
                Connected(ssid);
              }
            });
          } catch (Exception e) {
            final String errorMessage = "Conectado, pero no se pudo seleccionar la red: " + e.getMessage();
            form.runOnUiThread(new Runnable() {
              @Override public void run() {
                Error(errorMessage);
              }
            });
          }
        }
        @Override public void onUnavailable() {
          requestPending = false;
          form.runOnUiThread(new Runnable() {
            @Override public void run() {
              ConnectionFailed("El usuario canceló la solicitud o la red no está disponible.");
            }
          });
        }
        @Override public void onLost(Network network) {
          form.runOnUiThread(new Runnable() {
            @Override public void run() {
              Disconnected();
            }
          });
        }
      };
      requestPending = true;
      connectivityManager.requestNetwork(request, callback);
    } catch (Exception e) {
      requestPending = false;
      Error("No se pudo solicitar la conexión WiFi: " + e.getMessage());
    }
  }

  @SimpleFunction(description = "Libera la conexión WiFi solicitada por esta extensión y devuelve la selección de red al sistema.")
  public void Disconnect() {
    try {
      if (connectivityManager != null) connectivityManager.bindProcessToNetwork(null);
      if (callback != null && connectivityManager != null) {
        connectivityManager.unregisterNetworkCallback(callback);
      }
    } catch (Exception ignored) { }
    callback = null;
    requestPending = false;
  }

  @SimpleFunction(description = "Indica si hay una solicitud de conexión pendiente.")
  public boolean IsRequestPending() { return requestPending; }

  @SimpleEvent public void Connected(String ssid) {
    EventDispatcher.dispatchEvent(this, "Connected", ssid);
  }
  @SimpleEvent public void ConnectionFailed(String reason) {
    EventDispatcher.dispatchEvent(this, "ConnectionFailed", reason);
  }
  @SimpleEvent public void Disconnected() {
    EventDispatcher.dispatchEvent(this, "Disconnected");
  }
  @SimpleEvent public void Error(String message) {
    EventDispatcher.dispatchEvent(this, "Error", message);
  }
}
