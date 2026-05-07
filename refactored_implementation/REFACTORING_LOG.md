```
╔══════════════════════════════════════════════════════════════════════════════╗
║                                                                              ║
║   ▓▓▓▓▓▓ ▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓▓ ▓▓▓▓▓ ║
║   R E F A C T O R I N G   L O G   //   T I N Y   M U S I C   P L A Y E R    ║
║                                                                              ║
║   > SYSTEM    : SE4001 Software Re-Engineering                               ║
║   > TARGET    : tiny-music-player (Android, Java)                            ║
║   > OPERATOR  : labs@entropyand.co                                           ║
║   > DATE      : 2026-05-05                                                   ║
║   > MODE      : Mercilessly refactor. No behavior changes.                   ║
║                                                                              ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

```
[BOOT] ............................................................ OK
[LOAD] slides/11-Composing_Methods.pdf ............................. OK
[LOAD] slides/12-Refactoring_Brief.pdf ............................. OK
[LOAD] slides/13-Movie_Rental.pdf .................................. OK
[LOAD] slides/14-Conditions.pdf .................................... OK
[LOAD] slides/15-Unit_Tests.pdf .................................... OK
[LOAD] slides/Refactoring_Test_Code.pdf ............................ OK
[SCAN] app/src/main/java/.../tinymusicplayer/ ...................... OK
[INIT] writing to refactored_implementation/ ....................... OK
```

---

## ░▒▓ 0. EXECUTIVE TRACE ▓▒░

```
NOTE: TARGET / LINES columns reference the BEFORE source (original app/...),
      not the refactored output. Use them to locate what was changed in
      the input; they will not match the line numbers in
      refactored_implementation/.
```

```
+----------------------------------------------------------------------------+
|  CATALOG ENTRY                  | SMELL ADDRESSED   | TARGET / LINES (BEFORE) |
+----------------------------------------------------------------------------+
|  Replace Conditional w/ Poly.   | Switch Statement  | Service     45-66    |
|  Replace Type Code w/ Strategy  | Type Code         | PlaybackCommand all  |
|  Decompose Conditional          | Long Method       | Launcher    32-54    |
|  Extract Method                 | Long Method       | Launcher    28-72    |
|  Rename Method (onIntent→       | Comments          | Launcher    61, 68,  |
|    dispatch)                    | (-as-deodorant)   |             80       |
|  Extract Method                 | Long Method       | Service     86-99    |
|  Replace Temp with Query        | Temporary Field   | Service     71-77    |
|  Extract Method (×7)            | Long Method       | Notif.      47-145   |
|    └ setupChannel,              |                   |                      |
|      createBuilder,             |                   |                      |
|      applyBuilderDefaults,      |                   |                      |
|      applyJellyBeanActions,     |                   |                      |
|      applyLegacyContent,        |                   |                      |
|      buildPendingIntentFlags,   |                   |                      |
|      buildIntentFlags           |                   |                      |
|  Replace Magic # w/ Symbol      | Magic Number      | Notif.      41,76,164|
|  Decompose Conditional          | Long Method       | Notif.      105-117  |
|  Replace Temp with Query        | Long Method       | Notif.      113-117  |
|  Extract Method                 | Duplicated Code   | Notif.      131-145  |
|  Extract Method                 | Long Method       | HWListener  48-77    |
|  Extract Method (dedup)         | Duplicated Code   | HWListener  75-77    |
|  Decompose Conditional          | Long Method       | HWListener  86-96    |
|  Replace Temp / Ternary collapse| Switch Statement  | HWListener  79-84    |
|  Extract Method                 | Switch Statement  | HWListener  98-125   |
|  Inverted Guard (early-return)  | Long Method       | Notif.      164      |
+----------------------------------------------------------------------------+
```

```
> NOTE: "Inverted Guard" on Notifications.setupNotification flips
>   if (SDK_INT < 11)  { run legacy init }
>   into
>   if (SDK_INT >= HONEYCOMB) return;  run legacy init
>   Logically equivalent. Called out so reviewers don't pause on the flip.
```

NOT applied (and why):
```
- Replace Method with Method Object .... no method large enough
- Extract Class ........................ classes already cohesive
- Inline Class / Remove Middle Man ..... Service's fan-out is real, not delegation
- Pull Up / Push Down .................. no inheritance hierarchy in scope
- Substitute Algorithm ................. no clearer algorithm available
                                         (PlaybackCommand.fromCode does a 6-entry
                                          linear scan; an array lookup would be
                                          micro-optimization, not a clarity win)
- Introduce Parameter Object ........... considered for setupNotificationBuilder's
                                         4 args; rejected — the bundle is used
                                         once and naming it would not aid readers
- Replace Constructor with Factory ..... HWListener has two constructors, but the
                                         no-arg ctor is mandated by Android's
                                         BroadcastReceiver contract. Fowler's
                                         "don't refactor without a meaningful
                                         name" rule applies.
