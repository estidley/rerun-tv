# Rerun

Old-school cable TV, built from your own Plex library. For Fire TV and Android TV.

Turn it on and something's already playing. Flip channels and you land in the middle of whatever's on. There's a 90s cable guide, a CRT tube look, static between channels, and "technical difficulties" when your server naps.

- **Network channels:** Wonderland, Action Club, Kid Castle, Toon Tower, The Attic, Midnight Block, Story Yard, Warp Nine, Coast to Coast, The Premium, and more. The names on screen are parodies. Shows still land by the network and studio stored in Plex.
- **Streaming originals follow the studio and the audience:**
  - Disney+ kids shows go to Wonderland.
  - Marvel and live-action Star Wars go to **The Backlot**.
  - Paramount+ kids shows go to Kid Castle.
  - Max originals go to The Premium.
  - Hulu originals go to The Edge.
  - Netflix, Prime Video and Apple TV+ originals are spread by genre.
- **Theme channels:** Fright Night, The Void, Laughing Stock, Adrenaline, The Living Room, Neon Decade, Dial-Up Cinema, Cereal Hour. More themes show up only when that genre has at least 4 hours: True-ish (documentaries), Canned Laughter (sitcoms), Sob Story (dramedies), Cold Read (psychic and paranormal), This Just In (news), and others such as The Matinee and Case File.
- **Movie channels:** 4 hours of movies adds The Big Picture. 24 hours adds Second Feature. 48 hours adds Late Seating. They share your movies and shuffle independently.
- **Custom channels:** pick genres, shows, collections, decades and ratings right on the TV.
- **East and West:** Settings can add a West feed of the same schedule, 3 hours later. East keeps its name. West adds `-W` and appears immediately after East in the guide and channel surfing, without changing channel numbers. A new West feed is off air until East has three hours of schedule. Editing either feed edits East and updates its West twin.
- **Off air:** a time with nothing scheduled says Off Air in the guide and on the screen, then the next program starts. A short break already on the schedule still says Up Next.
- **Music:** enable Music in Settings and include your Plex music libraries in Manage libraries. Rerun imports audio tracks, not music videos, and adds a station for each genre with at least 4 hours. Genres inherit from albums/artists; untagged tracks go to Unclassified. The guide groups shuffled songs into hourly station blocks such as "Blues Music"; Off Air gaps remain visible. The music screen shows artist, song, album, square album art, elapsed time and song length. Genre/decade-inspired motifs travel slowly with the artwork and text across a black background to reduce fixed-image burn-in risk (not a guarantee against burn-in).
- **Subtitles:** off until you turn them on in Settings. Choose a preferred language or Automatic (Plex's selected track). If your language is unavailable, subtitles stay off for that program. Embedded and external text subtitles play directly; other formats are burned in by Plex.
- **Adaptive guide:** whole rows fill the available guide area when there are enough channels. Larger displays can show more rows; shorter lineups keep readable row heights. Channel names wrap when the row is tall enough, and alternating row colors make the grid easier to follow.
- **Theme colors:** Settings offers Standard blue, 70s earth tones, and 50s diner colors. The choice applies immediately to the guide, menus, and overlays and is saved between sessions.
- **Guide density:** choose Compact, Comfortable, or Large rows in Settings. All three fill the guide area when enough channels exist; taller rows allow more channel-name lines.
- **Manage libraries:** change your included libraries from Settings without signing out. Library pickers show `+` for included libraries and `x` for excluded libraries, with text labels in every theme. Save and sync updates the selection only after a successful fetch and rebuilds the guide, including delayed West feeds, so excluded content no longer airs.
- **Ad library:** setup asks you to select a Plex Other Videos or Movies library or Disable ads before choosing regular libraries. Selecting a source syncs its clips to a separate, persistent ad catalog; Settings also offers Force ad sync to refresh additions/removals. Failed syncs preserve the previous source and catalog. Off clears the catalog. The source is saved per server and excluded from regular library pickers/imports; re-sync regular libraries after selecting a previously imported library. Ads fill scheduled breaks without delaying programs; gaps too short for a clip show Up Next.
- **Guide updates:** rebuilding the guide shows an updating message and elapsed time instead of an empty grid. The guide refreshes after syncs and while open. Playback stays muted in the guide and other menus, even when a new program starts.
- **Start over:** hold Left for 2 seconds during an episode. It starts from the beginning on its own channel, and the TV returns when it ends. A short Left still jumps to the last channel. The temporary channel stays out of the channel editor and last-channel history; channel 999 is reserved for it.
- **Empty channels stay hidden** until you have at least 4 hours of content for them. Extra channels are not listed at all until they qualify.
- **Locked 48-hour schedule.** The guide never lies, and library changes never yank you out of a movie.
- **Read-only.** Nothing you watch is reported back to Plex. Your Continue Watching stays clean.
- **Updates and sign-out:** database upgrades preserve server/library choices using explicit migrations, never a silent database reset. Missing server configuration returns to setup. Sign out clears Plex credentials and the local library/lineup while keeping display preferences.

## Install on a Fire TV

Requirements:
- JDK 17 or newer (Gradle 9 will not run on JDK 11)
- Android SDK (platform 37)
- `adb` on PATH

1. On the TV, unlock Developer Options: Settings → My Fire TV → About → click the device name 7 times.
2. Turn on **ADB Debugging** in Developer Options.
3. Note the TV's IP address, under About → Network.
4. On your PC:

```powershell
.\scripts\install.ps1 -TvIp 192.168.1.50
```

The first build creates a private release key (`rerun-release.jks` and `keystore.properties`). Both stay on this machine and out of git. The script asks for a password, or generates a random one when it cannot prompt. Back those two files up. An install signed with a different key will not update the app, and saved Plex tokens will not carry over.

5. On the TV, the app shows a code. Enter it at **plex.tv/link**, pick your server, select the libraries to include, and continue to the first sync. Your choices are saved for future syncs; newly added libraries are not included automatically. Music libraries are only synced when Music is enabled in Settings. Existing installations without a saved selection continue syncing all supported libraries.

To remove it: `.\scripts\restore.ps1 -TvIp 192.168.1.50`

## Remote

| Button | Watching | Guide |
|---|---|---|
| ▲ / ▼ | Channel up / down | Move between channels |
| ◀ | Last channel | Earlier |
| ▶ / OK | Info banner | Later / tune in |
| Hold OK 2s | Cycle CRT look (Off / Subtle / Full) | — |
| ☰ | Guide | Edit the focused channel |
| Back | Guide | Back to TV. Press Back again right away to exit. |

Channels and Settings sit at the top of the guide: press ▲ from the first row.

## Commercials (optional)

`tools/commercials/fetch_archive_item.py` downloads every video in an Internet Archive item and queues each one for a local upscaler. Upscaled clips go into a Plex library named **Commercials**. Choose that library as the ad source during setup or in Settings. Rerun plays clips that fit between shows, seeking to the current ad position when you tune into a break. Clip choices are stable for a given channel, break, and catalog; consecutive repeats are avoided when another clip fits. Ads never appear as guide entries: each program's displayed time includes its following break, ending when the next program starts. Disabled ads, empty catalogs, and remaining gaps too short for a clip show an "Up Next" card. Times with no schedule say Off Air.

Vintage commercials are generally still under copyright. Keep downloaded copies for personal viewing, and never commit media to this repository.

## Development

- `core/` is pure Kotlin: catalog, routing, scheduler, tune math, and Plex parsing. It has unit tests: `./gradlew :core:test`.
- `app/` is the Android shell: Compose UI, Media3 player, and Room. JVM regression tests run with `.\gradlew.bat :app:testDebugUnitTest` (Robolectric). `./gradlew :app:assembleDebug` needs no signing key. A release build needs the key created by `scripts/keystore.ps1`.
- `tools/ktest/` runs the core tests with only a Kotlin compiler jar, for machines without Maven access.

Design spec and plans live in `docs/superpowers/`.

## Credits

VT323 font © The VT323 Project Authors, SIL Open Font License 1.1 (`licenses/VT323-OFL.txt`). Not affiliated with Plex or any studio named here. The names on the guide are parodies.
