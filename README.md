# Dynamic Duplicate Portrait Replacer

A Starsector utility mod that reduces repeated generated officer portraits without taking over every portrait in a modded game.

By default, the mod preserves the first use of a vanilla-style portrait and only considers later duplicates for replacement. It also protects unmapped factions and blacklists UAF and Avali/AEF factions by default, since those mods ship with strong portrait identities of their own.

## Features

- Dynamic officer portrait replacement from organized portrait pools.
- Duplicate-first behavior so vanilla portraits are preserved until repeated.
- Faction and gender-aware portrait pools.
- Optional LunaLib settings menu.
- Recommended faction blacklist for protecting mod factions, plus LunaLib additions for local overrides.
- Version Checker support.

## Configuration

LunaLib users can configure the main behavior in-game. Without LunaLib, settings are available in:

`data/config/dynamic_portraits/settings.json`

The LunaLib menu keeps the recommended blacklist as a toggle and provides a short field for extra faction IDs. Edit `settings.json` directly if you want to change the built-in recommended blacklist itself.

To add support for another faction, add its faction ID to `factionRoles` and create a matching portrait role folder under:

`graphics/portraits`
