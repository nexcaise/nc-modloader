package id.my.nexcaise.ncmodloader;

import android.content.Context;
import android.content.res.AssetManager;
import dalvik.system.DexClassLoader;
import java.lang.reflect.Method;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.json.*;
import java.text.SimpleDateFormat;

public class ModManager {
private final Context context;
private final File cacheDir;

public ModManager(Context ctx) {  
    context = ctx;  
    File dir = ctx.getDir("", Context.MODE_PRIVATE);  
    cacheDir = new File(dir, "cache/ncmodloader");  
    if (!cacheDir.exists()) cacheDir.mkdirs();  
}  

public static ModManager get(Context ctx) {  
    return new ModManager(ctx);  
}  

public void loadLib(File jarFile) {  
    JSONObject manifest = readManifest(jarFile);  
    if (manifest == null) {  
        Logger.get().error("manifest.json not found!, skipped: " + jarFile.getName());  
        return;  
    }  

    boolean hasNative = manifest.optBoolean("native", false);
    boolean hasAssets = manifest.optBoolean("assets_override", false);
    boolean hasCustomPack = manifest.optBoolean("custom_pack", false);
    String mainClass = manifest.optString("main", null);
    if (mainClass == null) {  
        Logger.get().error("'main' not found in manifest.json, skipped: " + jarFile.getName());  
        return;  
    }  

    try {  
        if (hasAssets) extractToApk(jarFile);  
        if (hasCustomPack) copyCustomPack(jarFile);

        File nativeDir = null;  
        if (hasNative) {  
            nativeDir = new File(cacheDir, "natives/" + jarFile.getName().replace(".jar", ""));  
            if (!nativeDir.exists() && !nativeDir.mkdirs()) {  
                Logger.get().error("Failed to create native dir: " + nativeDir);  
                return;  
            }  
            Utils.copyFolderFromJar(jarFile.getAbsolutePath(), "lib", nativeDir);  
        }  

        DexClassLoader dcl = new DexClassLoader(  
            jarFile.getAbsolutePath(),  
            cacheDir.getAbsolutePath(),  
            null,  
            context.getClassLoader()  
        );  

        invokeMain(dcl, mainClass, nativeDir);  
    } catch (Exception e) {  
        Logger.get().error("Failed to load modplus: " + e);  
    }  
}  

private void invokeMain(DexClassLoader dcl, String className, File nativeDir) {  
    try {  
        if (nativeDir != null) {  
            Object pathList = Utils.getPathList(dcl);  
            Utils.injectNativeLibraries(nativeDir.getAbsolutePath(), pathList);  
        }  
        Class<?> clazz = dcl.loadClass(className);  
        clazz.getDeclaredMethod("onLoad", Context.class).invoke(null, context);  
        Logger.get().info("Loaded: " + className);  
    } catch (Exception e) {  
        Throwable real = (e instanceof java.lang.reflect.InvocationTargetException) ? e.getCause() : e;  
        Logger.get().error(getStackTraceAsString(real));  
    }  
}  

private JSONObject readManifest(File jarFile) {  
    try (JarFile jar = new JarFile(jarFile)) {  
        JarEntry entry = jar.getJarEntry("manifest.json");  
        if (entry == null) return null;  
        try (InputStream in = jar.getInputStream(entry)) {  
            String jsonText = readFully(in);  
            return new JSONObject(jsonText);  
        }  
    } catch (Exception e) {  
        Logger.get().error("Failed to read manifest: " + e);  
        return null;  
    }  
}  

private void extractToApk(File jarFile) throws IOException {
    if (!jarFile.exists())  
        throw new FileNotFoundException(".jar file not found: " + jarFile.getAbsolutePath());  

    String baseName = jarFile.getName().replace(".jar", "");  
    String cleanName = baseName.endsWith(".modplus") ? baseName.substring(0, baseName.length() - 8) : baseName;  

    File apkFile = new File(cacheDir, "assets/" + baseName + ".apk");  
    File tempDir = new File(cacheDir, "assets/" + baseName + "_temp");  

    if (tempDir.exists()) Utils.deleteFolder(tempDir.getAbsolutePath());  
    tempDir.mkdirs();  
    File assetsDir = new File(tempDir, "assets");  
    assetsDir.mkdirs();  

    boolean foundAssets = false;

    try (JarFile jar = new JarFile(jarFile)) {
        Enumeration<JarEntry> entries = jar.entries();  
        while (entries.hasMoreElements()) {  
            JarEntry entry = entries.nextElement();  
            String name = entry.getName();  

            if (!name.startsWith("assets/")) continue;  
            foundAssets = true;  

            File outFile = new File(tempDir, name);  
            if (entry.isDirectory()) {  
                outFile.mkdirs();  
                continue;  
            }  
            outFile.getParentFile().mkdirs();  
            try (InputStream is = jar.getInputStream(entry);  
                 OutputStream os = new FileOutputStream(outFile)) {  
                Utils.copyStream(is, os);  
            }
        }  
    }  

    if (!foundAssets) {  
        Logger.get().i("there are no assets in: " + jarFile.getName() + " but 'assets_override': true in the manifest");  
        Utils.deleteFolder(tempDir.getAbsolutePath());  
        return;  
    }  

    zipFolder(tempDir, apkFile);  
    Utils.deleteFolder(tempDir.getAbsolutePath());  
    addAssetOverride(context.getAssets(), apkFile.getAbsolutePath());  
}

private void copyCustomPack(File jarFile) throws IOException {
    if (!jarFile.exists())
        throw new FileNotFoundException(".jar file not found: " + jarFile.getAbsolutePath());

    String baseName = jarFile.getName().replace(".jar", "");
    String cleanName = baseName.endsWith(".modplus") ? baseName.substring(0, baseName.length() - 8) : baseName;

    boolean found = false;
    File externalDir = context.getExternalFilesDir(null);
    //File cdnDir = new File("/sdcard/games/org.levimc/minecraft/com.mojang.minecraftpe/cdn");
    File resourceDst = new File(externalDir, "resource_packs/ncmodloader_" + cleanName);
    //cdnDir.mkdirs();
    resourceDst.mkdirs();

    // Salin isi custom_pack/
    try (JarFile jar = new JarFile(jarFile)) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("custom_pack/")) continue;
            found = true;

