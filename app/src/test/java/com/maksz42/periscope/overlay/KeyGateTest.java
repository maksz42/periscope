package com.maksz42.periscope.overlay;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import com.maksz42.periscope.overlay.KeyGate.Action;
import org.junit.Test;

public class KeyGateTest {
  private static final int INFO = KeyEvent.KEYCODE_INFO;
  private final KeyGate gate = new KeyGate();

  private KeyGate.Result key(int code, int action, int repeat, long down, long at, boolean chooser) {
    return gate.onKey(code, action, repeat, false, down, at, chooser);
  }

  @Test
  public void aShortInfoPressGoesToTheShowAndDoesNothingElse() {
    KeyGate.Result down = key(INFO, ACTION_DOWN, 0, 0, 0, false);
    assertFalse(down.consume);
    assertEquals("the press only starts the hold timer", Action.START_HOLD_TIMER, down.action);
    KeyGate.Result up = key(INFO, ACTION_UP, 0, 0, 120, false);
    assertFalse(up.consume);
    assertEquals(Action.NONE, up.action);
    assertEquals("released before the timer: nothing opens", Action.NONE, gate.onHoldTimer(0, false).action);
  }

  @Test
  public void onDevicesThatSendRepeatsAHoldAlsoOpensTheChooser() {
    assertEquals(Action.START_HOLD_TIMER, key(INFO, ACTION_DOWN, 0, 0, 0, false).action);
    assertEquals("a repeat before 500 ms is not yet a hold", Action.NONE, key(INFO, ACTION_DOWN, 1, 0, 450, false).action);
    KeyGate.Result held = key(INFO, ACTION_DOWN, 2, 0, 500, false);
    assertEquals(Action.OPEN_CHOOSER, held.action);
    assertFalse("Info is never taken", held.consume);
    assertEquals("only once per hold", Action.NONE, key(INFO, ACTION_DOWN, 3, 0, 550, true).action);
    assertFalse(key(INFO, ACTION_UP, 0, 0, 900, true).consume);
    key(INFO, ACTION_DOWN, 0, 2000, 2000, true);
    assertEquals("a second hold, with the chooser open, closes it", Action.CANCEL,
        key(INFO, ACTION_DOWN, 1, 2000, 2600, true).action);
  }

  @Test
  public void theSystemsLongPressFlagCountsAsAHold() {
    key(INFO, ACTION_DOWN, 0, 0, 0, false);
    assertEquals(Action.OPEN_CHOOSER, gate.onKey(INFO, ACTION_DOWN, 1, true, 0, 10, false).action);
  }

