package data.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DynamicPortraitsManager implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(DynamicPortraitsManager.class);

    private static final String MOD_ID = "dynamic_portraits";
    private static final String ASSIGNED_KEY = "$dynamic_portraits_assigned";
    private static final String ASSIGNED_VERSION_KEY = "$dynamic_portraits_assigned_version";
    private static final String USAGE_KEY_PREFIX = "$dynamic_portraits_use_";
    private static final String ORIGINAL_USAGE_KEY_PREFIX = "$dynamic_portraits_original_use_";
    private static final int CURRENT_CLEANUP_VERSION = 3;
    private static final String GENERIC_ROLE = "generic";
    private static final float SCAN_INTERVAL_SECONDS = 1f;
    private static final int MAX_REPLACEMENT_LOGS = 50;
    private static final float DEFAULT_REPLACEMENT_CHANCE = 0.35f;

    private final PortraitPools portraitPools;
    private final DynamicPortraitsSettings settings;
    private final Random random = new Random();
    private float elapsed = SCAN_INTERVAL_SECONDS;
    private int roundRobinLocationIndex = 0;
    private int roundRobinFleetIndex = 0;
    private int roundRobinMarketIndex = 0;
    private int roundRobinCurrentMarketIndex = 0;
    private int replacementLogs = 0;

    public DynamicPortraitsManager() {
        portraitPools = PortraitPools.load();
        settings = DynamicPortraitsSettings.load();
    }

    public static void install() {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            return;
        }

        if (!sector.hasTransientScript(DynamicPortraitsManager.class)) {
            sector.addTransientScript(new DynamicPortraitsManager());
            LOG.info("Dynamic Portraits campaign script installed");
        }

        new DynamicPortraitsManager().scanAllLocations();
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
        if (elapsed < SCAN_INTERVAL_SECONDS) {
            return;
        }
        elapsed = 0f;

        SectorAPI sector = Global.getSector();
        if (sector == null || portraitPools.isEmpty()) {
            return;
        }

        scanInteractionTarget(sector);
        scanFleet(sector.getPlayerFleet());
        scanNextFleetInLocation(sector.getCurrentLocation());
        scanNextMarketInLocation(sector, sector.getCurrentLocation());
        scanNextLocation(sector);
        scanNextMarket(sector);
    }

    private void scanAllLocations() {
        SectorAPI sector = Global.getSector();
        if (sector == null || portraitPools.isEmpty()) {
            return;
        }

        scanFleet(sector.getPlayerFleet());
        for (LocationAPI location : sector.getAllLocations()) {
            scanLocation(location);
        }
        scanAllMarkets(sector);
    }

    private void scanNextLocation(SectorAPI sector) {
        List<LocationAPI> locations = sector.getAllLocations();
        if (locations == null || locations.isEmpty()) {
            return;
        }

        if (roundRobinLocationIndex >= locations.size()) {
            roundRobinLocationIndex = 0;
        }

        scanNextFleetInLocation(locations.get(roundRobinLocationIndex));
        roundRobinLocationIndex++;
    }

    private void scanAllMarkets(SectorAPI sector) {
        if (sector == null || sector.getEconomy() == null) {
            return;
        }

        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        if (markets == null) {
            return;
        }

        for (MarketAPI market : markets) {
            scanMarket(market);
        }
    }

    private void scanNextMarketInLocation(SectorAPI sector, LocationAPI location) {
        if (sector == null || sector.getEconomy() == null || location == null) {
            return;
        }

        List<MarketAPI> markets = sector.getEconomy().getMarkets(location);
        if (markets == null || markets.isEmpty()) {
            return;
        }

        if (roundRobinCurrentMarketIndex >= markets.size()) {
            roundRobinCurrentMarketIndex = 0;
        }

        scanMarket(markets.get(roundRobinCurrentMarketIndex));
        roundRobinCurrentMarketIndex++;
    }

    private void scanNextMarket(SectorAPI sector) {
        if (sector == null || sector.getEconomy() == null) {
            return;
        }

        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        if (markets == null || markets.isEmpty()) {
            return;
        }

        if (roundRobinMarketIndex >= markets.size()) {
            roundRobinMarketIndex = 0;
        }

        scanMarket(markets.get(roundRobinMarketIndex));
        roundRobinMarketIndex++;
    }

    private void scanLocation(LocationAPI location) {
        if (location == null) {
            return;
        }

        List<CampaignFleetAPI> fleets = location.getFleets();
        if (fleets == null) {
            return;
        }

        for (CampaignFleetAPI fleet : fleets) {
            scanFleet(fleet);
        }
    }

    private void scanNextFleetInLocation(LocationAPI location) {
        if (location == null) {
            return;
        }

        List<CampaignFleetAPI> fleets = location.getFleets();
        if (fleets == null || fleets.isEmpty()) {
            return;
        }

        if (roundRobinFleetIndex >= fleets.size()) {
            roundRobinFleetIndex = 0;
        }

        scanFleet(fleets.get(roundRobinFleetIndex));
        roundRobinFleetIndex++;
    }

    private void scanInteractionTarget(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        CampaignUIAPI ui = sector.getCampaignUI();
        if (ui == null || !ui.isShowingDialog()) {
            return;
        }

        InteractionDialogAPI dialog = ui.getCurrentInteractionDialog();
        if (dialog == null) {
            return;
        }

        scanEntity(dialog.getInteractionTarget());
    }

    private void scanEntity(SectorEntityToken entity) {
        if (entity == null) {
            return;
        }

        if (entity instanceof CampaignFleetAPI) {
            scanFleet((CampaignFleetAPI) entity);
        }

        assignPortrait(entity.getActivePerson());

        MarketAPI market = entity.getMarket();
        if (market != null) {
            scanMarket(market);
        }
    }

    private void scanFleet(CampaignFleetAPI fleet) {
        if (fleet == null || fleet.getFleetData() == null) {
            return;
        }

        for (OfficerDataAPI officerData : fleet.getFleetData().getOfficersCopy()) {
            if (officerData != null) {
                assignPortrait(officerData.getPerson());
            }
        }

        assignPortrait(fleet.getCommander());
    }

    private void scanMarket(MarketAPI market) {
        if (market == null) {
            return;
        }

        assignPortrait(market.getAdmin());

        List<PersonAPI> people = market.getPeopleCopy();
        if (people != null) {
            for (PersonAPI person : people) {
                assignPortrait(person);
            }
        }

        CommDirectoryAPI directory = market.getCommDirectory();
        if (directory == null) {
            return;
        }

        List<CommDirectoryEntryAPI> entries = directory.getEntriesCopy();
        if (entries == null) {
            return;
        }

        for (CommDirectoryEntryAPI entry : entries) {
            if (entry != null && entry.getEntryData() instanceof PersonAPI) {
                assignPortrait((PersonAPI) entry.getEntryData());
            }
        }
    }

    private void assignPortrait(PersonAPI person) {
        if (!canAssignPortrait(person)) {
            return;
        }

        String normalizedPortrait = normalizePortraitPath(person.getPortraitSprite());
        if (portraitPools.contains(normalizedPortrait)) {
            markProcessed(person);
            return;
        }

        if (isCurrentCleanupComplete(person)) {
            return;
        }

        if (settings.isFactionBlacklisted(person)) {
            markProcessed(person);
            return;
        }

        boolean preservesFirstDuplicate = settings.preservesFirstDuplicate(person);
        if (settings.isUnmappedFactionProtected(person)) {
            markProcessed(person);
            return;
        }

        if (preservesFirstDuplicate && !isEligibleDuplicate(person)) {
            markProcessed(person);
            return;
        }

        String role = settings.getPortraitRole(person);
        if (random.nextFloat() >= settings.getReplacementChance(role)) {
            markProcessed(person);
            return;
        }

        String replacement = portraitPools.pick(role, person.getGender(), random);
        if (replacement == null) {
            return;
        }

        person.setPortraitSprite(replacement);
        markProcessed(person);
        incrementUsage(replacement);
        logReplacement(person, normalizedPortrait, replacement, role);
    }

    private void logReplacement(PersonAPI person, String originalPortrait, String replacementPortrait, String role) {
        if (replacementLogs >= MAX_REPLACEMENT_LOGS) {
            return;
        }

        FactionAPI faction = person.getFaction();
        String factionId = faction == null ? "unknown" : faction.getId();
        LOG.info("Dynamic Portraits replaced duplicate portrait for faction [" + factionId + "] role [" + role + "]: "
                + originalPortrait + " -> " + replacementPortrait);
        replacementLogs++;
    }

    private boolean isEligibleDuplicate(PersonAPI person) {
        if (!settings.onlyReplaceDuplicateVanillaPortraits) {
            return true;
        }

        String portrait = normalizePortraitPath(person.getPortraitSprite());
        if (portrait == null || portrait.length() <= 0) {
            return true;
        }
        if (!settings.isDuplicateSource(portrait)) {
            return false;
        }

        String key = ORIGINAL_USAGE_KEY_PREFIX + CURRENT_CLEANUP_VERSION + "_" + Integer.toHexString(portrait.hashCode());
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            return false;
        }

        int previousUses = sector.getMemoryWithoutUpdate().getInt(key);
        sector.getMemoryWithoutUpdate().set(key, previousUses + 1);
        return previousUses > 0;
    }

    private boolean canAssignPortrait(PersonAPI person) {
        if (person == null || person.isAICore() || person.isPlayer()) {
            return false;
        }

        String portrait = person.getPortraitSprite();
        if (portrait == null || portrait.trim().isEmpty()) {
            return true;
        }

        String normalized = normalizePortraitPath(portrait);
        if (normalized.contains("/characters/")) {
            return false;
        }
        return isImagePath(normalized);
    }

    private static boolean isImagePath(String path) {
        return path != null && (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg"));
    }

    private boolean isCurrentCleanupComplete(PersonAPI person) {
        return person.getMemoryWithoutUpdate().getInt(ASSIGNED_VERSION_KEY) >= CURRENT_CLEANUP_VERSION;
    }

    private void markProcessed(PersonAPI person) {
        person.getMemoryWithoutUpdate().set(ASSIGNED_KEY, true);
        person.getMemoryWithoutUpdate().set(ASSIGNED_VERSION_KEY, CURRENT_CLEANUP_VERSION);
    }

    private static String normalizePortraitPath(String portrait) {
        if (portrait == null) {
            return null;
        }
        return portrait.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private void incrementUsage(String portrait) {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            return;
        }

        String key = USAGE_KEY_PREFIX + Integer.toHexString(portrait.hashCode());
        int current = sector.getMemoryWithoutUpdate().getInt(key);
        sector.getMemoryWithoutUpdate().set(key, current + 1);
    }

    private static int getUsage(String portrait) {
        SectorAPI sector = Global.getSector();
        if (sector == null) {
            return 0;
        }
        return sector.getMemoryWithoutUpdate().getInt(USAGE_KEY_PREFIX + Integer.toHexString(portrait.hashCode()));
    }

    private static class DynamicPortraitsSettings {
        boolean onlyReplaceDuplicateVanillaPortraits;
        boolean protectUnmappedFactions;
        String duplicateSourceMode;
        float defaultReplacementChance;
        final Map<String, Float> roleReplacementChances = new HashMap<String, Float>();
        final Map<String, String> factionRoles = new HashMap<String, String>();
        final Set<String> duplicatePreservedFactionIds = new HashSet<String>();
        final List<String> blacklistedFactionIds = new ArrayList<String>();
        final List<String> recommendedBlacklistedFactionIds = new ArrayList<String>();

        private DynamicPortraitsSettings(boolean onlyReplaceDuplicateVanillaPortraits, boolean protectUnmappedFactions, String duplicateSourceMode, float defaultReplacementChance) {
            this.onlyReplaceDuplicateVanillaPortraits = onlyReplaceDuplicateVanillaPortraits;
            this.protectUnmappedFactions = protectUnmappedFactions;
            this.duplicateSourceMode = duplicateSourceMode == null ? "vanillaTopLevel" : duplicateSourceMode.trim();
            this.defaultReplacementChance = clamp(defaultReplacementChance, 0f, 1f);
            addDefaultFactionRoles();
        }

        static DynamicPortraitsSettings load() {
            DynamicPortraitsSettings settings = new DynamicPortraitsSettings(true, false, "allPortraits", 1f);
            try {
                JSONObject json = Global.getSettings().loadJSON("data/config/dynamic_portraits/settings.json", MOD_ID);
                settings = new DynamicPortraitsSettings(
                        json.optBoolean("onlyReplaceDuplicateVanillaPortraits", true),
                        json.optBoolean("protectUnmappedFactions", false),
                        json.optString("duplicateSourceMode", "allPortraits"),
                        (float) json.optDouble("defaultReplacementChance", 1f)
                );
                settings.loadBlacklistedFactionIds(json.optJSONArray("blacklistedFactionIds"));
                settings.rememberRecommendedBlacklistedFactionIds();
                settings.loadRoleReplacementChances(json.optJSONObject("roleReplacementChances"));
                settings.loadFactionRoles(json.optJSONObject("factionRoles"));
            } catch (Exception ex) {
                LOG.warn("Dynamic Portraits could not load settings; using defaults", ex);
            }
            settings.applyLunaSettingsIfAvailable();
            return settings;
        }

        boolean isFactionBlacklisted(PersonAPI person) {
            FactionAPI faction = person.getFaction();
            if (faction == null || faction.getId() == null) {
                return false;
            }
            return blacklistedFactionIds.contains(faction.getId().toLowerCase(Locale.ROOT));
        }

        boolean isUnmappedFactionProtected(PersonAPI person) {
            if (!protectUnmappedFactions) {
                return false;
            }

            return !isFactionMapped(person);
        }

        boolean isFactionMapped(PersonAPI person) {
            FactionAPI faction = person.getFaction();
            if (faction == null || faction.getId() == null) {
                return false;
            }
            return factionRoles.containsKey(faction.getId().toLowerCase(Locale.ROOT));
        }

        boolean preservesFirstDuplicate(PersonAPI person) {
            FactionAPI faction = person.getFaction();
            if (faction == null || faction.getId() == null) {
                return false;
            }
            return duplicatePreservedFactionIds.contains(faction.getId().toLowerCase(Locale.ROOT));
        }

        boolean isDuplicateSource(String portrait) {
            if ("allPortraits".equalsIgnoreCase(duplicateSourceMode)) {
                return portrait.contains("/portraits/");
            }
            return isVanillaTopLevelPortrait(portrait);
        }

        float getReplacementChance(String role) {
            Float chance = roleReplacementChances.get(PortraitPools.normalizeRole(role));
            if (chance != null) {
                return chance.floatValue();
            }
            return defaultReplacementChance;
        }

        String getPortraitRole(PersonAPI person) {
            FactionAPI faction = person.getFaction();
            if (faction == null || faction.getId() == null) {
                return GENERIC_ROLE;
            }

            String role = factionRoles.get(faction.getId().toLowerCase(Locale.ROOT));
            if (role == null) {
                return GENERIC_ROLE;
            }
            return role;
        }

        private static boolean isVanillaTopLevelPortrait(String portrait) {
            String marker = "graphics/portraits/";
            int index = portrait.indexOf(marker);
            if (index < 0) {
                return false;
            }

            String relative = portrait.substring(index + marker.length());
            if (relative.length() <= 0 || relative.contains("/")) {
                return false;
            }

            String lower = relative.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        }


        private void loadBlacklistedFactionIds(JSONArray json) throws JSONException {
            if (json == null) {
                return;
            }
            for (int i = 0; i < json.length(); i++) {
                addBlacklistedFactionIds(json.optString(i, ""));
            }
        }

        private void rememberRecommendedBlacklistedFactionIds() {
            recommendedBlacklistedFactionIds.clear();
            recommendedBlacklistedFactionIds.addAll(blacklistedFactionIds);
        }

        private void restoreRecommendedBlacklistedFactionIds() {
            blacklistedFactionIds.clear();
            for (String id : recommendedBlacklistedFactionIds) {
                addBlacklistedFactionIds(id);
            }
        }

        private void loadRoleReplacementChances(JSONObject json) throws JSONException {
            if (json == null) {
                return;
            }
            for (String role : toStringList(json.keys())) {
                roleReplacementChances.put(PortraitPools.normalizeRole(role), clamp((float) json.optDouble(role, defaultReplacementChance), 0f, 1f));
            }
        }

        private void loadFactionRoles(JSONObject json) throws JSONException {
            if (json == null) {
                return;
            }
            for (String factionId : toStringList(json.keys())) {
                String role = json.optString(factionId, GENERIC_ROLE);
                factionRoles.put(factionId.toLowerCase(Locale.ROOT), PortraitPools.normalizeRole(role));
            }
        }

        private void addDefaultFactionRoles() {
            addDefaultFactionRole("hegemony", "hegemony");
            addDefaultFactionRole("lions_guard", "lions_guard");
            addDefaultFactionRole("luddic_church", "luddic");
            addDefaultFactionRole("luddic_path", "luddic");
            addDefaultFactionRole("knights_of_ludd", "luddic");
            addDefaultFactionRole("persean", "persean_league");
            addDefaultFactionRole("persean_league", "persean_league");
            addDefaultFactionRole("pirates", "pirate");
            addDefaultFactionRole("sindrian_diktat", "sindrian_diktat");
            addDefaultFactionRole("tritachyon", "tritachyon");
            addDefaultFactionRole("mercenary", "mercenary");
            addDefaultFactionRole("independent", "mercenary");
            addDefaultFactionRole("scavengers", "mercenary");
        }

        private void addDefaultFactionRole(String factionId, String role) {
            String normalized = factionId.toLowerCase(Locale.ROOT);
            factionRoles.put(normalized, role);
            duplicatePreservedFactionIds.add(normalized);
        }

        private void applyLunaSettingsIfAvailable() {
            try {
                if (!Global.getSettings().getModManager().isModEnabled("lunalib")) {
                    return;
                }

                Boolean duplicateOnly = getLunaBoolean("dp_only_replace_duplicates");
                if (duplicateOnly != null) {
                    onlyReplaceDuplicateVanillaPortraits = duplicateOnly.booleanValue();
                }

                Boolean protectUnmapped = getLunaBoolean("dp_protect_unmapped_factions");
                if (protectUnmapped != null) {
                    protectUnmappedFactions = protectUnmapped.booleanValue();
                }

                String sourceMode = getLunaString("dp_duplicate_source_mode_v2");
                if (sourceMode != null && sourceMode.trim().length() > 0) {
                    duplicateSourceMode = sourceMode.trim();
                }

                Float defaultChance = getLunaFloat("dp_default_replacement_chance_v2");
                if (defaultChance != null) {
                    defaultReplacementChance = clamp(defaultChance.floatValue(), 0f, 1f);
                }

                Boolean useRecommendedBlacklist = getLunaBoolean("dp_use_recommended_blacklist");
                if (Boolean.FALSE.equals(useRecommendedBlacklist)) {
                    blacklistedFactionIds.clear();
                } else {
                    restoreRecommendedBlacklistedFactionIds();
                }

                String extraFactionBlacklist = getLunaString("dp_extra_blacklisted_factions");
                if (extraFactionBlacklist != null) {
                    addBlacklistedFactionIds(extraFactionBlacklist);
                }

                applyLunaRoleChance("generic", "dp_role_generic_v2");
                applyLunaRoleChance("hegemony", "dp_role_hegemony");
                applyLunaRoleChance("lions_guard", "dp_role_lions_guard");
                applyLunaRoleChance("luddic", "dp_role_luddic");
                applyLunaRoleChance("mercenary", "dp_role_mercenary");
                applyLunaRoleChance("persean_league", "dp_role_persean_league");
                applyLunaRoleChance("pirate", "dp_role_pirate");
                applyLunaRoleChance("sindrian_diktat", "dp_role_sindrian_diktat");
                applyLunaRoleChance("tritachyon", "dp_role_tritachyon");
            } catch (Exception ex) {
                LOG.warn("Dynamic Portraits could not read LunaLib settings; using JSON settings", ex);
            }
        }

        private void applyLunaRoleChance(String role, String lunaKey) throws Exception {
            Float chance = getLunaFloat(lunaKey);
            if (chance != null) {
                roleReplacementChances.put(PortraitPools.normalizeRole(role), clamp(chance.floatValue(), 0f, 1f));
            }
        }

        private static Boolean getLunaBoolean(String key) throws Exception {
            Object value = getLunaValue("getBoolean", key);
            return value instanceof Boolean ? (Boolean) value : null;
        }

        private static Float getLunaFloat(String key) throws Exception {
            Object value = getLunaValue("getFloat", key);
            return value instanceof Number ? Float.valueOf(((Number) value).floatValue()) : null;
        }

        private static String getLunaString(String key) throws Exception {
            Object value = getLunaValue("getString", key);
            return value instanceof String ? (String) value : null;
        }

        private static Object getLunaValue(String methodName, String key) throws Exception {
            Class<?> lunaSettings = Class.forName("lunalib.lunaSettings.LunaSettings");
            return lunaSettings.getMethod(methodName, String.class, String.class).invoke(null, MOD_ID, key);
        }

        private void addBlacklistedFactionIds(String rawIds) {
            if (rawIds == null) {
                return;
            }

            String[] ids = rawIds.split("[,;\\s]+");
            for (String id : ids) {
                String normalized = id.trim().toLowerCase(Locale.ROOT);
                if (normalized.length() <= 0 || blacklistedFactionIds.contains(normalized)) {
                    continue;
                }
                blacklistedFactionIds.add(normalized);
            }
        }

        private static float clamp(float value, float min, float max) {
            if (value < min) return min;
            if (value > max) return max;
            return value;
        }

        private static List<String> toStringList(java.util.Iterator<?> iterator) {
            List<String> result = new ArrayList<String>();
            while (iterator != null && iterator.hasNext()) {
                Object next = iterator.next();
                if (next != null) {
                    result.add(next.toString());
                }
            }
            return result;
        }
    }

    private static class PortraitPools {
        private final Map<String, Map<String, List<String>>> byRole = new LinkedHashMap<String, Map<String, List<String>>>();
        private final Set<String> normalizedPortraits = new HashSet<String>();

        static PortraitPools load() {
            PortraitPools pools = new PortraitPools();

            try {
                ModSpecAPI spec = Global.getSettings().getModManager().getModSpec(MOD_ID);
                if (spec == null) {
                    LOG.warn("Dynamic Portraits could not find its own mod spec");
                    return pools;
                }

                File root = new File(spec.getPath(), "graphics" + File.separator + "portraits");
                pools.loadFrom(root);
                LOG.info("Dynamic Portraits loaded " + pools.countPortraits() + " portraits in " + pools.byRole.size() + " role pools");
            } catch (Exception ex) {
                LOG.warn("Dynamic Portraits failed to load portrait pools", ex);
            }

            return pools;
        }

        boolean isEmpty() {
            return byRole.isEmpty();
        }

        boolean contains(String portrait) {
            return portrait != null && normalizedPortraits.contains(normalizePortraitPath(portrait));
        }

        String pick(String role, FullName.Gender gender, Random random) {
            List<String> candidates = new ArrayList<String>();
            addCandidates(candidates, role, gender);
            addCandidates(candidates, role, FullName.Gender.ANY);
            if (!GENERIC_ROLE.equals(role)) {
                addCandidates(candidates, GENERIC_ROLE, gender);
                addCandidates(candidates, GENERIC_ROLE, FullName.Gender.ANY);
            }

            if (candidates.isEmpty()) {
                return null;
            }

            int lowestUse = Integer.MAX_VALUE;
            List<String> leastUsed = new ArrayList<String>();
            for (String portrait : candidates) {
                int usage = getUsage(portrait);
                if (usage < lowestUse) {
                    lowestUse = usage;
                    leastUsed.clear();
                }
                if (usage == lowestUse) {
                    leastUsed.add(portrait);
                }
            }

            return leastUsed.get(random.nextInt(leastUsed.size()));
        }

        private void addCandidates(List<String> candidates, String role, FullName.Gender gender) {
            Map<String, List<String>> rolePools = byRole.get(normalizeRole(role));
            if (rolePools == null) {
                return;
            }

            List<String> portraits = rolePools.get(normalizeGender(gender));
            if (portraits != null) {
                candidates.addAll(portraits);
            }
        }

        private void loadFrom(File root) {
            if (root == null || !root.isDirectory()) {
                LOG.warn("Dynamic Portraits portrait root is missing: " + root);
                return;
            }

            File[] roleDirs = root.listFiles();
            if (roleDirs == null) {
                return;
            }

            for (File roleDir : roleDirs) {
                if (!roleDir.isDirectory()) {
                    continue;
                }
                loadRole(root, roleDir);
            }
        }

        private void loadRole(File root, File roleDir) {
            File[] genderDirs = roleDir.listFiles();
            if (genderDirs == null) {
                return;
            }

            String role = normalizeRole(roleDir.getName());
            for (File genderDir : genderDirs) {
                if (!genderDir.isDirectory()) {
                    continue;
                }

                String gender = normalizeGender(genderDir.getName());
                if (gender == null) {
                    continue;
                }

                File[] files = genderDir.listFiles();
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    if (!file.isFile() || !isImage(file.getName())) {
                        continue;
                    }
                    add(role, gender, toGamePath(root, file));
                }
            }
        }

        private void add(String role, String gender, String path) {
            Map<String, List<String>> rolePools = byRole.get(role);
            if (rolePools == null) {
                rolePools = new HashMap<String, List<String>>();
                byRole.put(role, rolePools);
            }

            List<String> portraits = rolePools.get(gender);
            if (portraits == null) {
                portraits = new ArrayList<String>();
                rolePools.put(gender, portraits);
            }
            portraits.add(path);
            normalizedPortraits.add(normalizePortraitPath(path));
        }

        private int countPortraits() {
            int count = 0;
            for (Map<String, List<String>> rolePools : byRole.values()) {
                for (List<String> portraits : rolePools.values()) {
                    count += portraits.size();
                }
            }
            return count;
        }

        private static boolean isImage(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        }

        private static String toGamePath(File portraitRoot, File image) {
            String relative = portraitRoot.toPath().relativize(image.toPath()).toString();
            return "graphics/portraits/" + relative.replace('\\', '/');
        }

        private static String normalizeRole(String role) {
            if (role == null) {
                return GENERIC_ROLE;
            }
            return role.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        }

        private static String normalizeGender(FullName.Gender gender) {
            if (gender == null) {
                return "any";
            }
            return normalizeGender(gender.name());
        }

        private static String normalizeGender(String gender) {
            if (gender == null) {
                return null;
            }

            String normalized = gender.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("male") || normalized.equals("female") || normalized.equals("any")) {
                return normalized;
            }
            return null;
        }
    }
}
