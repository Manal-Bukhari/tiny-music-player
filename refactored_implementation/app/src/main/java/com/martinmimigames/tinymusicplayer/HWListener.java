package com.martinmimigames.tinymusicplayer;

import static android.content.Intent.EXTRA_KEY_EVENT;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyEvent;

/**
 * Pre-Lollipop hardware media-button router (smell 3.14 / 3.18).
 *
 * Kept as a real BroadcastReceiver so the manifest entry continues to work on
 * legacy devices. The Lollipop+ path lives in MediaSessionRouter — this class
 * is no longer a hybrid, so the Temporary Field smell (mediaSession /
 * playbackStateBuilder being null on legacy devices) and the Refused Bequest
 * smell (Lollipop+ never used onReceive) are both gone.
 *
 * The two helpers mapKeyToCommand / buildServiceIntent are static and shared
 * with MediaSessionRouter so the keycode-to-command mapping stays in one place.
 */
public class HWListener extends BroadcastReceiver implements MediaButtonRouter {

  private final Service service;
  private ComponentName cn;

  /** Manifest-instantiated, no-arg ctor (BroadcastReceiver contract). */
  public HWListener() {
    super();
    this.service = null;
  }

  public HWListener(Service service) {
    this.service = service;
  }

  @Override
  public void create() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
      cn = new ComponentName(service, HWListener.class);
      audioManager().registerMediaButtonEventReceiver(cn);
    }
    service.registerReceiver(this, new IntentFilter(Intent.ACTION_MEDIA_BUTTON));
  }

  @Override
  public void setState(Playback playback) {
    /* legacy receiver path has no playback-state surface to update */
  }

  @Override
  public void destroy() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
      audioManager().unregisterMediaButtonEventReceiver(cn);
    }
    service.unregisterReceiver(this);
  }

  private AudioManager audioManager() {
    return (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    final KeyEvent event = intent.getParcelableExtra(EXTRA_KEY_EVENT);
    if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;

    final PlaybackCommand command = mapKeyToCommand(event.getKeyCode());
    if (command == null) return;

    context.startService(buildServiceIntent(context, command));
  }

  /** Keycode → PlaybackCommand mapping shared with MediaSessionRouter. */
  static PlaybackCommand mapKeyToCommand(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_MEDIA_PLAY:       return PlaybackCommand.PLAY;
      case KeyEvent.KEYCODE_MEDIA_PAUSE:      return PlaybackCommand.PAUSE;
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return PlaybackCommand.PLAY_PAUSE;
      case KeyEvent.KEYCODE_MEDIA_STOP:       return PlaybackCommand.KILL;
      default:                                return null;
    }
  }

  /** Build the Service-targeted Intent for a command (shared). */
  static Intent buildServiceIntent(Context context, PlaybackCommand command) {
    final Intent intent = new Intent(context, Service.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    intent.putExtra(Service.EXTRA_PLAYBACK_COMMAND, command.code);
    return intent;
  }
}
