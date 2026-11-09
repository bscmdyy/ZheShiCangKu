## tlmformation 9.3 — 2026-11-09

### Added
- Swap deployment: deploying a new squad while another is still on the field no longer blocks; the old squad boards the same helicopter and flies home.
- Hard rescue: forced cross-dimension retrieval on respawn and dimension change, plus a periodic reconcile check every 5 seconds for teleports that fire no event.
- Squad force-loading: 3x3 chunks around every deployed squad member stay loaded while deployed (configurable).
- Own helicopter audio: registered rotor loop and engine-start sounds for scripted airdrop flights; player-flown helicopters keep the original SuperbWarfare engine sound.
- Radio SFX on F9 deploy / withdrawal clicks.
- Bilingual UI: every screen, HUD element, item name, tooltip, command feedback and maid voice line is available in Chinese and English.

### Fixed
- Tactical car (HAVOC) now uses the correct texture; the leftover Humvee test entity has been removed.
- Maids in unloaded chunks or other dimensions now show their real name in the F9 panel instead of `?`.
- Rappelling maids no longer teleport straight to the ground.
- Helicopter no longer bobs vertically while the ropes are deployed.
- Extraction chat no longer fires while summoning a squad.

### Requirements
Minecraft 1.20.1 · Forge 47.x · Touhou Little Maid 1.5.3 · GeckoLib 4.8.4 · SuperbWarfare 0.8.9.2 · SimpleBedrockModel 2.5.1+ · Java 17

Client and server must run the same tlmformation version.

### License
GPL-3.0. Corresponding source is available in the auto-generated "Source code (zip)" asset below.
