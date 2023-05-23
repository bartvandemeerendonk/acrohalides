package app.Client.Utils;

public class TimeUtils {
    private static long time = 0;

    public static long getMillis() {
//        return System.currentTimeMillis();
        return time;
    }

    public static void incrementTime() {
        time += 1000;
    }
}
