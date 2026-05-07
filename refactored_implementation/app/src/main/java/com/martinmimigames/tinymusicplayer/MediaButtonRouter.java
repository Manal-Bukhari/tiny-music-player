package com.martinmimigames.tinymusicplayer;

/**
 * Lifecycle + state contract for routing hardware media-button events into the
 * Service. Implementations are chosen once at construction time per SDK band:
 *
 *   Build.VERSION.SDK_INT >= LOLLIPOP -> MediaSessionRouter (uses MediaSession)
 *   Build.VERSION.SDK_INT <  LOLLIPOP -> HWListener         (uses BroadcastReceiver)
 *
 * This kills the Temporary Field smell (HWListener.cn / mediaSession /
 * playbackStateBuilder were null in roughly half of the SDK bands) and the
 * Refused Bequest smell (HWListener used to extend BroadcastReceiver but
 * skipped the receiver mechanism entirely on Lollipop+).
 */
interface MediaButtonRouter {
  void create();
  void setState(Playback playback);
  void destroy();
}
