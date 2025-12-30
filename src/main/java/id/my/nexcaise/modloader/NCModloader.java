package id.my.nexcaise.ncmodloader;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;
import android.app.Activity;
import org.levimc.launcher.settings.FeatureSettings;

public class NCModloader {
    private static ModManager lm;
    private static Activity activity;
    
    public static Activity getActivity() {
        return activity;
    }

    public static void onLoad(Context ctx) {
        Activity act = (Activity) context;
        activity = act;
        
        if(!FeatureSettings.getInstance().isNCMEnabled()) return;
        lm = ModManager.get(ctx);
        clearCache(ctx);
        copyAllLibs(ctx);
        loadAllLibs(ctx);
        
    }

    public static void clearCache(Context ctx) {
        File dir = ctx.getDir("ncmodloader", Context.MODE_PRIVATE);
        Logger.get().info("Clearing Cache...");
        Utils.deleteFolder(dir.getAbsolutePath());
        Logger.get().info("Clearing Cache Done!");
    }

    public static void copyAllLibs(Context ctx) {
        try {
            File dir = ctx.getDir("ncmodloader", Context.MODE_PRIVATE);
            File internalLibs = new File(dir, "mods");
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
                    File internalLib = new File(ctx.getDir("ncmodloader", Context.MODE_PRIVATE), "mods/" + c.name + ".jar");
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
                File libsDir = new File(ctx.getDir("ncmodloader", Context.MODE_PRIVATE), "mods");
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
