package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.io.File;

/**
 * Value object for the (uri, title) clump that previously appeared inline in
 * Notifications.getNotification (smell 3.8 / anti-pattern 4.10).
 */
final class Track {
  final Uri uri;
  final String title;

  Track(Uri uri, String title) {
    this.uri = uri;
    this.title = title;
  }

  static Track fromUri(Uri uri) {
    return new Track(uri, new File(uri.getPath()).getName());
  }
}
