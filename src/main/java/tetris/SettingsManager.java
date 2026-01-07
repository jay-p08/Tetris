package tetris;

import java.io.*;
import java.util.Properties;

public class SettingsManager {
    private static final String SETTINGS_FILE = "settings.properties";
    private static SettingsManager instance;
    private Properties properties;

    // Member variables to hold settings values
    private long das = 150;
    private long arr = 30;
    private double sdf = 20.0;
    private boolean ghostEnabled = true;
    private boolean soundEnabled = true;
    private int boardWidth = 10;
    private int boardHeight = 20;

    private SettingsManager() {
        properties = new Properties();
        load();
    }

    public static SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    private void load() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                properties.load(input);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Load values from properties into member variables, using defaults if not
        // present
        das = Long.parseLong(properties.getProperty("das", String.valueOf(this.das)));
        arr = Long.parseLong(properties.getProperty("arr", String.valueOf(this.arr)));
        sdf = Double.parseDouble(properties.getProperty("sdf", String.valueOf(this.sdf)));
        ghostEnabled = Boolean.parseBoolean(properties.getProperty("ghost", String.valueOf(this.ghostEnabled)));
        soundEnabled = Boolean.parseBoolean(properties.getProperty("sound", String.valueOf(this.soundEnabled)));
        boardWidth = Integer.parseInt(properties.getProperty("boardWidth", String.valueOf(this.boardWidth)));
        boardHeight = Integer.parseInt(properties.getProperty("boardHeight", String.valueOf(this.boardHeight)));
    }

    public void save() {
        // Update properties object from current member variables before saving
        properties.setProperty("das", String.valueOf(this.das));
        properties.setProperty("arr", String.valueOf(this.arr));
        properties.setProperty("sdf", String.valueOf(this.sdf));
        properties.setProperty("ghost", String.valueOf(this.ghostEnabled));
        properties.setProperty("sound", String.valueOf(this.soundEnabled));
        properties.setProperty("boardWidth", String.valueOf(this.boardWidth));
        properties.setProperty("boardHeight", String.valueOf(this.boardHeight));

        try (OutputStream output = new FileOutputStream(SETTINGS_FILE)) {
            properties.store(output, "Tetris User Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public long getDas() {
        return das;
    }

    public void setDas(long das) {
        this.das = das;
    }

    public long getArr() {
        return arr;
    }

    public void setArr(long arr) {
        this.arr = arr;
    }

    public double getSdf() {
        return sdf;
    }

    public void setSdf(double sdf) {
        this.sdf = sdf;
    }

    public boolean isGhostEnabled() {
        return ghostEnabled;
    }

    public void setGhostEnabled(boolean enabled) {
        this.ghostEnabled = enabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public int getBoardWidth() {
        return boardWidth;
    }

    public void setBoardWidth(int boardWidth) {
        this.boardWidth = boardWidth;
    }

    public int getBoardHeight() {
        return boardHeight;
    }

    public void setBoardHeight(int boardHeight) {
        this.boardHeight = boardHeight;
    }
}
