package com.martinmimigames.tinymusicplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import mg.utils.notify.NotificationHelper;

/**
 * Refactorings applied:
 * - Replace Magic Number with Symbolic Constant: SDK literals replaced with
 *   Build.VERSION_CODES; hard-coded UI strings extracted as named constants
 *   (smell 3.20).
 * - Extract Method: setupChannel, applyBuilderDefaults, applyJellyBeanActions,
 *   applyLegacyContent, buildPendingIntentFlags, buildIntentFlags,
 *   buildPlaybackText.
 * - Encapsulate Field (smell 3.17): notification and builder are private; an
 *   accessor exposes the built notification to Service.startForeground.
 * - Introduce Parameter Object (smell 3.4): the three PendingIntents passed to
 *   setupNotificationBuilder are wrapped in PlaybackIntents.
 * - Move Field (smell 3.7): the playback-command extras key is read from
 *   Service rather than from the Launcher activity.
 * - Use service.getPackageName() instead of a hard-coded package literal.
 */
class Notifications {

  public static final String NOTIFICATION_CHANNEL = "nc";
  public static final int NOTIFICATION_ID = 1;

  private static final String CHANNEL_NAME = "Playback Control";
  private static final String CHANNEL_DESCRIPTION = "Notification audio controls";
  private static final String LOOP_ACTION = "loop";
  private static final String TAP_TO_CLOSE = "Tap to close";
  private static final String TAP_TO_PREFIX = "Tap to ";
  private static final String LOOPING_SUFFIX = " | looping";

  private final Service service;
  private Notification notification;
  private Notification.Builder builder;

  public Notifications(Service service) {
    this.service = service;
  }

  /** Encapsulate Field — accessor used by Service.startForeground. */
  Notification getNotification() {
    return notification;
  }

  public void create() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setupChannel();
    }
  }

  private void setupChannel() {
    var channel = NotificationHelper.setupNotificationChannel(
      service, NOTIFICATION_CHANNEL, CHANNEL_NAME, CHANNEL_DESCRIPTION,
      NotificationManager.IMPORTANCE_LOW);
    channel.setSound(null, null);
    channel.setVibrationPattern(null);
  }

  /** Parameter object for the three PendingIntents the builder wires up. */
  static final class PlaybackIntents {
    final PendingIntent playPause;
    final PendingIntent kill;
    final PendingIntent loop;

    PlaybackIntents(PendingIntent playPause, PendingIntent kill, PendingIntent loop) {
      this.playPause = playPause;
      this.kill = kill;
      this.loop = loop;
    }
  }

  void setupNotificationBuilder(String title, PlaybackIntents intents) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return;

    builder = createBuilder();
    applyBuilderDefaults(title);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      applyJellyBeanActions(intents);
    } else {
      applyLegacyContent(intents.kill);
    }
  }

  private Notification.Builder createBuilder() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return new Notification.Builder(service, NOTIFICATION_CHANNEL);
    }
    return new Notification.Builder(service);
  }

  private void applyBuilderDefaults(String title) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setCategory(Notification.CATEGORY_SERVICE);
    }
    builder.setSmallIcon(R.drawable.ic_notif);
    builder.setContentTitle(title);
    builder.setSound(null);
    builder.setVibrate(null);
  }

  private void applyJellyBeanActions(PlaybackIntents intents) {
    builder.setContentIntent(intents.playPause);
    builder.addAction(0, LOOP_ACTION, intents.loop);
    builder.addAction(0, TAP_TO_CLOSE, intents.kill);
  }

  private void applyLegacyContent(PendingIntent killIntent) {
    builder.setContentText(TAP_TO_CLOSE);
    builder.setContentIntent(killIntent);
  }

  void setState(Playback playback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
    builder.setContentText(buildPlaybackText(playback));
    buildNotification();
    update();
  }

  private String buildPlaybackText(Playback playback) {
    final String action = playback.playing ? "pause" : "play";
    final String suffix = playback.looping ? LOOPING_SUFFIX : "";
    return TAP_TO_PREFIX + action + suffix;
  }

  /**
   * Build a PendingIntent that re-enters the Service with a playback action.
   * The request-code is derived from the command's own byte (id == code) so a
   * single source of truth identifies each action.
   */
  PendingIntent genIntent(PlaybackCommand command) {
    return PendingIntent.getService(
      service,
      command.code,
      new Intent(service, Service.class)
        .addFlags(buildIntentFlags())
        .putExtra(Service.EXTRA_PLAYBACK_COMMAND, command.code),
      buildPendingIntentFlags());
  }

  private int buildPendingIntentFlags() {
    int flags = PendingIntent.FLAG_IMMUTABLE;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
      flags |= PendingIntent.FLAG_UPDATE_CURRENT;
    }
    return flags;
  }

  private int buildIntentFlags() {
    int flags = Intent.FLAG_ACTIVITY_NO_HISTORY;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
      flags |= Intent.FLAG_ACTIVITY_NO_ANIMATION;
    }
    return flags;
  }

  void genNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      buildNotification();
    } else {
      notification = new Notification();
    }
  }

  void buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      notification = builder.build();
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      notification = builder.getNotification();
    }
  }

  void setupNotification(String title, PendingIntent killIntent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) return;
    notification.contentView = new RemoteViews(service.getPackageName(), R.layout.notif);
    notification.icon = R.drawable.ic_notif;
    notification.audioStreamType = AudioManager.STREAM_MUSIC;
    notification.sound = null;
    notification.contentIntent = killIntent;
    notification.contentView.setTextViewText(R.id.notif_title, title);
    notification.vibrate = null;
  }

  void getNotification(final Uri uri) {
    final Track track = Track.fromUri(uri);

    var intents = new PlaybackIntents(
      genIntent(PlaybackCommand.PLAY_PAUSE),
      genIntent(PlaybackCommand.KILL),
      genIntent(PlaybackCommand.LOOP));

    setupNotificationBuilder(track.title, intents);
    genNotification();
    setupNotification(track.title, intents.kill);

    update();
  }

  private void update() {
    NotificationHelper.send(service, NOTIFICATION_ID, notification);
  }

  void destroy() {
    NotificationHelper.unsend(service, NOTIFICATION_ID);
  }
}
