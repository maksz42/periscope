package com.maksz42.periscope.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The chooser's rows: one per camera, then action rows. While a camera is showing they are
 * "full screen" and a last "close"; otherwise "open the app". Pure logic.
 */
final class ChooserModel {
  /** What choosing the highlighted row does. */
  enum Choice {
    /** Show the highlighted camera. */
    CAMERA,
    /** Open the camera that is showing full screen in the app. */
    FULL_SCREEN,
    /** Close the camera that is showing. */
    CLOSE,
    /** Open the app's camera grid. */
    OPEN_APP,
    /** Nothing to choose. */
    NONE
  }

  /**
   * Which cameras the list offers: its own selection if the user made one, else the grid's,
   * else null, meaning every camera on the server. An empty selection is a choice too.
   */
  static List<String> cameraSource(List<String> listSelection, List<String> gridSelection) {
    return listSelection != null ? listSelection : gridSelection;
  }

  private final List<String> rows = new ArrayList<>();
  private final List<Choice> actions = new ArrayList<>();
  private final int cameraCount;
  private int index;

  /**
   * @param cameras the cameras to offer, in order
   * @param watching the camera on screen now, or null. While one is showing, the list starts
   *     on the close row, so closing takes the trigger and OK, and any camera is one step away
   *     because the list wraps
   * @param closeLabel the text of the close row, shown only while watching
   * @param fullScreenLabel the text of the full screen row, shown only while watching (just
   *     above close, so it is one Up away); null for none
   * @param openAppLabel the text of the row that opens the app, shown only while not watching
   *     (last, so it is one Up away from the first camera); null for none
   */
  ChooserModel(List<String> cameras, String watching, String closeLabel, String fullScreenLabel,
               String openAppLabel) {
    rows.addAll(cameras);
    cameraCount = cameras.size();
    if (watching != null) {
      if (fullScreenLabel != null) addAction(fullScreenLabel, Choice.FULL_SCREEN);
      addAction(closeLabel, Choice.CLOSE);
    } else if (openAppLabel != null) {
      addAction(openAppLabel, Choice.OPEN_APP);
    }
    index = watching != null ? rows.size() - 1 : 0;
  }

  /** Cameras and, while watching, a close row only. */
  ChooserModel(List<String> cameras, String watching, String closeLabel) {
    this(cameras, watching, closeLabel, null, null);
  }

  private void addAction(String label, Choice choice) {
    rows.add(label);
    actions.add(choice);
  }

  boolean hasCameras() {
    return cameraCount > 0;
  }

  List<String> rows() {
    return Collections.unmodifiableList(rows);
  }

  int index() {
    return index;
  }

  /** Moves the highlight, wrapping around from either end. */
  void move(int by) {
    if (rows.isEmpty()) return;
    index = Math.floorMod(index + by, rows.size());
  }

  /**
   * The rows to show when at most {@code maxRows} fit: a run that contains the highlight,
   * as [first, end).
   */
  int[] window(int maxRows) {
    int n = rows.size();
    if (maxRows <= 0 || n <= maxRows) return new int[] {0, n};
    int first = Math.max(0, Math.min(index - maxRows / 2, n - maxRows));
    return new int[] {first, first + maxRows};
  }

  /** What choosing the highlighted row does. */
  Choice choice() {
    if (rows.isEmpty()) return Choice.NONE;
    return index < cameraCount ? Choice.CAMERA : actions.get(index - cameraCount);
  }

  /** True when the close row is highlighted. */
  boolean closeSelected() {
    return choice() == Choice.CLOSE;
  }

  /** The highlighted camera; null when the close row is highlighted or there are no rows. */
  String selectedCamera() {
    return index < cameraCount ? rows.get(index) : null;
  }
}
