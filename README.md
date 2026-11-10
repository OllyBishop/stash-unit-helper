# STASH Unit Helper

An observational RuneLite prototype for researching STASH interactions and teleport event sequences. It logs existing client events at DEBUG level. No reminders, overlays, automated inputs, menu changes or plugin network requests are implemented.

In-game verification remains pending. Compilation and unit tests cannot confirm the observations in-game.

## Setup

Use JDK 11 or newer with the Gradle wrapper; the existing development setup uses JDK 21. Plugin source targets Java 11. The first build downloads dependencies. RuneLite uses `latest.release`, so the resolved version can change.

From the repository root:

```sh
./gradlew build
./gradlew run
```

The run task enables developer mode and debug logging. Follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) instructions to log in. Enable **STASH Unit Helper** in the plugin list; it is disabled by default.

## Capture Controls

- **Capture diagnostics** starts a fresh capture when toggled on. Toggle off to stop. After expiry, toggle off and on to restart. If left on, it also starts a capture at plugin startup.
- Starting a fresh capture while one is already open records `END` with reason `Capture restarted` before the new `BEGIN`. Configuration and lifecycle changes take effect on the client thread; queued changes from a previous plugin session are ignored.
- Selecting **Search** on `ObjectID.HH_EASY001` or `ObjectID.HH_EASY002` automatically starts a capture if none is active, even with the capture setting off. Their physical STASH locations still need in-game confirmation. Other STASH objects require manual capture.
- Each capture is limited to 120 seconds and 600 event records. Searching during an active capture does not restart it. The next event after expiry reports the end; there is no background timer.
- **Capture variable changes** is off by default. When enabled, it records at most 80 raw varbit/varp candidate events per capture. These do not establish STASH contents or completion state.
- Disabling the plugin or reaching the login screen ends capture. Start a fresh capture for the next session.

## Manual Observation

Perform all game actions yourself. Keep one experiment per capture and note the location, known STASH contents, actions and observed result.

1. With manual capture off, Search each supported STASH separately. Check for `BEGIN`, the Search menu record and object definition. Confirm which physical STASH each identifier represents.
2. Where possible, repeat with an empty and stocked STASH. Manually deposit and withdraw items. Compare inventory/equipment snapshots and STASH-related game messages with what you saw. Repeat with variable capture enabled to collect candidates without treating them as verified state.
3. Start manual capture before teleporting. Try the teleport methods being researched, including a cancelled or failed attempt where possible. Compare menu, widget, location, container and game-state events with the actual outcome. The prototype does not classify teleports or decide when a reminder is needed.
4. Check manual stopping, expiry, plugin disable/re-enable and logout/login. During an automatic capture, toggle **Capture diagnostics** on and confirm `END` precedes the new `BEGIN`. Try rapidly toggling capture and disabling/re-enabling the plugin; stale queued changes should not start a capture in the next session. Check that event records stop at the time/record limit and a fresh capture resets its event numbering and variable budget. An unsupported STASH should require manual capture.
5. Check that menu-option and STASH-related chat text have formatting tags removed, with their full length and spacing preserved. Line breaks and tabs should appear as escaped characters so each event stays on one log line. Confirm the plugin produces no reminders or game actions.

## Reading the Output

Search the Gradle console or `~/.runelite/logs/client.log` for `STASH_DIAG`. The development logging configuration writes to both. `BEGIN` and `END` delimit captures. Event records include `eventNumber`, `tickCount`, `gameState`, `playerLocation` and `eventType`, followed by event-specific fields. `BEGIN` includes `durationSeconds` and `maxRecordCount`; `END` includes `recordCount`, the number of event records emitted during that capture.

All field values are single-quoted. Within values, backslashes are escaped as `\\`, single quotes as `\'` and carriage returns, line feeds and tabs as `\r`, `\n` and `\t`. This also applies to capture reasons. For example, the Search trigger appears as `reason='\'Search\' selected on supported STASH unit'`.

Menu events use `targetId` for the action target; its meaning depends on `menuAction`. `param0` and `param1` are also action-dependent raw parameters. `isConsumed` reports whether the event was consumed when this plugin observed it, not whether another subscriber consumed it later. Object definitions use `objectId`, `varbitId`, `varpId` (the varplayer identifier) and `impostorIds`. Other event-specific fields include `chatMessageType`, `messageText`, `previousLocation`, `newValue`, `widgetGroupId` and `newGameState`.

Details can include menu options and identifiers, supported object definitions, inventory/equipment IDs and quantities, widget groups, STASH-related game messages, location changes and optional variable candidates. Menu target text is omitted because it can contain another player's name. Logs still contain account activity and locations: review excerpts before sharing and exclude unrelated client log lines.

Unit tests cover capture expiry, record limits, stopping, restarting, automatic Search capture, queued lifecycle/configuration changes and log-value formatting. Only manual in-game observation can confirm event sequences. Include the client version, actions, relevant log excerpt and observed result when reporting findings.

## Licence

BSD 2-Clause; see [LICENSE](LICENSE). Existing third-party copyright notices remain in place.
