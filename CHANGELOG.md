# Dynamic Duplicate Portrait Replacer Changelog

## 0.2.9

- Scans other NPC fleets in the current location when an interaction dialog opens, so multi-fleet comm directories can clean every listed contact instead of only the fleet being hailed.
- Keeps the scan event-driven and skips the player fleet to avoid unnecessary background work or surprise player-officer changes.

## 0.2.8

- Checks ship captains assigned directly to fleet members when the player opens a fleet interaction.
- Fixes mod and special fleets whose visible captains were not registered in the fleet officer list.

## 0.2.7

- Adds four Valkyrian-style female portraits to the mercenary replacement pool.
- Registers the new portraits for both Dynamic Portraits replacement and player new-game portrait selection.

## 0.2.6

- Fixes Starsector script-sandbox compatibility by loading portrait pools from registered JSON data instead of scanning the filesystem.
- Reads LunaLib settings through the normal LunaLib API instead of blocked reflection.

## 0.2.5

- Replaces the paused polling script with an event-driven campaign listener.
- Cleans portraits when the player opens an interaction dialog, comm screen target, or market, instead of scanning repeatedly while menus are open.

## 0.2.4

- Reduces background scan cost by processing one current-location fleet and market slice per tick instead of sweeping the whole location.
- Prioritizes the active interaction target so open market and patrol dialogs are still cleaned promptly while paused.

## 0.2.3

- Simplifies mod-faction cleanup: non-blacklisted unmapped factions are cleaned directly instead of using vanilla first-duplicate preservation.
- Loosens portrait eligibility for mod faction portrait paths outside the normal `graphics/portraits` layout.
- Fixes LunaLib numeric setting reads when values are returned as a generic number type.

## 0.2.2

- Runs duplicate cleanup while paused so newly opened comm directories and interaction screens can be cleaned up.
- Prioritizes markets in the player's current location before falling back to the broader market scan.
- Shortens the scan interval and logs the first few replacements to make faction-specific cleanup easier to verify.

## 0.2.1

- Adds a versioned cleanup pass so existing saves can revisit duplicate portraits that older settings already marked as processed.
- Scans market administrators, market people, and comm-directory entries in addition to fleets.
- Prevents already-assigned Dynamic Portraits images from being re-rolled by later cleanup passes.

## 0.1.9

- Strengthens default duplicate cleanup for mod factions.
- Counts duplicates across all portrait paths by default and replaces generic/unmapped duplicate portraits at 100%.
- Refreshes the LunaLib setting IDs for the stronger defaults so old cached values do not keep the previous conservative behavior.

## 0.1.8

- Allows unmapped mod factions to participate by default, relying on the recommended blacklist for faction-specific protection.

## 0.1.7

- Adds bundled portraits to the player faction portrait lists so they appear in the new-game portrait picker.

## 0.1.6

- Registers all bundled portrait images so they can be selected by the player during new-game character creation.

## 0.1.5

- Adds LunaLib text rows showing the faction IDs included in the recommended blacklist.

## 0.1.4

- Reworks the LunaLib blacklist controls so long default values no longer overflow the settings panel.
- Adds a recommended-blacklist toggle and a short editable field for additional faction IDs.

## 0.1.3

- Adds LunaLib help text explaining how to edit the faction blacklist.

## 0.1.2

- Fixes the LunaLib faction blacklist setting so it is editable in-game.

## 0.1.1

- Expands the default faction blacklist for mod factions with distinctive portrait sets.
- Adds Hiver Swarm, HMI Supervillains Fang Society, Emergent Threats, and Emergent Threats IX Revival faction IDs to the default protection list.

## 0.1.0

- Initial release.
- Adds dynamic duplicate portrait replacement for generated officers.
- Preserves vanilla and mod portraits by default unless duplicate/replacement rules allow a swap.
- Supports faction/gender portrait pools.
- Adds LunaLib settings for duplicate behavior, replacement chances, faction blacklist, and unmapped faction protection.
- Adds Version Checker support.
