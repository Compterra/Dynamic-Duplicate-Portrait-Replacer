# Dynamic Duplicate Portrait Replacer Changelog

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