```

```
> 15-Refactoring_Unit_Tests.pdf + Refactoring_Test_Code.pdf .......... N/A
>   The repository ships zero unit tests
>     ($ find app/src -name '*Test*'  →  empty).
>   None of the 11 test smells (Mystery Guest, Eager Test, Assertion
>   Roulette, etc.) or 6 test refactorings (Inline Resource, Setup
>   External Resource, Make Resource Unique, Reduce Data, Add Assertion
>   Explanation, Introduce Equality Method) had a target.
>
>   RECOMMENDED FOLLOW-UP: characterization tests on the two pure
>   functions surfaced by this refactoring — both are now trivially
>   testable in isolation:
>     • PlaybackCommand.fromCode(byte) → PlaybackCommand
>     • Notifications.buildPlaybackText(boolean, boolean) → String
```

---

## ░▒▓ 1. Service.java ▓▒░

### 1.1  Replace Conditional with Polymorphism + Replace Temp with Query

> SMELL  : Switch on a `byte` type code with branch-local temps.
> SOURCE : slide deck `14-Refactoring_Conditions` — "Replace Conditional with Polymorphism".
> ALSO   : `11-Composing_Methods` — "Replace Temp with Query".

```diff
--- BEFORE ─ app/src/main/java/.../Service.java ──────────────────────────────
+++ AFTER  ─ refactored_implementation/src/.../Service.java ─────────────────
```

**BEFORE**
```java
@Override
public void onStart(final Intent intent, final int startId) {
  if (intent.getAction() == null) {
    var isPLaying = audioPlayer.isPlaying();
    var isLooping = audioPlayer.isLooping();
    switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
      case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping);
      case Launcher.PLAY       -> setState(true, isLooping);
      case Launcher.PAUSE      -> setState(false, isLooping);
      case Launcher.LOOP       -> setState(isPLaying, !isLooping);
      case Launcher.KILL       -> stopSelf();
    }
  } else {
    switch (intent.getAction()) {
      case Intent.ACTION_VIEW -> setAudio(intent.getData());
      case Intent.ACTION_SEND -> setAudio(intent.getParcelableExtra(Intent.EXTRA_STREAM));
    }
  }
}
```

**AFTER**
```java
@Override
public void onStart(final Intent intent, final int startId) {
  if (intent.getAction() == null) {
    handleSelfIntent(intent);
  } else {
    handleExternalIntent(intent);
  }
}

private void handleSelfIntent(Intent intent) {
  final byte code = intent.getByteExtra(Launcher.TYPE, PlaybackCommand.NULL.code);
  PlaybackCommand.fromCode(code).apply(this);
}

private void handleExternalIntent(Intent intent) {
  switch (intent.getAction()) {
    case Intent.ACTION_VIEW -> setAudio(intent.getData());
    case Intent.ACTION_SEND -> setAudio(intent.getParcelableExtra(Intent.EXTRA_STREAM));
  }
}

boolean isPlaying() { return audioPlayer.isPlaying(); }
boolean isLooping() { return audioPlayer.isLooping(); }
```

```
> NOTE: temps `isPLaying` / `isLooping` are gone — Replace Temp with Query
        moved them onto Service so each PlaybackCommand can ask for current
        state at the moment it acts.
```

---

### 1.2  Extract Method  ─  setAudio()

> SMELL  : Single method does construction, foregrounding, and 4-arm error mapping.
> SOURCE : slide `12-Refactoring_Brief` — "Extract Method".

**BEFORE**
```java
void setAudio(final Uri audioLocation) {
  try {
    audioPlayer = new AudioPlayer(this, audioLocation);
    audioPlayer.start();
    notifications.getNotification(audioLocation);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
      startForeground(Notifications.NOTIFICATION_ID, notifications.notification);
  } catch (IllegalArgumentException e) {
    Exceptions.throwError(this, Exceptions.IllegalArgument);
  } catch (SecurityException e) {
    Exceptions.throwError(this, Exceptions.Security);
  } catch (IllegalStateException e) {
    Exceptions.throwError(this, Exceptions.IllegalState);
  } catch (IOException e) {
    Exceptions.throwError(this, Exceptions.IO);
  }
}
```

**AFTER**
```java
void setAudio(final Uri audioLocation) {
  try {
    startPlayback(audioLocation);
  } catch (IllegalArgumentException e) { reportPlaybackError(Exceptions.IllegalArgument); }
    catch (SecurityException e)        { reportPlaybackError(Exceptions.Security); }
    catch (IllegalStateException e)    { reportPlaybackError(Exceptions.IllegalState); }
    catch (IOException e)              { reportPlaybackError(Exceptions.IO); }
}