            String relative = name.substring("custom_pack/".length());
            if (relative.isEmpty() || relative.equalsIgnoreCase("manifest.json")) continue;

            File outFile = new File(resourceDst, relative);
            if (entry.isDirectory()) {
                outFile.mkdirs();
                continue;
            }
            outFile.getParentFile().mkdirs();
            try (InputStream is = jar.getInputStream(entry);
                 OutputStream os = new FileOutputStream(outFile)) {
                Utils.copyStream(is, os);
            }
        }
    }

    if (!found) {
        Logger.get().i("No custom_pack found in: " + jarFile.getName());
        return;
    }

    // Buat manifest.json untuk resource pack
    File manifestFile = new File(resourceDst, "manifest.json");
    String uuid1 = UUID.randomUUID().toString();
    String uuid2 = UUID.randomUUID().toString();

    String manifest = "{\n" +
        "  \"format_version\": 2,\n" +
        "  \"header\": {\n" +
        "    \"description\": \"This is the Resource Pack of the mod " + cleanName + "\",\n" +
        "    \"name\": \"ModPlusPack: " + cleanName + "\",\n" +
        "    \"uuid\": \"" + uuid1 + "\",\n" +
        "    \"version\": [1, 0, 0],\n" +
        "    \"min_engine_version\": [1, 21, 120]\n" +
        "  },\n" +
        "  \"modules\": [\n" +
        "    {\n" +
        "      \"type\": \"resources\",\n" +
        "      \"uuid\": \"" + uuid2 + "\",\n" +
        "      \"version\": [1, 0, 0]\n" +
        "    }\n" +
        "  ]\n" +
        "}";
    try (FileWriter writer = new FileWriter(manifestFile)) {
        writer.write(manifest);
    }

    // === Update global_resource_packs.json ===
    File globalPacksFile = new File(externalDir, "games/com.mojang/minecraftpe/global_resource_packs.json");
    JSONArray packsArray;

    if (!globalPacksFile.exists()) {
      File parent = globalPacksFile.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      try (FileWriter w = new FileWriter(globalPacksFile)) {
        w.write("[]");
      }
    }
    
    if (globalPacksFile.exists()) {
        try (InputStream in = new FileInputStream(globalPacksFile)) {
            String content = readFully(in).trim();
            packsArray = content.isEmpty() ? new JSONArray() : new JSONArray(content);
        } catch (Exception e) {
            packsArray = new JSONArray();
        }
    } else {
        packsArray = new JSONArray();
    }

    // Tambahkan entry baru ke atas (hindari duplikat pack_id)
    JSONArray newArray = new JSONArray();
    JSONObject newPack = new JSONObject();
    newPack.put("pack_id", uuid1);
    newPack.put("version", new JSONArray("[1,0,0]"));
    newPack.put("#mod", cleanName);
    newArray.put(newPack);

    for (int i = 0; i < packsArray.length(); i++) {
        JSONObject existing = packsArray.getJSONObject(i);
        if (!existing.optString("pack_id").equals(uuid1)) {
            newArray.put(existing);
        }
    }

    try (FileWriter writer = new FileWriter(globalPacksFile)) {
        writer.write(newArray.toString(2));
    }

    Logger.get().i("✅ Global resource pack registered: " + uuid1);
    Logger.get().i("✅ Custom pack path: " + resourceDst.getAbsolutePath());
}
 
private void zipFolder(File source, File zipFile) throws IOException {  
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {  
        zipFileRecursive(source, source, zos);  
    }  
}  

private void zipFileRecursive(File rootDir, File source, ZipOutputStream zos) throws IOException {  
    File[] files = source.listFiles();  
    if (files == null) return;  
    for (File file : files) {  
        String entryName = rootDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");  
        if (file.isDirectory()) {  
            if (!entryName.endsWith("/")) entryName += "/";  
            zos.putNextEntry(new ZipEntry(entryName));  
            zos.closeEntry();  
            zipFileRecursive(rootDir, file, zos);  
        } else {  
            try (InputStream fis = new FileInputStream(file)) {  
                zos.putNextEntry(new ZipEntry(entryName));  
                Utils.copyStream(fis, zos);  
                zos.closeEntry();  
            }  
        }  
    }  
}  

private String getStackTraceAsString(Throwable t) {  
    StringWriter sw = new StringWriter();  
    t.printStackTrace(new PrintWriter(sw));  
    return sw.toString();  
}  

private void addAssetOverride(AssetManager mgr, String packagePath) {  
    try {  
        Method m = AssetManager.class.getMethod("addAssetPath", String.class);  
        m.invoke(mgr, packagePath);  
    } catch (Throwable t) {  
        t.printStackTrace();  
    }  
}  

private String readFully(InputStream in) throws IOException {  
    ByteArrayOutputStream baos = new ByteArrayOutputStream();  
    byte[] buffer = new byte[4096];  
    int len;  
    while ((len = in.read(buffer)) != -1) {  
        baos.write(buffer, 0, len);  
    }  
    return baos.toString("UTF-8");  
}

                                            }
