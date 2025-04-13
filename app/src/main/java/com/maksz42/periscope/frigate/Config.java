package com.maksz42.periscope.frigate;

import com.maksz42.periscope.utils.IO;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Config {
  public Config() { }

  public List<String> getCameras() throws IOException, InterruptedException {
    String response;
    try (
        Client.CloseableConnection cc =
            Client.openConnection("api/config", true);
        InputStream is = cc.get().getInputStream()
    ) {
      response = IO.readAllText(is);
    }
    JSONObject config;
    try {
      config = new JSONObject(response);
    } catch (JSONException e) {
      throw new InvalidResponseException(e);
    }
    JSONObject cameras = config.optJSONObject("cameras");
    if (cameras == null) {
      return Collections.emptyList();
    }
    Iterator<String> keys = cameras.keys();
    List<String> cameraNames = new ArrayList<>(cameras.length());
    while (keys.hasNext()) {
      String key = keys.next();
      cameraNames.add(key);
    }
    Collections.sort(cameraNames, String::compareToIgnoreCase);
    return cameraNames;
  }
}