  @Test
  public void withTheChooserHiddenEveryKeyGoesToTheShow() {
    int[] keys = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK,
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_HOME};
    for (int k : keys) {
      assertFalse("key " + k, key(k, ACTION_DOWN, 0, 0, 0, false).consume);
      assertFalse("key " + k, key(k, ACTION_UP, 0, 0, 0, false).consume);
    }
  }

  @Test
  public void withTheChooserShowingOnlyItsFourKeysAreTakenAndBothHalvesOfEach() {
    Object[][] taken = {{KeyEvent.KEYCODE_DPAD_UP, Action.UP}, {KeyEvent.KEYCODE_DPAD_DOWN, Action.DOWN},
      {KeyEvent.KEYCODE_DPAD_CENTER, Action.SELECT}, {KeyEvent.KEYCODE_ENTER, Action.SELECT}, {KeyEvent.KEYCODE_BACK, Action.CANCEL}};
    for (Object[] t : taken) {
      KeyGate.Result down = key((Integer) t[0], ACTION_DOWN, 0, 0, 0, true);
      assertTrue(down.consume);
      assertEquals(t[1], down.action);
      KeyGate.Result repeat = key((Integer) t[0], ACTION_DOWN, 1, 0, 500, true);
      assertTrue(repeat.consume);
      assertEquals("a held arrow does not race through the list", Action.NONE, repeat.action);
      KeyGate.Result up = key((Integer) t[0], ACTION_UP, 0, 0, 100, true);
      assertTrue(up.consume);
      assertEquals(Action.NONE, up.action);
    }
    for (int k : new int[] {KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
      KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT}) {
      assertFalse("key " + k + " still reaches the show", key(k, ACTION_DOWN, 0, 0, 0, true).consume);
    }
  }

  @Test
  public void okClosesTheChooserButItsReleaseStillDoesNotReachTheShow() {
    KeyGate.Result down = key(KeyEvent.KEYCODE_DPAD_CENTER, ACTION_DOWN, 0, 0, 0, true);
    assertEquals(Action.SELECT, down.action);
    // The service closes the chooser on SELECT; the rest of the press arrives with it hidden.
    assertTrue("a repeat after closing", key(KeyEvent.KEYCODE_DPAD_CENTER, ACTION_DOWN, 1, 0, 500, false).consume);
    assertTrue("the release after closing", key(KeyEvent.KEYCODE_DPAD_CENTER, ACTION_UP, 0, 0, 600, false).consume);
    assertFalse("the next press is the show's again", key(KeyEvent.KEYCODE_DPAD_CENTER, ACTION_DOWN, 0, 1000, 1000, false).consume);
  }

  @Test
  public void backCancelsAndItsReleaseIsTakenToo() {
    assertEquals(Action.CANCEL, key(KeyEvent.KEYCODE_BACK, ACTION_DOWN, 0, 0, 0, true).action);
    assertTrue(key(KeyEvent.KEYCODE_BACK, ACTION_UP, 0, 0, 90, false).consume);
  }

  @Test
  public void aPressThatBeganBeforeTheChooserOpenedFinishesInTheShow() {
    assertFalse(key(KeyEvent.KEYCODE_DPAD_DOWN, ACTION_DOWN, 0, 0, 0, false).consume);
    // Info is held meanwhile and the chooser opens.
    KeyGate.Result repeat = key(KeyEvent.KEYCODE_DPAD_DOWN, ACTION_DOWN, 3, 0, 700, true);
    assertFalse(repeat.consume);
    assertEquals("a repeat of the show's press does not move the list", Action.NONE, repeat.action);
    assertFalse(key(KeyEvent.KEYCODE_DPAD_DOWN, ACTION_UP, 0, 0, 800, true).consume);
  }

  @Test
  public void aLostReleaseDoesNotKeepAKeyTakenForever() {
    key(KeyEvent.KEYCODE_DPAD_UP, ACTION_DOWN, 0, 0, 0, true);
    // No up arrives (for example the service was briefly disconnected); the next press is new.
    assertFalse(key(KeyEvent.KEYCODE_DPAD_UP, ACTION_DOWN, 0, 5000, 5000, false).consume);
    assertFalse(key(KeyEvent.KEYCODE_DPAD_UP, ACTION_UP, 0, 5000, 5100, false).consume);
  }

  @Test
  public void aChosenButtonReplacesInfo() {
    int red = KeyEvent.KEYCODE_PROG_RED;
    gate.setHoldKey(red);
    key(INFO, ACTION_DOWN, 0, 0, 0, false);
    assertEquals("Info is an ordinary key now", Action.NONE, key(INFO, ACTION_DOWN, 1, 0, 600, false).action);
    key(red, ACTION_DOWN, 0, 1000, 1000, false);
    KeyGate.Result held = key(red, ACTION_DOWN, 1, 1000, 1600, false);
    assertEquals(Action.OPEN_CHOOSER, held.action);
    assertFalse("the chosen button is never taken either", held.consume);
  }

  @Test
  public void buttonsWhoseHoldWouldActOnTheShowCannotBeChosen() {
    for (int k : new int[] {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_UNKNOWN}) {
      assertFalse("key " + k, KeyGate.canTrigger(k));
    }
    for (int k : new int[] {INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BOOKMARK,
        KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_CAPTIONS}) {
      assertTrue("key " + k, KeyGate.canTrigger(k));
    }
    gate.setHoldKey(KeyEvent.KEYCODE_BACK);
    assertEquals("a refused button leaves the choice unchanged", INFO, gate.holdKey());
  }

  @Test
  public void buttonNamesAreReadable() {
    assertEquals("Info", KeyGate.label("KEYCODE_INFO"));
    assertEquals("Prog red", KeyGate.label("KEYCODE_PROG_RED"));
    assertEquals("1234", KeyGate.label("1234"));
  }

  @Test
  public void aHoldWithoutRepeatsIsMeasuredByTheTimer() {
    // What the TCL remote sends: one down, then one up 2.3 s later, nothing between.
    KeyGate.Result down = key(INFO, ACTION_DOWN, 0, 1000, 1000, false);
    assertEquals(Action.START_HOLD_TIMER, down.action);
    assertFalse(down.consume);
    assertEquals(Action.OPEN_CHOOSER, gate.onHoldTimer(1000, false).action);
    assertEquals("only once per hold", Action.NONE, gate.onHoldTimer(1000, true).action);
    assertFalse(key(INFO, ACTION_UP, 0, 1000, 3300, true).consume);
  }

  @Test
  public void aShortPressEndsBeforeTheTimerAndOpensNothing() {
    key(INFO, ACTION_DOWN, 0, 1000, 1000, false);
    key(INFO, ACTION_UP, 0, 1000, 1200, false);
    assertEquals(Action.NONE, gate.onHoldTimer(1000, false).action);
  }

  @Test
  public void aTimerFromAnEarlierPressIsIgnored() {
    key(INFO, ACTION_DOWN, 0, 1000, 1000, false);
    key(INFO, ACTION_UP, 0, 1000, 1100, false);
    key(INFO, ACTION_DOWN, 0, 1300, 1300, false);
    assertEquals("the first press's timer", Action.NONE, gate.onHoldTimer(1000, false).action);
    assertEquals(Action.OPEN_CHOOSER, gate.onHoldTimer(1300, false).action);
  }

  @Test
  public void aSecondHoldWithTheListOpenClosesIt() {
    key(INFO, ACTION_DOWN, 0, 5000, 5000, true);
    assertEquals(Action.CANCEL, gate.onHoldTimer(5000, true).action);
  }

  @Test
  public void doublePressingTheGearOpensAndClosesTheList() {
    int gear = KeyEvent.KEYCODE_NOTIFICATION;
    gate.setHoldKey(gear);
    gate.setGesture(KeyGate.Gesture.DOUBLE_PRESS);
    // As recorded from the TCL remote: two taps, 135 ms apart.
    assertEquals(Action.NONE, key(gear, ACTION_DOWN, 0, 9632, 9632, false).action);
    assertFalse(key(gear, ACTION_UP, 0, 9632, 9823, false).consume);
    KeyGate.Result second = key(gear, ACTION_DOWN, 0, 9958, 9958, false);
    assertEquals(Action.OPEN_CHOOSER, second.action);
    assertFalse("the gear is never taken", second.consume);
    key(gear, ACTION_UP, 0, 9958, 10160, true);
    key(gear, ACTION_DOWN, 0, 12000, 12000, true);
    key(gear, ACTION_UP, 0, 12000, 12100, true);
    assertEquals(Action.CANCEL, key(gear, ACTION_DOWN, 0, 12300, 12300, true).action);
  }

  @Test
  public void slowOrLongPressesAreNotADoublePress() {
    int gear = KeyEvent.KEYCODE_NOTIFICATION;
    gate.setHoldKey(gear);
    gate.setGesture(KeyGate.Gesture.DOUBLE_PRESS);
    key(gear, ACTION_DOWN, 0, 0, 0, false);
    key(gear, ACTION_UP, 0, 0, 100, false);
    assertEquals("too far apart", Action.NONE, key(gear, ACTION_DOWN, 0, 900, 900, false).action);
    key(gear, ACTION_UP, 0, 900, 1600, false); // a long press is not a first tap
    assertEquals(Action.NONE, key(gear, ACTION_DOWN, 0, 1700, 1700, false).action);
    key(gear, ACTION_UP, 0, 1700, 1800, false);
    assertEquals("three quick presses open it once", Action.OPEN_CHOOSER, key(gear, ACTION_DOWN, 0, 1900, 1900, false).action);
    key(gear, ACTION_UP, 0, 1900, 2000, true);
    assertEquals(Action.NONE, key(gear, ACTION_DOWN, 0, 2100, 2100, true).action);
    assertEquals("double press never starts a hold timer", Action.NONE, gate.onHoldTimer(2100, true).action);
  }

  @Test
  public void gesturesParseWithHoldAsTheFallback() {
    assertEquals(KeyGate.Gesture.DOUBLE_PRESS, KeyGate.Gesture.parse("DOUBLE_PRESS"));
    assertEquals(KeyGate.Gesture.HOLD, KeyGate.Gesture.parse(null));
  }

  @Test
  public void resettingForgetsAPressSoItsTimerOpensNothing() {
    key(INFO, ACTION_DOWN, 0, 1000, 1000, false);
    gate.resetTrigger();
    assertEquals(Action.NONE, gate.onHoldTimer(1000, false).action);
  }

  @Test
  public void changingTheGestureMidPressCancelsTheHold() {
    key(INFO, ACTION_DOWN, 0, 1000, 1000, false);
    gate.setGesture(KeyGate.Gesture.DOUBLE_PRESS);
    assertEquals(Action.NONE, gate.onHoldTimer(1000, false).action);
  }

  @Test
  public void changingTheButtonMidPressCancelsTheHold() {
    key(INFO, ACTION_DOWN, 0, 1000, 1000, false);
    gate.setHoldKey(KeyEvent.KEYCODE_PROG_RED);
    assertEquals(Action.NONE, gate.onHoldTimer(1000, false).action);
  }
}
