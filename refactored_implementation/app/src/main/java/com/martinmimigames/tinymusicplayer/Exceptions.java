package com.martinmimigames.tinymusicplayer;

import android.content.Context;

import java.io.IOException;

import mg.utils.notify.ToastHelper;

/**
 * Refactorings applied:
 * - Inline Middle Man (smell 3.16) / Lazy Class (smell 3.12 / anti-pattern 4.2):
 *   the class no longer just delegates to ToastHelper. It now owns the
 *   exception-type → user-message mapping that previously lived as a four-arm
 *   catch ladder in Service.setAudio.
 *
 * The IllegalArgument / IllegalState / IO / Security String constants are kept
 * for use from AudioPlayer (which still calls throwError directly) and so the
 * messages stay centralised and translatable.
 */
final class Exceptions {
  static final String IllegalArgument = "Requires cookies, which the app does not support.";
  static final String IllegalState = "Unusable player state, close app and try again.";
  static final String IO = "Read error, try again later.";
  static final String Security = "File location protected, cannot be accessed.";

  /** Display a toast for a free-form error message. */
  static void throwError(Context context, String msg) {
    ToastHelper.showLong(context, msg);
  }

  /** Map a playback-startup exception to its user-facing message and report it. */
  static void report(Context context, Throwable t) {
    final String msg;
    if (t instanceof IllegalArgumentException) msg = IllegalArgument;
    else if (t instanceof SecurityException)   msg = Security;
    else if (t instanceof IllegalStateException) msg = IllegalState;
    else if (t instanceof IOException)         msg = IO;
    else                                       msg = IllegalState;
    throwError(context, msg);
  }
}
