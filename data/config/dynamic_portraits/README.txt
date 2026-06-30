onlyReplaceDuplicateVanillaPortraits controls the default philosophy of the mod.
When true, the first eligible use of a vanilla portrait is preserved, and only later duplicate uses become eligible for replacement.

If LunaLib is enabled, the in-game LunaLib menu overrides the matching values in settings.json.
The factionRoles map remains JSON-only so modded faction support can stay explicit and readable.

protectUnmappedFactions leaves factions not listed in factionRoles unchanged.
This is disabled by default so ordinary mod factions can participate. Use the blacklist for factions that should keep their own portraits.

blacklistedFactionIds is a list of faction ids Dynamic Portraits should never alter.
This is best for protecting mod factions that already ship with their own portrait set.
LunaLib keeps this recommended list behind a toggle, then adds any extra faction IDs from its short additional blacklist field.

duplicateSourceMode controls what counts as the duplicate source.
vanillaTopLevel means only portraits directly under graphics/portraits are counted, which is how vanilla human portraits are normally referenced.
allPortraits means any eligible portrait path can be counted for duplicate cleanup.

defaultReplacementChance controls how often an eligible officer gets a Dynamic Portraits image when their role does not have a specific chance.

Officers that do not pass the roll are still marked as processed, so their original portrait remains and they are not rerolled later.

roleReplacementChances lets each portrait folder have its own replacement chance.
For example, pirates can use custom portraits more often while Hegemony can remain closer to vanilla.

factionRoles maps Starsector faction ids to portrait folders.
To add modded faction support, add a folder under graphics/portraits, then add that faction id here.

Use 1.0 to replace every eligible officer for that role.
Use 0.0 to only mark officers without changing portraits for that role.