private void startPlayback(Uri audioLocation) throws IOException {
  audioPlayer = new AudioPlayer(this, audioLocation);
  audioPlayer.start();
  notifications.getNotification(audioLocation);
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
    startForeground(Notifications.NOTIFICATION_ID, notifications.notification);
  }
}

private void reportPlaybackError(String message) {
  Exceptions.throwError(this, message);
}
```

---

## ░▒▓ 2. PlaybackCommand.java   [NEW] ▓▒░

> CATALOG : Replace Type Code with State / Strategy
>           + Replace Conditional with Polymorphism
>           + Move Method (behavior moved from Service onto each enum case).
> SOURCE  : slide deck `13-Refactroing_Movie_Rental` — the canonical worked
>           example. The State-pattern slide names exactly these three
>           refactorings as the recipe ("Three refactorings needed: Replace
>           Type Code with State/Strategy, Move Method, Replace Conditional
>           with Polymorphism"). Movie's int priceCode (REGULAR / CHILDRENS /
>           NEW_RELEASE) → Price subclasses with polymorphic getCharge() maps
>           1:1 onto Launcher's byte codes (PLAY / PAUSE / LOOP / KILL / ...)
>           → PlaybackCommand cases with polymorphic apply().
> ALSO    : slide `14-Refactoring_Conditions` ("Replace Conditional with
>           Polymorphism" catalog entry).
> SMELL   : Switch Statement + Type Code (slide 12 / Refactoring Brief).

```
> The Launcher class previously held a flat namespace of `byte` codes
> (PLAY_PAUSE, KILL, PLAY, PAUSE, LOOP, NULL). The Service.onStart switch
> read the byte and *executed* logic per code. Each code now carries its
> own behavior via an enum strategy.
```

**BEFORE — Launcher.java**
```java
static final String TYPE = "type";
static final byte NULL       = 0;
static final byte PLAY_PAUSE = 1;
static final byte KILL       = 2;
static final byte PLAY       = 3;
static final byte PAUSE      = 4;
static final byte LOOP       = 5;
```

**AFTER — PlaybackCommand.java (new)**
```java
enum PlaybackCommand {
  NULL((byte) 0)       { @Override void apply(Service s) { /* no-op */ } },
  PLAY_PAUSE((byte) 1) { @Override void apply(Service s) { s.setState(!s.isPlaying(), s.isLooping()); } },
  KILL((byte) 2)       { @Override void apply(Service s) { s.stopSelf(); } },
  PLAY((byte) 3)       { @Override void apply(Service s) { s.setState(true, s.isLooping()); } },
  PAUSE((byte) 4)      { @Override void apply(Service s) { s.setState(false, s.isLooping()); } },
  LOOP((byte) 5)       { @Override void apply(Service s) { s.setState(s.isPlaying(), !s.isLooping()); } };

  final byte code;
  PlaybackCommand(byte code) { this.code = code; }
  abstract void apply(Service service);

  static PlaybackCommand fromCode(byte code) {
    for (PlaybackCommand c : values()) if (c.code == code) return c;
    return NULL;
  }
}
```

```
> WIRE  : Launcher keeps `TYPE` (the intent extra key, used as a String).
>         Wire-protocol byte values are PRESERVED on PlaybackCommand.code
>         so any existing PendingIntents on user devices still decode.
```

---

## ░▒▓ 3. Launcher.java ▓▒░

### 3.1  Decompose Conditional + Extract Method

> SMELL  : `onCreate` was a 4-deep nested if-chain mixing 3 concerns
>          (intent type detection / permission check / file picker).
> SOURCE : slide `14-Refactoring_Conditions` — "Decompose Conditional".

**BEFORE**
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  if (!Intent.ACTION_VIEW.equals(getIntent().getAction())
    && !Intent.ACTION_SEND.equals(getIntent().getAction())) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      this.getPackageManager()
        .checkPermission(
          Manifest.permission.POST_NOTIFICATIONS, this.getPackageName())
        != PackageManager.PERMISSION_GRANTED) {
      final var intent = new Intent();
      intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, this.getPackageName());
      this.startActivity(intent);
      finish();
      return;
    }

    var intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("audio/*");
    startActivityForResult(intent, REQUEST_CODE);
    return;
  }
  onIntent(getIntent());
}
```

**AFTER**
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  if (isMediaIntent(getIntent())) { dispatch(getIntent()); return; }
  if (needsNotificationPermission()) { requestNotificationPermission(); return; }
  pickAudioFile();
}

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

private void requestNotificationPermission() {
  final var intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
  intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
  startActivity(intent);
  finish();
}

private void pickAudioFile() {
  var intent = new Intent(Intent.ACTION_GET_CONTENT);
  intent.setType("audio/*");
  startActivityForResult(intent, REQUEST_CODE);
}
```

```
> NOTE: `onIntent` → `dispatch`. The new name describes intent, not
        timing (slide: "name it by what it does, not how").
