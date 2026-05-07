package com.martinmimigames.tinymusicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Activity for controlling playback by dispatching incoming intents.
 *
 * Refactorings applied:
 * - Decompose Conditional: onCreate's nested if-chain split into named queries
 *   (isMediaIntent, needsNotificationPermission) and Extract Method handlers.
 * - Extract Method: requestNotificationPermission, pickAudioFile, dispatch.
 */
public class Launcher extends Activity {

  /**
   * @deprecated kept as an alias of {@link Service#EXTRA_PLAYBACK_COMMAND}
   *   for any external launcher that already sends this extras key. New
   *   callers should use Service.EXTRA_PLAYBACK_COMMAND directly.
   */
  @Deprecated
  static final String TYPE = Service.EXTRA_PLAYBACK_COMMAND;

  /** Arbitrary unique tag for the audio-picker activity result. */
  private static final int REQUEST_CODE_PICK_AUDIO = 3216487;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (isMediaIntent(getIntent())) {
      dispatch(getIntent());
      return;
    }

    if (needsNotificationPermission()) {
      requestNotificationPermission();
      return;
    }

    pickAudioFile();
  }

  /* ---- Decompose Conditional: named predicates ---- */

  private boolean isMediaIntent(Intent intent) {
    final String action = intent.getAction();
    return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action);
  }

  private boolean needsNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
    return getPackageManager()
      .checkPermission(Manifest.permission.POST_NOTIFICATIONS, getPackageName())
      != PackageManager.PERMISSION_GRANTED;
  }

  /* ---- Extracted handlers ---- */

  private void requestNotificationPermission() {
    final var intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
    startActivity(intent);
    finish();
  }

  private void pickAudioFile() {
    var intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("audio/*");
    startActivityForResult(intent, REQUEST_CODE_PICK_AUDIO);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    dispatch(intent);
  }

  /**
   * Restart service with the given intent.
   * (Renamed from onIntent: name describes intent, per Extract Method mechanics.)
   */
  private void dispatch(Intent intent) {
    intent.setClass(this, Service.class);
    stopService(intent);
    startService(intent);
    finish();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == REQUEST_CODE_PICK_AUDIO && resultCode == Activity.RESULT_OK) {
      intent.setAction(Intent.ACTION_VIEW);
      dispatch(intent);
      return;
    }
    finish();
  }
}
