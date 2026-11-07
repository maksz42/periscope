package com.maksz42.periscope.overlay;

import android.view.KeyEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides, for each remote key, whether the camera feature takes it and what it means. Pure
 * logic.
 *
 * Holding the chosen button (Info unless the user picked another), or pressing it twice
 * quickly if the user prefers, opens the chooser. That button itself is never taken, so its
 * presses keep their normal meaning in every app and on HDMI.
 *
 * An accessibility service sees a held button as one down and one up, without the repeats an
 * app gets, so a hold is measured with a timer: {@link #onKey} asks for one with
 * {@link Action#START_HOLD_TIMER}, and {@link #onHoldTimer} decides when it runs out. While the chooser is showing, it takes only Up, Down, OK and
 * Back; every other key, and every key while the chooser is hidden, goes to the show untouched.
 *
 * A press is owned by whoever got its first down event, until its up event: if the chooser
 * took the down, it also takes the repeats and the up, even after the chooser has closed; if
 * the show got the down, it also gets the up, even if the chooser opened in between. So the show
 * never sees half a key press.
 */
public final class KeyGate {
  enum Action { NONE, OPEN_CHOOSER, UP, DOWN, SELECT, CANCEL, START_HOLD_TIMER }

  /** How the chosen button opens the chooser. */
  public enum Gesture {
    HOLD,
    DOUBLE_PRESS;

    /** The stored value, or HOLD for anything unknown. */
    public static Gesture parse(String value) {
      for (Gesture g : values()) {
        if (g.name().equals(value)) return g;
      }
      return HOLD;
    }
  }

  static final class Result {
    final boolean consume;
    final Action action;

    Result(boolean consume, Action action) {
      this.consume = consume;
      this.action = action;
    }
  }

  @SuppressWarnings("InlinedApi") // a constant; older systems simply never send it
  public static final int DEFAULT_HOLD_KEY = KeyEvent.KEYCODE_INFO;
  /** How long the button must be held. */
  static final long HOLD_MS = 500;
  /** The longest gap between the two presses of a double press. */
  static final long DOUBLE_PRESS_MS = 400;

  private static final Result PASS = new Result(false, Action.NONE);
  private static final Result TAKEN = new Result(true, Action.NONE);

  private int holdKey = DEFAULT_HOLD_KEY;
  private Gesture gesture = Gesture.HOLD;
  private boolean firedThisHold;
  /** Down time of the trigger press in progress, or -1. */
  private long holdDownTime = -1;
  /** When the last short press of the trigger was released, for a double press; or -1. */
  private long lastTapUpTime = -1;
  private final Set<Integer> owned = new HashSet<>();

  /** Changes the button that opens the chooser; a button {@link #canTrigger} refuses is ignored. */
  void setHoldKey(int keyCode) {
    if (canTrigger(keyCode) && keyCode != holdKey) {
      holdKey = keyCode;
      resetTrigger();
    }
  }

  void setGesture(Gesture gesture) {
    if (gesture != null && gesture != this.gesture) {
      this.gesture = gesture;
      resetTrigger();
    }
  }

  /** Forgets any press in progress, so a pending hold timer opens nothing. */
  void resetTrigger() {
    firedThisHold = false;
    holdDownTime = -1;
    lastTapUpTime = -1;
  }

  /**
   * The hold timer asked for by {@link Action#START_HOLD_TIMER} ran out, for the press that went
   * down at {@code downTime}. Opens (or closes) the chooser if that press is still held.
   */
  Result onHoldTimer(long downTime, boolean chooserVisible) {
    if (gesture != Gesture.HOLD || downTime != holdDownTime || firedThisHold) return PASS;
    firedThisHold = true;
    return new Result(false, chooserVisible ? Action.CANCEL : Action.OPEN_CHOOSER);
  }

  int holdKey() {
    return holdKey;
  }

  /**
   * Whether a button may be the one held to open the chooser. Holding it also reaches the show,
   * so buttons whose hold would do something there (navigation, OK, Back, Home, power, volume,
   * play/pause) are refused, as are the chooser's own keys.
   */
  public static boolean canTrigger(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_UNKNOWN:
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_NUMPAD_ENTER:
      case KeyEvent.KEYCODE_BACK:
      case KeyEvent.KEYCODE_HOME:
      case KeyEvent.KEYCODE_POWER:
      case KeyEvent.KEYCODE_VOLUME_UP:
      case KeyEvent.KEYCODE_VOLUME_DOWN:
      case KeyEvent.KEYCODE_VOLUME_MUTE:
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
      case KeyEvent.KEYCODE_MEDIA_PLAY:
      case KeyEvent.KEYCODE_MEDIA_PAUSE:
        return false;
      default:
        return true;
    }
  }

  /** "KEYCODE_PROG_RED" becomes "Prog red": a readable name from KeyEvent.keyCodeToString. */
  public static String label(String keyCodeName) {
    String name = keyCodeName.startsWith("KEYCODE_") ? keyCodeName.substring(8) : keyCodeName;
    name = name.replace('_', ' ').toLowerCase(Locale.US);
    return name.length() == 0 ? keyCodeName : Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  Result onKey(int keyCode, int action, int repeatCount, boolean longPress, long downTime,
               long eventTime, boolean chooserVisible) {
    if (keyCode == holdKey) {
      Action toggle = chooserVisible ? Action.CANCEL : Action.OPEN_CHOOSER;
      if (gesture == Gesture.DOUBLE_PRESS) {
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
          if (lastTapUpTime >= 0 && downTime - lastTapUpTime <= DOUBLE_PRESS_MS) {
            lastTapUpTime = -1;
            holdDownTime = -1; // this press opened it; its release doesn't start another
            return new Result(false, toggle);
          }
          holdDownTime = downTime;
        } else if (action == KeyEvent.ACTION_UP) {
          // Only a short press counts as the first half of a double press.
          lastTapUpTime = holdDownTime >= 0 && eventTime - holdDownTime < HOLD_MS ? eventTime : -1;
          holdDownTime = -1;
        }
        return PASS;
      }
      if (action == KeyEvent.ACTION_UP) {
        firedThisHold = false;
        holdDownTime = -1;
        return PASS;
      }
      if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
        firedThisHold = false;
        holdDownTime = downTime;
        return new Result(false, Action.START_HOLD_TIMER);
      }
      // Devices that do send repeats to the service can also end the hold this way.
      boolean held = repeatCount > 0 && (longPress || eventTime - downTime >= HOLD_MS);
      if (held && !firedThisHold) {
        firedThisHold = true;
        return new Result(false, toggle);
      }
      return PASS;
    }

    boolean firstDown = action == KeyEvent.ACTION_DOWN && repeatCount == 0;
    if (!firstDown) {
      // The rest of a press goes wherever its first down went.
      if (!owned.contains(keyCode)) return PASS;
      if (action == KeyEvent.ACTION_UP) owned.remove(keyCode);
      return TAKEN;
    }
    owned.remove(keyCode); // a new press; a lost up must not keep the key owned
    if (!chooserVisible) return PASS;
    Action a = chooserAction(keyCode);
    if (a == Action.NONE) return PASS;
    owned.add(keyCode);
    return new Result(true, a);
  }

  private static Action chooserAction(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
        return Action.UP;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        return Action.DOWN;
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_NUMPAD_ENTER:
        return Action.SELECT;
      case KeyEvent.KEYCODE_BACK:
        return Action.CANCEL;
      default:
        return Action.NONE;
    }
  }
}