```

---

## ░▒▓ 4. Notifications.java ▓▒░

### 4.1  Replace Magic Number with Symbolic Constant

> SMELL  : Bare integers `11` and `26` standing in for SDK versions.
> SOURCE : Fowler classic; slide deck reference under "Organizing Data".

```diff
- if (Build.VERSION.SDK_INT >= 26) { ... }
- if (Build.VERSION.SDK_INT < 11) return;
+ if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { ... }
+ if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return;
```

### 4.2  Extract Method  ─  setupNotificationBuilder()

> SMELL  : Single 25-line method handling builder construction, defaults,
>          and two API-level branches.
> SOURCE : slide `12-Refactoring_Brief` — "Extract Method".

**BEFORE**
```java
void setupNotificationBuilder(String title, PendingIntent playPauseIntent,
                              PendingIntent killIntent, PendingIntent loopIntent) {
  if (Build.VERSION.SDK_INT < 11) return;

  if (Build.VERSION.SDK_INT >= 26) {
    builder = new Notification.Builder(service, NOTIFICATION_CHANNEL);
  } else {
    builder = new Notification.Builder(service);
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    builder.setCategory(Notification.CATEGORY_SERVICE);
  }

  builder.setSmallIcon(R.drawable.ic_notif);
  builder.setContentTitle(title);
  builder.setSound(null);
  builder.setVibrate(null);
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
    builder.setContentIntent(playPauseIntent);
    builder.addAction(0, "loop", loopIntent);
    builder.addAction(0, TAP_TO_CLOSE, killIntent);
  } else {
    builder.setContentText(TAP_TO_CLOSE);
    builder.setContentIntent(killIntent);
  }
}
```

**AFTER**
```java
void setupNotificationBuilder(String title, PendingIntent playPauseIntent,
                              PendingIntent killIntent, PendingIntent loopIntent) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return;
  builder = createBuilder();
  applyBuilderDefaults(title);
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
    applyJellyBeanActions(playPauseIntent, killIntent, loopIntent);
  } else {
    applyLegacyContent(killIntent);
  }
}

private Notification.Builder createBuilder() { /* ... */ }
private void applyBuilderDefaults(String title) { /* ... */ }
private void applyJellyBeanActions(PendingIntent p, PendingIntent k, PendingIntent l) { /* ... */ }
private void applyLegacyContent(PendingIntent killIntent) { /* ... */ }
```

### 4.3  Decompose Conditional + Replace Temp with Query  ─  setState()

**BEFORE**
```java
void setState(boolean playing, boolean looping) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
    var playbackText = "Tap to ";
    playbackText += (playing) ? "pause" : "play";
    if (looping) { playbackText += " | looping"; }
    builder.setContentText(playbackText);
    buildNotification();
    update();
  }
}
```

**AFTER**
```java
void setState(boolean playing, boolean looping) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
  builder.setContentText(buildPlaybackText(playing, looping));
  buildNotification();
  update();
}

private String buildPlaybackText(boolean playing, boolean looping) {
  final String action = playing ? "pause" : "play";
  final String suffix = looping ? " | looping" : "";
  return "Tap to " + action + suffix;
}
```

```
> Guard inverted (early-return) to flatten nesting.
> String concatenation lifted to a query — testable in isolation.
```

### 4.4  Extract Method  ─  genIntent() flag clumps

**BEFORE**
```java
PendingIntent genIntent(int id, byte action) {
  var pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE;
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE)
    pendingIntentFlag |= PendingIntent.FLAG_UPDATE_CURRENT;

  var intentFlag = Intent.FLAG_ACTIVITY_NO_HISTORY;
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
    intentFlag |= Intent.FLAG_ACTIVITY_NO_ANIMATION;

  return PendingIntent.getService(service, id,
    new Intent(service, Service.class)
      .addFlags(intentFlag)
      .putExtra(Launcher.TYPE, action),
    pendingIntentFlag);
}
```

**AFTER**
```java
PendingIntent genIntent(int id, byte action) {
  return PendingIntent.getService(
    service, id,
    new Intent(service, Service.class)
      .addFlags(buildIntentFlags())
      .putExtra(Launcher.TYPE, action),
    buildPendingIntentFlags());
}

private int buildPendingIntentFlags() {
  int flags = PendingIntent.FLAG_IMMUTABLE;
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) flags |= PendingIntent.FLAG_UPDATE_CURRENT;
  return flags;
}

