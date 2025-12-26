package id.my.nexcaise.ncmodloader;

import android.os.Build;
import org.jetbrains.annotations.NotNull;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;

public class Utils {

    public static void deleteFolder(String path) {
        File f = new File(path);
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File c : files) deleteFolder(c.getAbsolutePath());
            }
        }
        boolean deleted = f.delete();
        //Logger.get().i("Folder " + (deleted ? "successfully deleted" : "failed to delete") + ": " + f.getAbsolutePath());
    }

    public static Object getPathList(@NotNull ClassLoader loader) throws ReflectiveOperationException {
        Field field = Objects.requireNonNull(loader.getClass().getSuperclass()).getDeclaredField("pathList");
        field.setAccessible(true);
        return field.get(loader);
  }

  public static void injectNativeLibraries(String nld, Object pathList) throws ReflectiveOperationException {
        try {
            final File newLibDir = new File(nld);

            Field nativeLibraryDirectoriesField = pathList.getClass().getDeclaredField("nativeLibraryDirectories");
            nativeLibraryDirectoriesField.setAccessible(true);

            Collection<File> currentDirs = (Collection<File>) nativeLibraryDirectoriesField.get(pathList);
            if (currentDirs == null) {
                currentDirs = new ArrayList<>();
            }

            List<File> libDirs = new ArrayList<>(currentDirs);

            Iterator<File> it = libDirs.iterator();
            while (it.hasNext()) {
                File libDir = it.next();
                if (newLibDir.equals(libDir)) {
                    it.remove();
                    break;
                }
            }
            libDirs.add(0, newLibDir);
            nativeLibraryDirectoriesField.set(pathList, libDirs);

            Field nativeLibraryPathElementsField = pathList.getClass().getDeclaredField("nativeLibraryPathElements");
            nativeLibraryPathElementsField.setAccessible(true);

            Object[] elements;

            if (Build.VERSION.SDK_INT >= 25) {
                Method makePathElements = pathList.getClass().getDeclaredMethod("makePathElements", List.class);
                makePathElements.setAccessible(true);

                Field systemNativeLibDirsField = pathList.getClass().getDeclaredField("systemNativeLibraryDirectories");
                systemNativeLibDirsField.setAccessible(true);
                List<File> systemLibDirs = (List<File>) systemNativeLibDirsField.get(pathList);
                if (systemLibDirs != null) {
                    libDirs.addAll(systemLibDirs);
                }

                elements = (Object[]) makePathElements.invoke(pathList, libDirs);
            } else {
                Method makePathElements = pathList.getClass().getDeclaredMethod("makePathElements", List.class, File.class, List.class);
                makePathElements.setAccessible(true);

                Field systemNativeLibDirsField = pathList.getClass().getDeclaredField("systemNativeLibraryDirectories");
                systemNativeLibDirsField.setAccessible(true);
                List<File> systemLibDirs = (List<File>) systemNativeLibDirsField.get(pathList);
                if (systemLibDirs != null) {
                    libDirs.addAll(systemLibDirs);
                }
                ArrayList<Throwable> suppressedExceptions = new ArrayList<>();
                elements = (Object[]) makePathElements.invoke(pathList, libDirs, null, suppressedExceptions);
            }
            nativeLibraryPathElementsField.set(pathList, elements);


        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new ReflectiveOperationException("Unable to inject native libraries", e);
        }
    }

    public static void copyFileFromJar(String jar, String src, File dst) throws IOException {
        try (JarFile j = new JarFile(jar)) {
            JarEntry entry = j.getJarEntry(src);
            if (entry == null) return;
            try (InputStream in = j.getInputStream(entry);
                 OutputStream out = new FileOutputStream(dst)) {
                copyStream(in, out);
            }
        }
    }

    public static void copyFolderFromJar(String jar, String src, File dst) throws IOException {
        try (JarFile j = new JarFile(jar)) {
            Enumeration<JarEntry> e = j.entries();
            while (e.hasMoreElements()) {
                JarEntry en = e.nextElement();
                if (!en.getName().startsWith(src + "/")) continue;
                if (en.isDirectory()) continue;
                File outFile = new File(dst, en.getName().substring(src.length() + 1));
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (InputStream in = j.getInputStream(en);
                     OutputStream out = new FileOutputStream(outFile)) {
                    copyStream(in, out);
                }
            }
        }
    }

    public static void copyFile(File src, File dst) throws IOException {
        if (!dst.getParentFile().exists()) dst.getParentFile().mkdirs();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        }
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }

    public static byte[] readAllBytes(File file) throws IOException {
    try (FileInputStream fis = new FileInputStream(file);
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[4096];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
    }
}
