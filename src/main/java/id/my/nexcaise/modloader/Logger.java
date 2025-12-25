package id.my.nexcaise.ncmodloader;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Logger {

    public enum Level {
        VERBOSE(1), DEBUG(2), INFO(3), WARN(4), ERROR(5), NONE(6);
        final int value;
        Level(int v){ this.value = v; }
    }

    private static final Logger INSTANCE = new Logger();

    // default tag when user doesn't supply tag
    private String tagPrefix = "NCModloader";
    private Level minLevel = Level.VERBOSE;

    // Android reflection helpers
    private final boolean hasAndroidLog;
    private final Class<?> androidLogClass;
    private final Method androidV, androidD, androidI, androidW, androidE;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    private Logger() {
        Class<?> logClass = null;
        Method vMethod = null, dMethod = null, iMethod = null, wMethod = null, eMethod = null;
        boolean found = false;
        try {
            logClass = Class.forName("android.util.Log");
            // methods: Log.v(String tag, String msg); Log.v(String tag, String msg, Throwable tr) etc.
            vMethod = logClass.getMethod("v", String.class, String.class);
            dMethod = logClass.getMethod("d", String.class, String.class);
            iMethod = logClass.getMethod("i", String.class, String.class);
            wMethod = logClass.getMethod("w", String.class, String.class);
            eMethod = logClass.getMethod("e", String.class, String.class);
            found = true;
        } catch (Throwable ignored) {
            // not running on Android or reflection failed
        }
        hasAndroidLog = found;
        androidLogClass = logClass;
        androidV = vMethod;
        androidD = dMethod;
        androidI = iMethod;
        androidW = wMethod;
        androidE = eMethod;
    }

    public static Logger get(){ return INSTANCE; }

    // Configuration
    public Logger setTagPrefix(String prefix){
        if (prefix != null) this.tagPrefix = prefix;
        return this;
    }

    public Logger setMinLevel(Level level){
        if (level != null) this.minLevel = level;
        return this;
    }

    public String getTagPrefix(){ return tagPrefix; }
    public Level getMinLevel(){ return minLevel; }

    // Short aliases + full names
    public void v(String msg){ log(Level.VERBOSE, null, msg, null); }
    public void verbose(String msg){ v(msg); }

    public void d(String msg){ log(Level.DEBUG, null, msg, null); }
    public void debug(String msg){ d(msg); }

    public void i(String msg){ log(Level.INFO, null, msg, null); }
    public void info(String msg){ i(msg); }

    public void w(String msg){ log(Level.WARN, null, msg, null); }
    public void warn(String msg){ w(msg); }

    public void e(String msg){ log(Level.ERROR, null, msg, null); }
    public void error(String msg){ e(msg); }

    // With Throwable
    public void e(String msg, Throwable t){ log(Level.ERROR, null, msg, t); }
    public void w(String msg, Throwable t){ log(Level.WARN, null, msg, t); }

    // Internal logging method
    private void log(Level level, String tag, String message, Throwable t){
        if (level.value < minLevel.value) return;

        String finalTag = (tag == null || tag.isEmpty()) ? tagPrefix : (tagPrefix + "-" + tag);
        String formattedMsg = formatMessage(level, message, t);

        if (hasAndroidLog) {
            try {
                Method m = selectAndroidMethod(level);
                if (m != null) {
                    m.invoke(androidLogClass, finalTag, formattedMsg);
                    if (t != null) {
                        // Try to call method with throwable if exists: e(String, String, Throwable)
                        try {
                            Method m3 = androidLogClass.getMethod(levelToMethodName(level), String.class, String.class, Throwable.class);
                            m3.invoke(androidLogClass, finalTag, formattedMsg, t);
                        } catch (Throwable ignored) {
                            // ignore; we've already logged the message
                        }
                    }
                    return;
                }
            } catch (Throwable ignored) {
                // fall back to console output
            }
        }

        // Fallback: console output
        printToConsole(level, finalTag, formattedMsg, t);
    }

    private Method selectAndroidMethod(Level level){
        switch (level){
            case VERBOSE: return androidV;
            case DEBUG: return androidD;
            case INFO: return androidI;
            case WARN: return androidW;
            case ERROR: return androidE;
            default: return androidI;
        }
    }

    private String levelToMethodName(Level level){
        switch (level){
            case VERBOSE: return "v";
            case DEBUG: return "d";
            case INFO: return "i";
            case WARN: return "w";
            case ERROR: return "e";
            default: return "i";
        }
    }

    private String formatMessage(Level level, String msg, Throwable t){
        String time = sdf.format(new Date());
        String base = String.format(Locale.getDefault(), "%s %s: %s", time, level.name(), msg == null ? "null" : msg);
        if (t != null && (hasAndroidLog == false)) {
            // on non-Android, append stacktrace to message (console)
            StringBuilder sb = new StringBuilder(base);
            sb.append('\n');
            sb.append(formatThrowable(t));
            return sb.toString();
        }
        return base;
    }

    private void printToConsole(Level level, String tag, String msg, Throwable t){
        String output = String.format(Locale.getDefault(), "[%s] %s: %s", tag, level.name(), msg);
        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
        if (t != null && hasAndroidLog == false) {
            t.printStackTrace(System.err);
        }
    }

    private String formatThrowable(Throwable t){
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append('\n');
        for (StackTraceElement el : t.getStackTrace()){
            sb.append("\tat ").append(el.toString()).append('\n');
        }
        if (t.getCause() != null) {
            sb.append("Caused by: ").append(formatThrowable(t.getCause()));
        }
        return sb.toString();
    }

    // Optional: convenience to log with a custom tag (if you ever need it)
    public void i(String tag, String msg){ log(Level.INFO, tag, msg, null); }
    public void d(String tag, String msg){ log(Level.DEBUG, tag, msg, null); }
    public void e(String tag, String msg, Throwable t){ log(Level.ERROR, tag, msg, t); }

}