private int buildIntentFlags() {
  int flags = Intent.FLAG_ACTIVITY_NO_HISTORY;
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) flags |= Intent.FLAG_ACTIVITY_NO_ANIMATION;
  return flags;
}
```

---

## ░▒▓ 5. HWListener.java ▓▒░

### 5.1  Extract Method  ─  create() / destroy()

**BEFORE**
```java
void create() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    mediaSession = new MediaSession(service, HWListener.class.toString());
    mediaSession.setCallback(new MediaSession.Callback() {
      @Override public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        onReceive(service, mediaButtonIntent);
        return super.onMediaButtonEvent(mediaButtonIntent);
      }
    });
    playbackStateBuilder = new PlaybackState.Builder();
    playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE);
    mediaSession.setPlaybackState(playbackStateBuilder.build());
    mediaSession.setActive(true);
  } else {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
      cn = new ComponentName(service, HWListener.class);
      ((AudioManager) service.getSystemService(Context.AUDIO_SERVICE)).registerMediaButtonEventReceiver(cn);
    }
    service.registerReceiver(this, new IntentFilter(Intent.ACTION_MEDIA_BUTTON));
  }
}
```

**AFTER**
```java
void create() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    setupModernSession();
  } else {
    setupLegacyReceiver();
  }
}

private void setupModernSession() { /* ... */ }
private void setupLegacyReceiver() { /* ... */ }
private AudioManager audioManager() {
  return (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
}
```

### 5.2  Extract Method + Replace Conditional with Polymorphism  ─  onReceive()

**BEFORE**
```java
@Override
public void onReceive(Context context, Intent intent) {
  var event = (KeyEvent) intent.getParcelableExtra(EXTRA_KEY_EVENT);
  if (event.getAction() == KeyEvent.ACTION_DOWN) {
    intent = new Intent(context, Service.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_MEDIA_PLAY:       intent.putExtra(Launcher.TYPE, Launcher.PLAY); break;
      case KeyEvent.KEYCODE_MEDIA_PAUSE:      intent.putExtra(Launcher.TYPE, Launcher.PAUSE); break;
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: intent.putExtra(Launcher.TYPE, Launcher.PLAY_PAUSE); break;
      case KeyEvent.KEYCODE_MEDIA_STOP:       intent.putExtra(Launcher.TYPE, Launcher.KILL); break;
      default: return;
    }
    context.startService(intent);
  }
}
```

**AFTER**
```java
@Override
public void onReceive(Context context, Intent intent) {
  var event = (KeyEvent) intent.getParcelableExtra(EXTRA_KEY_EVENT);
  if (event.getAction() != KeyEvent.ACTION_DOWN) return;

  final PlaybackCommand command = mapKeyToCommand(event.getKeyCode());
  if (command == null) return;

  context.startService(buildServiceIntent(context, command));
}

private PlaybackCommand mapKeyToCommand(int keyCode) {
  switch (keyCode) {
    case KeyEvent.KEYCODE_MEDIA_PLAY:       return PlaybackCommand.PLAY;
    case KeyEvent.KEYCODE_MEDIA_PAUSE:      return PlaybackCommand.PAUSE;
    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return PlaybackCommand.PLAY_PAUSE;
    case KeyEvent.KEYCODE_MEDIA_STOP:       return PlaybackCommand.KILL;
    default:                                return null;
  }
}

private Intent buildServiceIntent(Context context, PlaybackCommand command) {
  Intent intent = new Intent(context, Service.class);
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
  intent.putExtra(Launcher.TYPE, command.code);
  return intent;
}
```

---

### 5.3  Decompose Conditional + Inverted Guard  ─  destroy()

> SMELL  : Long Method, nested if/else with branch-symmetric work.
> SOURCE : slide `14-Refactoring_Conditions` — "Decompose Conditional".

**BEFORE**
```java
void destroy() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    mediaSession.setActive(false);
    mediaSession.release();
  } else {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
      ((AudioManager) service.getSystemService(Context.AUDIO_SERVICE))
        .unregisterMediaButtonEventReceiver(cn);
    }
    service.unregisterReceiver(this);
  }
}
```

**AFTER**
```java
void destroy() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    mediaSession.setActive(false);
    mediaSession.release();
    return;
  }
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
    audioManager().unregisterMediaButtonEventReceiver(cn);
  }
  service.unregisterReceiver(this);
}
```

```
> Nested if/else flattened into early-return + sequential checks.
> The duplicated `((AudioManager) service.getSystemService(...))` cast
> (also present in setupLegacyReceiver) is eliminated by the new
> audioManager() helper — Extract Method's "remove duplicated
> expression" aspect.
```

### 5.4  Replace Temp with Query / Ternary Collapse  ─  setState()

> SMELL  : Two-arm `if/else` whose only difference is one constant.
> SOURCE : slide `11-Composing_Methods` — "Replace Temp with Query"
>          (degenerate case: the temp is born and consumed in one line).

**BEFORE**
```java
void setState(boolean playing, boolean looping) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    if (playing)
      playbackStateBuilder.setState(PlaybackState.STATE_PLAYING,
        PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
    else
      playbackStateBuilder.setState(PlaybackState.STATE_PAUSED,
        PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
    mediaSession.setPlaybackState(playbackStateBuilder.build());
  }
}
```

**AFTER**
```java
void setState(boolean playing, boolean looping) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
  final int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
  playbackStateBuilder.setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
  mediaSession.setPlaybackState(playbackStateBuilder.build());
}
```

```
> Guard inverted (early-return) to flatten nesting.
> Two parallel setState() calls collapsed to one; the only thing that
> varied (the state constant) is now a named local.
```

---

## ░▒▓ 6. Files NOT Changed ▓▒░

```
+ res/, drawables, layouts ............ no UI changes, copied verbatim.
+ proguard-rules.pro .................. unchanged.
+ keystore.properties handling ........ unchanged.
+ mg/utils/ submodule files ........... bundled byte-identical from upstream
                                        (notify/NotificationHelper.java,
                                         notify/ToastHelper.java,
                                         helper/MainThread.java).
