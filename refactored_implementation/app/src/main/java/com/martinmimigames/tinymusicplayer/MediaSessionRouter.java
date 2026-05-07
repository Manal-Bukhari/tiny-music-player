package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;

/**
 * Lollipop+ media-button routing via MediaSession (smell 3.14 / 3.18).
 *
 * Owns mediaSession + playbackStateBuilder unconditionally — these fields are
 * never null for the lifetime of an instance, so the Temporary Field smell is
 * gone. The class no longer extends BroadcastReceiver, so Refused Bequest is
 * also gone.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
final class MediaSessionRouter implements MediaButtonRouter {

  private final Service service;
  private MediaSession mediaSession;
  private PlaybackState.Builder playbackStateBuilder;

  MediaSessionRouter(Service service) {
    this.service = service;
  }

  @Override
  public void create() {
    mediaSession = new MediaSession(service, MediaSessionRouter.class.toString());
    mediaSession.setCallback(new MediaSession.Callback() {
      @Override
      public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        dispatch(mediaButtonIntent);
        return super.onMediaButtonEvent(mediaButtonIntent);
      }
    });

    playbackStateBuilder = new PlaybackState.Builder();
    playbackStateBuilder.setActions(
      PlaybackState.ACTION_PLAY
        | PlaybackState.ACTION_PAUSE
        | PlaybackState.ACTION_PLAY_PAUSE);
    mediaSession.setPlaybackState(playbackStateBuilder.build());
    mediaSession.setActive(true);
  }

  @Override
  public void setState(Playback playback) {
    final int state = playback.playing
      ? PlaybackState.STATE_PLAYING
      : PlaybackState.STATE_PAUSED;
    playbackStateBuilder.setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
    mediaSession.setPlaybackState(playbackStateBuilder.build());
  }

  @Override
  public void destroy() {
    mediaSession.setActive(false);
    mediaSession.release();
  }

  /** Hand a media-button intent off to the Service via PlaybackCommand. */
  private void dispatch(Intent mediaButtonIntent) {
    final android.view.KeyEvent event =
      mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    if (event == null || event.getAction() != android.view.KeyEvent.ACTION_DOWN) return;
    final PlaybackCommand command = HWListener.mapKeyToCommand(event.getKeyCode());
    if (command == null) return;
    service.startService(HWListener.buildServiceIntent(service, command));
  }
}
