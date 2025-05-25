package com.maksz42.periscope;

import android.app.Application;
import android.os.Build;

import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.helper.Settings;
import com.maksz42.periscope.utils.Net;

import java.net.MalformedURLException;

public class Periscope extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    Settings settings = Settings.getInstance(this);
    Client.Protocol protocol = settings.getProtocol();
    String host = settings.getHost();
    int port = settings.getPort();
    try {
      Client.setup(protocol, host, port);
    } catch (MalformedURLException | NullPointerException ignored) { }
    String user = settings.getUser();
    String password = settings.getPassword();
    Client.setCredentials(user, password);

    if (settings.getProtocol() == Client.Protocol.HTTPS) {
      Net.configureSSLSocketFactory(settings.getDisableCertVerification());
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      BootReceiver.setEnabled(this, settings.getAutostart());
    } else {
      boolean enabled = BootReceiver.getEnabled(this);
      settings.setAutostart(enabled).apply();
    }
  }
}
