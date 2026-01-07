package tetris;

public class GameSettings {
    private static final SettingsManager sm = SettingsManager.getInstance();

    public static long getDas() {
        return sm.getDas();
    }

    public static long getArr() {
        return sm.getArr();
    }

    public static double getSdf() {
        return sm.getSdf();
    }

    public static boolean isGhostEnabled() {
        return sm.isGhostEnabled();
    }

    public static boolean isSoundEnabled() {
        return sm.isSoundEnabled();
    }

    // Constants for backward compatibility or direct access if needed
    public static long DAS = sm.getDas();
    public static long ARR = sm.getArr();
    public static double SOFT_DROP_SPEED = sm.getSdf();

    public static void refresh() {
        DAS = sm.getDas();
        ARR = sm.getArr();
        SOFT_DROP_SPEED = sm.getSdf();
    }
}
