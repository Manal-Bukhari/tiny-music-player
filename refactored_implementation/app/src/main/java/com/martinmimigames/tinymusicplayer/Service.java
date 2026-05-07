package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

/**
 * Service for playing music.
 *
 * Refactorings applied:
 * - Replace Conditional with Polymorphism: byte switch on the playback-command
 *   extra is delegated to PlaybackCommand.apply().
 * - Replace Temp with Query: isPlaying / isLooping queried instead of stored.
 * - Extract Method: handleSelfIntent / handleExternalIntent / startPlayback split out.
 * - Move Field (smell 3.7): the playback-command extras key now lives on the
 *   Service that owns the protocol, not on the Launcher activity.
 * - Introduce Parameter Object (smell 3.8): setState now accepts a Playback.
 * - Inline Middle Man (smell 3.16 / 4.2): the four-arm catch ladder is delegated
 *   to Exceptions.report, which absorbs real exception-to-message logic.
 */
public class Service extends android.app.Service {

  /** Intent extras key for a {@link PlaybackCommand#code}. */
  static final String EXTRA_PLAYBACK_COMMAND = "type";

  final MediaButtonRouter mediaButtons;
  final Notifications notifications;

  private AudioPlayer audioPlayer;

  public Service() {
    mediaButtons = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
      ? new MediaSessionRouter(this)
      : new HWListener(this);
    notifications = new Notifications(this);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    mediaButtons.create();
    notifications.create();
    super.onCreate();
  }

  @Override
  public void onStart(final Intent intent, final int startId) {
    if (intent.getAction() == null) {
      handleSelfIntent(intent);
    } else {
      handleExternalIntent(intent);
    }
  }

  private void handleSelfIntent(Intent intent) {
    final byte code = intent.getByteExtra(EXTRA_PLAYBACK_COMMAND, PlaybackCommand.NULL.code);
    PlaybackCommand.fromCode(code).apply(this);
  }

  private void handleExternalIntent(Intent intent) {
    switch (intent.getAction()) {
      case Intent.ACTION_VIEW -> setAudio(intent.getData());
      case Intent.ACTION_SEND -> setAudio(intent.getParcelableExtra(Intent.EXTRA_STREAM));
    }
  }

  boolean isPlaying() {
    return audioPlayer.isPlaying();
  }

  boolean isLooping() {
    return audioPlayer.isLooping();
  }

  void setAudio(final Uri audioLocation) {
    try {
      startPlayback(audioLocation);
    } catch (IllegalArgumentException | SecurityException
             | IllegalStateException | IOException e) {
      Exceptions.report(this, e);
    }
  }

  private void startPlayback(Uri audioLocation) throws IOException {
    audioPlayer = new AudioPlayer(this, audioLocation);
    audioPlayer.start();

    notifications.getNotification(audioLocation);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
      startForeground(Notifications.NOTIFICATION_ID, notifications.getNotification());
    }
  }

  /** Convenience overload kept so existing two-arg call sites still compile. */
  void setState(boolean playing, boolean looping) {
    setState(new Playback(playing, looping));
  }

  void setState(Playback state) {
    audioPlayer.setState(state);
    mediaButtons.setState(state);
    notifications.setState(state);
  }

  @TargetApi(Build.VERSION_CODES.ECLAIR)
  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    onStart(intent, startId);
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    notifications.destroy();
    mediaButtons.destroy();
    if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();
    super.onDestroy();
  }
}
