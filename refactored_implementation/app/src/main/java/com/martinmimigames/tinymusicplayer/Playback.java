package com.martinmimigames.tinymusicplayer;

/**
 * Replace Data Clump (playing, looping) — smell 3.8 / anti-pattern 4.10.
 * Carries the two flags that previously travelled together as separate
 * parameters across Service.setState / AudioPlayer.setState /
 * HWListener.setState / Notifications.setState.
 *
 * Named Playback rather than PlaybackState to avoid colliding with
 * android.media.session.PlaybackState used in HWListener.
 */
final class Playback {
  final boolean playing;
  final boolean looping;

  Playback(boolean playing, boolean looping) {
    this.playing = playing;
    this.looping = looping;
  }
}