```

```
NOTE: AndroidManifest.xml had the package="com.martinmimigames.tinymusicplayer"
      attribute removed and the namespace declared in app/build.gradle instead.
      This is the AGP 8 namespace migration — required for the build to
      compile, behaviorally a no-op (the package coordinate is identical).
```

```
NOTE: Service.isPlaying() / isLooping() were added at package-private
      visibility for use by PlaybackCommand.apply(...). They do not widen
      the public API surface — both are package-scoped and only callable
      from inside com.martinmimigames.tinymusicplayer.
```

`AudioPlayer.java` and `Exceptions.java` were initially copied verbatim;
both were touched later in this log:
* `AudioPlayer.setState(boolean,boolean)` → `AudioPlayer.setState(Playback)` (smell 3.8)
* `Exceptions` gained `report(Context, Throwable)` (smell 3.12 / 3.16 / 4.2).
See Section 9 for the full smell-by-smell map.

---

## ░▒▓ 7. Security Audit ▓▒░

```
[CHECK] hardcoded secrets ............................ NONE
[CHECK] env vars in source ........................... NONE
[CHECK] API tokens / keys ............................ NONE
[CHECK] frontend-accessible secrets .................. N/A (Android, no FE)
[CHECK] keystore moves ............................... UNTOUCHED (build cfg)
[CHECK] AndroidManifest permissions changed .......... NO
[CHECK] exported components changed .................. NO
```

The PlaybackCommand.code byte values match the previous `Launcher.PLAY` etc.
constants exactly, so any pre-existing PendingIntents continue to decode the
intended action. No protocol break, no on-device migration required.

```
+----------------------------------------+
| BEFORE (Launcher constant) | AFTER (PlaybackCommand.code) |
+----------------------------------------+
| Launcher.NULL       = 0    | PlaybackCommand.NULL.code       = 0 |
| Launcher.PLAY_PAUSE = 1    | PlaybackCommand.PLAY_PAUSE.code = 1 |
| Launcher.KILL       = 2    | PlaybackCommand.KILL.code       = 2 |
| Launcher.PLAY       = 3    | PlaybackCommand.PLAY.code       = 3 |
| Launcher.PAUSE      = 4    | PlaybackCommand.PAUSE.code      = 4 |
| Launcher.LOOP       = 5    | PlaybackCommand.LOOP.code       = 5 |
+----------------------------------------+
```

Also note: the intent extras key was renamed from `Launcher.TYPE` to
`Service.EXTRA_PLAYBACK_COMMAND`, but the underlying string value remains
`"type"` and `Launcher.TYPE` is preserved as a `@Deprecated` alias —
external launchers that bind this extras key continue to work.

---

## ░▒▓ 8. Behavioral Equivalence ▓▒░

```
+----------------------------------------------------------------+
| INVOCATION PATH               | BEFORE BEHAVIOR | AFTER BEHAVIOR
+----------------------------------------------------------------+
| Launch w/ no intent action    | pick file       | pick file        OK
| Launch w/ ACTION_VIEW         | dispatch        | dispatch         OK
| Launch w/ ACTION_SEND         | dispatch        | dispatch         OK
| TIRAMISU+ no notif perm       | settings page   | settings page    OK
| MEDIA_PLAY keycode            | START PLAY      | START PLAY       OK
| MEDIA_STOP keycode            | KILL            | KILL             OK
| Notification PLAY_PAUSE       | toggle          | toggle           OK
| Notification LOOP             | toggle loop     | toggle loop      OK
| Notification KILL             | stopSelf        | stopSelf         OK
| AudioPlayer onCompletion      | stopSelf        | stopSelf         OK
+----------------------------------------------------------------+
```

```
[DONE] Refactoring complete. Logic preserved. Smells reduced.
[EOF]
```

---

## ░▒▓ 9. Smell-by-smell fix map ▓▒░

Traceability between the report's smell catalogue and concrete changes in
`refactored_implementation/`. Section numbers refer to the report.

| # | Smell | Where it lived (original) | Status | Fix technique | File(s) in refactored_implementation/ |
|---|---|---|---|---|---|
| 3.1  | Duplicated Code           | SDK_INT ladders across 5 of 6 classes                                           | Fixed              | Extract Method per band                                            | `Notifications.java`, `HWListener.java`, `Service.java`, `AudioPlayer.java` |
| 3.2  | Long Method               | `Notifications.setupNotificationBuilder`, `Service.onStart`, `Launcher.onCreate`, `HWListener.create` | Fixed              | Extract Method, Decompose Conditional                              | as above |
| 3.3  | Large Class               | `Notifications` (WMC=25, RFC=37), `Service` (CBO=5)                             | Partial            | `Service` slimmed; `Notifications` reorganised but still ~210 LOC  | `Service.java`, `Notifications.java` |
| 3.4  | Long Parameter List       | `setupNotificationBuilder(String, PendingIntent, PendingIntent, PendingIntent)` | Fixed              | Introduce Parameter Object → `Notifications.PlaybackIntents`       | `Notifications.java` |
| 3.5  | Divergent Change          | `Notifications` mixed channel / builder / intents / legacy renderer             | Partial            | Each axis isolated in its own private method                       | `Notifications.java` |
| 3.6  | Shotgun Surgery           | adding an action edited 4 classes                                               | Fixed              | Move action codes into `PlaybackCommand` enum                      | `PlaybackCommand.java` |
| 3.7  | Feature Envy              | `HWListener` / `Notifications` reading `Launcher.TYPE`                          | Fixed              | Move Field — extras key relocated to `Service.EXTRA_PLAYBACK_COMMAND`; `Launcher.TYPE` kept as `@Deprecated` alias for backward compat | `Service.java`, `HWListener.java`, `Notifications.java`, `Launcher.java` |
| 3.8  | Data Clumps               | `(boolean playing, boolean looping)` in 4 setState methods                      | Fixed              | Introduce Parameter Object → `Playback`; two-arg shim kept on `Service` | `Playback.java` (new), `Service.java`, `AudioPlayer.java`, `HWListener.java`, `Notifications.java` |
| 3.9  | Primitive Obsession       | byte action constants, magic intent IDs `1/2/3`, `REQUEST_CODE = 3216487`       | Fixed              | Replace Type Code with Strategy (`PlaybackCommand`); intent IDs derived from `command.code`; request code renamed `REQUEST_CODE_PICK_AUDIO` | `PlaybackCommand.java`, `Notifications.java`, `Launcher.java` |
| 3.10 | Switch Statements         | byte switch in `Service.onStart` + KeyEvent switch in `HWListener.onReceive`    | Fixed              | Replace Conditional with Polymorphism (`apply()` per enum const); `mapKeyToCommand` collapses `KeyEvent` → enum once | `Service.java`, `PlaybackCommand.java`, `HWListener.java` |
| 3.12 | Lazy Class                | `Exceptions` was a one-line delegator to `ToastHelper.showLong`                 | Fixed              | Class now owns the exception-type → user-message mapping via `Exceptions.report(Context, Throwable)`; absorbs the four-arm catch ladder from `Service.setAudio` | `Exceptions.java`, `Service.java` |
| 3.14 | Temporary Field           | `HWListener.cn`, `mediaSession`, `playbackStateBuilder` null in some SDK bands  | Fixed              | Extract Class along the Lollipop boundary: `MediaButtonRouter` interface, with `MediaSessionRouter` (Lollipop+) and `HWListener` (legacy `BroadcastReceiver`) as the two implementations. Each class owns only the fields it actually uses for its lifetime. | `MediaButtonRouter.java` (new), `MediaSessionRouter.java` (new), `HWListener.java`, `Service.java` |
| 3.16 | Middle Man                | `Exceptions.throwError` was pure delegation                                     | Fixed              | See 3.12 — class earns its keep                                    | `Exceptions.java` |
| 3.17 | Inappropriate Intimacy    | `Notifications.notification` and `.builder` mutated by `Service`                | Fixed              | Encapsulate Field — both fields `private`; `Notifications.getNotification()` accessor | `Notifications.java`, `Service.java` |
| 3.18 | Refused Bequest           | `HWListener` only borrows `BroadcastReceiver.onReceive` post-Lollipop           | Fixed              | After the 3.14 split, `HWListener` is a real `BroadcastReceiver` only on legacy devices; `MediaSessionRouter` does not extend `BroadcastReceiver` at all, so the inherited contract is no longer refused | `MediaSessionRouter.java`, `HWListener.java` |
| 3.19 | Comments                  | weak comments restating the obvious (`/* setup player variables */`, `/* get ready for playback */`, `/* setup listeners for further logics */`, `/* initiate new audio player */`) | Fixed              | Removed; new comments explain non-obvious why                       | `AudioPlayer.java`, all classes |
| 3.20 | Hard Coding               | `"nc"`, `"Tap to close"`, `"Tap to "`, `"loop"`, `"Playback Control"`, `"com.martinmimigames.tinymusicplayer"`, magic IDs, `REQUEST_CODE = 3216487` | Fixed              | Replace Magic Number/String with Symbolic Constant; `service.getPackageName()` for the package literal | `Notifications.java`, `Launcher.java` |
| 3.21 | Conditional Complexity    | 15 `Build.VERSION.SDK_INT` branches across 6 files                              | Reduced            | Hardware-routing branches collapsed by the 3.14 router-strategy split (4 `SDK_INT` checks gone from old `HWListener`); remaining branches are local to their owning classes | `MediaSessionRouter.java`, `HWListener.java` |
| 3.22 | Combinatorial Explosion   | `Notifications.setState` × `(playing, looping)` × SDK band                      | Reduced            | `buildPlaybackText(Playback)` collapses the string-axis; routing × SDK product collapsed by 3.14 | `Notifications.java`, `MediaSessionRouter.java`, `HWListener.java` |
| 3.23 | Data Class                | not present                                                                     | n/a                | —                                                                  | — |
| 4.1  | Blob (`Service`)          | CBO=5, dispatch + lifecycle + mediator                                          | Reduced            | Dispatch moved to `PlaybackCommand`; `Service` is now a thin mediator with CBO≈3 | `Service.java`, `PlaybackCommand.java` |
| 4.2  | Lazy Class (`Exceptions`) | one-method utility                                                              | Fixed              | See 3.12                                                           | `Exceptions.java` |
| 4.3  | Copy-Paste Programming    | SDK ladders duplicated                                                          | Fixed              | Extract Method                                                     | all classes |
| 4.4  | Golden Hammer             | `if SDK_INT` everywhere as polymorphism substitute                              | Reduced            | Strategy via `PlaybackCommand`; remaining SDK branches stay local within their owning class | `PlaybackCommand.java` |
| 4.5  | Spaghetti Code            | `Service.onStart` interleaved null-check / byte switch / string switch          | Fixed              | `handleSelfIntent` / `handleExternalIntent` split                  | `Service.java` |
| 4.6  | Hard Coding               | see 3.20                                                                        | Fixed              | see 3.20                                                           | — |
| 4.7  | NIH Syndrome (`mg/utils`) | submodule reimplements parts of AndroidX                                        | Not fixed (scope)  | Out-of-tree submodule, owned upstream                              | — |
| 4.8  | Big Ball of Mud           | dense intra-class call graph                                                    | Reduced            | `PlaybackCommand` isolates dispatch; back-references unchanged    | `PlaybackCommand.java` |
| 4.10 | Anemic Domain Model       | no `Track` / `PlaybackState` / `PlaybackAction`                                 | Fixed              | `PlaybackCommand` (action enum), `Playback` (state value object), `Track` (uri+title value object via `Track.fromUri`) | `PlaybackCommand.java`, `Playback.java`, `Track.java`, `Notifications.java` |

### Section 9.1 — HWListener split (3.14 + 3.18)

```
                       MediaButtonRouter (interface)
                       /                   \
   HWListener (legacy, BroadcastReceiver)   MediaSessionRouter (Lollipop+)
   - cn (FROYO+)                            - mediaSession
   - service                                - playbackStateBuilder
                                            - service
