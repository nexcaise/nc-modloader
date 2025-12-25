package id.my.nexcaise.ncmodloader;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;
import org.levimc.launcher.settings.FeatureSettings;

public class NCModloader {
    private static ModManager lm;

    public static void onLoad(Context ctx) {
        lm = ModManager.get(ctx);
        if(!FeatureSettings.getInstance().isNCMEnabled) {
            Logger.get().i("Version Isolation Is Disabled!, ncmodloader won't work!");
            return;
        }
        clearCache(ctx);
        ClearCustomPacks(ctx);
        copyAllLibs(ctx);
        loadAllLibs(ctx);
    }

    public static void clearCache(Context ctx) {
        File dir = ctx.getDir("cache/ncmodloader", Context.MODE_PRIVATE);
        Logger.get().info("Clearing Cache...");
        Utils.deleteFolder(dir.getAbsolutePath());
        Logger.get().info("Clearing Cache Done!");
    }

    public static void onUnload(Context ctx) {
        ClearCustomPacks(ctx);
        Logger.get().i("onUnload::Main");
    }

    private static void ClearCustomPacks(Context ctx) {
        File externalDir = ctx.getExternalFilesDir(null);
        File resourceDir = new File(externalDir, "resource_packs");
        File globalPacksFile = new File(externalDir, "games/com.mojang/minecraftpe/global_resource_packs.json");

        if (resourceDir.exists()) {
            File[] subs = resourceDir.listFiles();
            if (subs != null) {
                for (File f : subs) {
                    if (f.isDirectory() && f.getName().startsWith("ncmodloader_")) {
                        Utils.deleteFolder(f.getAbsolutePath());
                    }
                }
            }
        }
        resetGlobalPacks(globalPacksFile);
    }

    private static void resetGlobalPacks(File globalPacksFile) {
        if (!globalPacksFile.exists()) {
            Logger.get().i("Global resource pack file not found, nothing to clean.");
            return;
        }

        try (InputStream in = new FileInputStream(globalPacksFile)) {
            String content = readFully(in).trim();
            if (content.isEmpty() || content.equals("[]")) {
                Logger.get().i("Global resource pack list already empty.");
                return;
            }

            JSONArray packs = new JSONArray(content);
            JSONArray newArray = new JSONArray();

            for (int i = 0; i < packs.length(); i++) {
                JSONObject pack = packs.getJSONObject(i);
                if (!pack.has("#mod")) {
                    newArray.put(pack);
                }
            }

            try (FileWriter writer = new FileWriter(globalPacksFile)) {
                writer.write(newArray.toString(2));
            }

            Logger.get().i("🧹 Removed all ncmodloader-related global resource packs (" + (packs.length() - newArray.length()) + " entries cleaned).");
        } catch (Exception e) {
            Logger.get().error("Failed to clean ncmodloader global packs: " + e);
        }
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = in.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString("UTF-8");
    }

    public static void copyAllLibs(Context ctx) {
        try {
            File dir = ctx.getDir("", Context.MODE_PRIVATE);
            File internalLibs = new File(dir, "cache/ncmodloader/mods");
            File externalLibs = new File("/storage/emulated/0/games/NexCaise/ModLoader/mods");

            if (!externalLibs.exists() || !externalLibs.isDirectory()) {
                externalLibs.delete();
                externalLibs.mkdirs();
                Logger.get().info("Libs Folder Created!");
            }

            if (!internalLibs.exists() || !internalLibs.isDirectory()) {
                internalLibs.delete();
                internalLibs.mkdirs();
                Logger.get().info("Internal Libs Folder Created!");
            } else {
                internalLibs.delete();
                internalLibs.mkdirs();
            }

            File[] mods = externalLibs.listFiles();
            if (mods == null) mods = new File[0];

            for (File mod : mods) {
                if (mod.getName().endsWith(".ncm")) {
                    File dest = new File(internalLibs, mod.getName() + ".jar");
                    Utils.copyFile(mod, dest);
                }
            }

            File configFile = new File(externalLibs, "ncmodloader_config.json");
            JSONArray configArray = new JSONArray();
            Map<String, JSONObject> oldMap = new HashMap<>();

            if (configFile.exists()) {
                try {
                    String oldJson = new String(Utils.readAllBytes(configFile));
                    JSONArray oldArr = new JSONArray(oldJson);
                    for (int i = 0; i < oldArr.length(); i++) {
                        JSONObject obj = oldArr.getJSONObject(i);
                        oldMap.put(obj.getString("name"), obj);
                    }
                } catch (Exception e) {
                    Logger.get().warn("Failed to read old ncmodloader_config.json, will recreate.");
                }
            }

            int order = 0;
            for (File mod : mods) {
                if (!mod.getName().endsWith(".ncm")) continue;
                String name = mod.getName();
                JSONObject existing = oldMap.get(name);
                JSONObject newObj = new JSONObject();
                if (existing != null) {
                    newObj.put("name", name);
                    newObj.put("enabled", existing.optBoolean("enabled", true));
                    newObj.put("order", existing.optInt("order", order));
                } else {
                    newObj.put("name", name);
                    newObj.put("enabled", true);
                    newObj.put("order", order);
                }
                configArray.put(newObj);
                order++;
            }

            FileWriter writer = new FileWriter(configFile, false);
            writer.write(configArray.toString(4));
            writer.close();
            Logger.get().info("ncmodloader_config.json updated (" + configArray.length() + " entries)");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadAllLibs(Context ctx) {
        try {
            File configFile = new File("/storage/emulated/0/games/NexCaise/ModLoader/mods/ncmodloader_config.json");
            List<ModConfig> configs = new ArrayList<>();

            if (configFile.exists()) {
                String jsonText = new String(Utils.readAllBytes(configFile));
                JSONArray array = new JSONArray(jsonText);

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    configs.add(new ModConfig(
                        obj.getString("name"),
                        obj.optBoolean("enabled", true),
                        obj.optInt("order", 0)
                    ));
                }

                Collections.sort(configs, new Comparator<ModConfig>() {
                    public int compare(ModConfig a, ModConfig b) {
                        return Integer.compare(a.order, b.order);
                    }
                });

                for (ModConfig c : configs) {
                    if (!c.enabled) continue;
                    File internalLib = new File(ctx.getDir("cache/ncmodloader", Context.MODE_PRIVATE), "mods/" + c.name + ".jar");
                    if (internalLib.exists()) {
                        Logger.get().info("Loading -> " + c.name);
                        lm.loadLib(internalLib);
                        Logger.get().info("Loaded -> " + c.name + " Done!");
                    } else {
                        Logger.get().warn("Skipped -> " + c.name + " (not found)");
                    }
                }
            } else {
                Logger.get().warn("ncmodloader_config.json not found! Loading all .modplus files instead.");
                File libsDir = new File(ctx.getDir("cache/ncmodloader", Context.MODE_PRIVATE), "mods");
                File[] jars = libsDir.listFiles();
                if (jars != null) {
                    for (File jar : jars) {
                        if (!jar.getName().endsWith(".ncm.jar")) continue;
                        Logger.get().info("Loading -> " + jar.getName());
                        ModManager.get(ctx).loadLib(jar);
                        Logger.get().info("Loaded -> " + jar.getName() + " Done!");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class ModConfig {
        public String name;
        public boolean enabled;
        public int order;

        public ModConfig(String name, boolean enabled, int order) {
            this.name = name;
            this.enabled = enabled;
            this.order = order;
        }
    }
}
