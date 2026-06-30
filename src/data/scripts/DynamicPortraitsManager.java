package data.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class DynamicPortraitsManager implements EveryFrameScript {
    private static final Logger LOG = Global.getLogger(DynamicPortraitsManager.class);

    private static final String MOD_ID = "dynamic_portraits";
    private static final String ASSIGNED_KEY = "$dynamic_portraits_assigned";
    private static final String USAGE_KEY_PREFIX = "$dynamic_portraits_use_";
    private static final String ORIGINAL_USAGE_KEY_PREFIX = "$dynamic_portraits_original_use_";
    private static final String GENERIC_ROLE = "generic";
    private static final float SCAN_INTERVAL_SECONDS = 5f;
    private static final float DEFAULT_REPLACEMENT_CHANCE = 0.35f;

    private final PortraitPools portraitPools;
    private final DynamicPortraitsSettings settings;
    private final Random random = new Random();
    private float elapsed = SCAN_INTERVAL_SECONDS;
    private int roundRobinLocationIndex = 0;

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
        return false;
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

        scanFleet(sector.getPlayerFleet());
        scanLocation(sector.getCurrentLocation());
        scanNextLocation(sector);
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
    }

    private void scanNextLocation(SectorAPI sector) {
        List<LocationAPI> locations = sector.getAllLocations();
        if (locations == null || locations.isEmpty()) {
            return;
        }

        if (roundRobinLocationIndex >= locations.size()) {
            roundRobinLocationIndex = 0;
        }

        scanLocation(locations.get(roundRobinLocationIndex));
        roundRobinLocationIndex++;
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

    private void assignPortrait(PersonAPI person) {
        if (!canAssignPortrait(person)) {
            return;
        }

        if (settings.isFactionBlacklisted(person)) {
            person.getMemoryWithoutUpdate().set(ASSIGNED_KEY, true);
            return;
        }

        if (settings.isUnmappedFactionProtected(person)) {
            person.getMemoryWithoutUpdate().set(ASSIGNED_KEY, true);
            return;
        }

        if (!isEligibleDuplicate(person)) {
            person.getMemoryWithoutUpdate().set(ASSIGNED_KEY, true);
            return;
        }

        String role = settings.getPortraitRole(person);
        if (random.nextFloat() >= settings.getReplacementChance(role)) {
            person.getMemoryWithoutUpdate().set(ASSIGNED_KEY, true);
            return;
        }

        String portrait = portraitPools.pick(role, person.getGender(), random);
        if (portrait == null) {
            return;
        }

        person.setPortraitSprite(portrait);
        person.getMemoryWithoutUpdate().set(ASSIGNED_KEY, true);
        incrementUsage(portrait);
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

        String key = ORIGINAL_USAGE_KEY_PREFIX + Integer.toHexString(portrait.hashCode());
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
        if (person.getMemoryWithoutUpdate().getBoolean(ASSIGNED_KEY)) {
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
        return normalized.contains("/portraits/");
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
            DynamicPortraitsSettings settings = new DynamicPortraitsSettings(true, true, "vanillaTopLevel", DEFAULT_REPLACEMENT_CHANCE);
            try {
                JSONObject json = Global.getSettings().loadJSON("data/config/dynamic_portraits/settings.json", MOD_ID);
                settings = new DynamicPortraitsSettings(
                        json.optBoolean("onlyReplaceDuplicateVanillaPortraits", true),
                        json.optBoolean("protectUnmappedFactions", true),
                        json.optString("duplicateSourceMode", "vanillaTopLevel"),
                        (float) json.optDouble("defaultReplacementChance", DEFAULT_REPLACEMENT_CHANCE)
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

            FactionAPI faction = person.getFaction();
            if (faction == null || faction.getId() == null) {
                return false;
            }
            return !factionRoles.containsKey(faction.getId().toLowerCase(Locale.ROOT));
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
            factionRoles.put("hegemony", "hegemony");
            factionRoles.put("lions_guard", "lions_guard");
            factionRoles.put("luddic_church", "luddic");
            factionRoles.put("luddic_path", "luddic");
            factionRoles.put("knights_of_ludd", "luddic");
            factionRoles.put("persean", "persean_league");
            factionRoles.put("persean_league", "persean_league");
            factionRoles.put("pirates", "pirate");
            factionRoles.put("sindrian_diktat", "sindrian_diktat");
            factionRoles.put("tritachyon", "tritachyon");
            factionRoles.put("mercenary", "mercenary");
            factionRoles.put("independent", "mercenary");
            factionRoles.put("scavengers", "mercenary");
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

                String sourceMode = getLunaString("dp_duplicate_source_mode");
                if (sourceMode != null && sourceMode.trim().length() > 0) {
                    duplicateSourceMode = sourceMode.trim();
                }

                Float defaultChance = getLunaFloat("dp_default_replacement_chance");
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

                applyLunaRoleChance("generic", "dp_role_generic");
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
            return value instanceof Float ? (Float) value : null;
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