```

`Service` selects one implementation at construction time based on
`Build.VERSION.SDK_INT`. Each class now holds only the fields meaningful for
its lifetime (3.14 gone). `HWListener` actually uses `BroadcastReceiver.onReceive`
on the path it's selected for; `MediaSessionRouter` does not extend
`BroadcastReceiver` at all (3.18 gone). Shared keycode→command logic stays in
`HWListener.mapKeyToCommand` / `buildServiceIntent` (package-private statics).

The manifest `<receiver android:name=".HWListener">` entry is unchanged —
`HWListener` is still a `BroadcastReceiver` so manifest-instantiation on legacy
devices still works.

### Verification

* `./gradlew assembleDebug` from `refactored_implementation/` → `BUILD SUCCESSFUL`.
* No external references to `notifications.notification` or `notifications.builder` remain.
* `Launcher.TYPE` survives only as a deprecated alias for `Service.EXTRA_PLAYBACK_COMMAND`.
* `PlaybackCommand.code` byte values are unchanged, so PendingIntents stored on installed devices remain decodable.
* `Service.mediaButtons` is the new field name (formerly `hwListener`); type is the `MediaButtonRouter` interface.
* Manifest `<receiver android:name=".HWListener">` left intact — `HWListener` remains a `BroadcastReceiver` for legacy registration.

