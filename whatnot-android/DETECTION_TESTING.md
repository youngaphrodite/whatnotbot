# Giveaway detection validation

The service is restricted to `com.whatnot_mobile` in both its configuration and
its active-window check. It matches visible text or content descriptions after
case, whitespace and apostrophe normalization. Supported labels are those already
present in the original implementation plus the verified phone screenshot: `Giveaway`, `Enter Giveaway`, `Follow and
Enter`, `Follow Host & Enter Giveaway`, and `You're in the Giveaway`. Generic `Enter` and `You're in` are excluded
because they can describe unrelated actions. No coordinates or current Whatnot
resource IDs are assumed.

Clicks use ACTION_CLICK on the labeled node or its nearest clickable ancestor.
There is deliberately no blind coordinate fallback. If the installed Whatnot
version exposes different labels, combines labels with other text, or omits
accessibility click actions, inspect its accessibility tree on a phone before
adding support. Do not upload screen dumps containing account or chat data.

An accepted click is not confirmation. The counter increments only if this
service attempted entry and then sees the explicit confirmation label. Already
entered giveaways are not counted. Attempts expire after ten seconds and use a
three-second cooldown before retrying. No giveaway identifier is available, so
exact deduplication across navigation, process restarts or hidden confirmations
cannot be guaranteed. A confirmation appearing after the timeout is not counted.

## Build checks

With Java 17, Gradle 8.0 and Android SDK 34 installed:

```
cd whatnot-android
gradle --no-daemon assembleDebug testDebugUnitTest lintDebug
```

The existing GitHub workflow builds the debug APK on pushes. It is unchanged.
The repository wrapper lacks gradle-wrapper.jar; use the installed Gradle as CI does.

## Required phone tests

- Re-enable the accessibility service after installing the APK so Android reloads
  the package filter. Start the bot and open a live Whatnot giveaway.
- Check collapsed and expanded cards, text and content-description labels, disabled
  entry controls, already entered cards and entry confirmation. Confirm one count
  per observed successful entry, and no count for a rejected click or missing
  confirmation.
- Check different display sizes, font scales, orientations, and window modes.
- Leave Whatnot during entry; verify there are no actions in other apps. Confirm
  unrelated Enter controls and giveaway chat messages are not activated.
- Pause/resume with the overlay, stop/start from the activity, disable/re-enable
  accessibility, interrupt the service and restart the app process. Ensure no
  stale callbacks act after stopping and scanning resumes when explicitly active.
- Test a missing/disappearing entry button: scanning must recover after timeout.

The Android manifest has no Internet permission; the service does not store or
transmit screen text. Statistics remain in private preferences. Android backup
is still enabled as before, so the count may be backed up by the OS. Both services
are non-exported and accessibility binding requires the system permission.

Phone-reported follow-host fix: recognize the exact `Follow Host & Enter Giveaway`
label. When an entry label is visible but cannot be clicked, do not fall back to
a giveaway heading that could open item details. Test both followed and unfollowed
hosts; follow-and-enter intentionally follows the host as the button states.
