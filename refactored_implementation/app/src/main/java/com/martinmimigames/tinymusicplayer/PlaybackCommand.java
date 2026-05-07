package com.martinmimigames.tinymusicplayer;

/**
 * Replace Type Code with Strategy: each playback command knows how to apply
 * itself to a Service, eliminating the switch statement in Service.onStart.
 */
enum PlaybackCommand {
  NULL((byte) 0) {
    @Override void apply(Service s) { /* no-op */ }
  },
  PLAY_PAUSE((byte) 1) {
    @Override void apply(Service s) { s.setState(!s.isPlaying(), s.isLooping()); }
  },
  KILL((byte) 2) {
    @Override void apply(Service s) { s.stopSelf(); }
  },
  PLAY((byte) 3) {
    @Override void apply(Service s) { s.setState(true, s.isLooping()); }
  },
  PAUSE((byte) 4) {
    @Override void apply(Service s) { s.setState(false, s.isLooping()); }
  },
  LOOP((byte) 5) {
    @Override void apply(Service s) { s.setState(s.isPlaying(), !s.isLooping()); }
  };

  final byte code;

  PlaybackCommand(byte code) {
    this.code = code;
  }

  abstract void apply(Service service);

  static PlaybackCommand fromCode(byte code) {
    for (PlaybackCommand c : values()) {
      if (c.code == code) return c;
    }
    return NULL;
  }
}
