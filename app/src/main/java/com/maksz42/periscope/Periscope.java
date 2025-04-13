package com.maksz42.periscope;

import android.app.Application;

import com.maksz42.periscope.frigate.Client;
import com.maksz42.periscope.helper.Settings;

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
  }
}
