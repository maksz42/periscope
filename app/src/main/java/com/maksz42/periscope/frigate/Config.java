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
        InputStream is = cc.getConnection().getInputStream()
    ) {
      response = IO.readAllText(is);
    }
    JSONObject config;
    try {
      config = new JSONObject(response);
    } catch (JSONException e) {
      throw new InvalidResponseException(e);
    }

    List<String> cameraNames = new ArrayList<>();
    JSONObject cameras = config.optJSONObject("cameras");
    if (cameras != null) {
      Iterator<String> keys = cameras.keys();
      while (keys.hasNext()) {
        cameraNames.add(keys.next());
      }
    }

    JSONObject birdseye = config.optJSONObject("birdseye");
    if (birdseye != null && birdseye.optBoolean("enabled") && birdseye.optBoolean("restream")) {
      cameraNames.add("birdseye");
    }

    Collections.sort(cameraNames, String::compareToIgnoreCase);
    return cameraNames;
  }
}
