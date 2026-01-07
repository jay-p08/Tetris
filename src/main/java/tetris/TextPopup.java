package tetris;

import javafx.scene.paint.Color;

/**
 * Represents an animated text popup that appears on screen and fades out.
 */
public class TextPopup {
    public String text;
    public double x;
    public double y;
    public Color color;
    public long createdTime;
    public int durationMs;
    public boolean isActive;

    public TextPopup(String text, double x, double y, Color color, int durationMs) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
        this.durationMs = durationMs;
        this.createdTime = System.currentTimeMillis();
        this.isActive = true;
    }

    /**
     * Updates the popup state and returns the current opacity (0.0 to 1.0)
     */
    public double getOpacity() {
        long elapsed = System.currentTimeMillis() - createdTime;
        if (elapsed >= durationMs) {
            isActive = false;
            return 0.0;
        }
        // Fade out over time
        return 1.0 - ((double) elapsed / durationMs);
    }

    /**
     * Returns the current Y position (moves upward over time)
     */
    public double getCurrentY() {
        long elapsed = System.currentTimeMillis() - createdTime;
        double progress = (double) elapsed / durationMs;
        return y - (progress * 50); // Move up 50 pixels over duration
    }
}
