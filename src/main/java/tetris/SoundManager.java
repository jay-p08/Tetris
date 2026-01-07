package tetris;

import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javafx.scene.media.AudioClip;
import java.net.URL;

public class SoundManager {
    private boolean soundEnabled = true;
    private final Map<String, Clip> clipCache = new HashMap<>();
    private final Map<String, AudioClip> voiceCache = new HashMap<>();

    public SoundManager() {
        // Pre-cache common sounds
        preCache(400, 50); // Move
        preCache(600, 50); // Rotate
        preCache(500, 100); // Lock
        preCache(300, 150); // Hard Drop
        preCache(700, 150); // Single
        preCache(1000, 300); // Tetris
    }

    private void preCache(int frequency, int durationMs) {
        String key = frequency + "_" + durationMs;
        try {
            int sampleRate = 22050;
            int samples = (durationMs * sampleRate) / 1000;
            byte[] buf = new byte[samples];
            for (int i = 0; i < buf.length; i++) {
                double angle = i / (double) sampleRate * frequency * 2.0 * Math.PI;
                double volume = 1.0;
                if (i < 100)
                    volume = i / 100.0;
                if (i > samples - 500)
                    volume = Math.max(0, (samples - i) / 500.0);
                buf[i] = (byte) (Math.sin(angle) * 127.0 * volume);
            }
            AudioFormat af = new AudioFormat((float) sampleRate, 8, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(af, buf, 0, buf.length);
            clipCache.put(key, clip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playTone(int frequency, int durationMs) {
        if (!soundEnabled)
            return;
        String key = frequency + "_" + durationMs;
        Clip clip = clipCache.get(key);
        if (clip == null) {
            preCache(frequency, durationMs);
            clip = clipCache.get(key);
        }
        if (clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    public void playMove() {
        playTone(400, 50);
    }

    public void playRotate() {
        playTone(600, 50);
    }

    public void playLock() {
        playTone(500, 100);
    }

    public void playSingle() {
        playTone(700, 150);
    }

    public void playDouble() {
        playTone(800, 200);
    }

    public void playTriple() {
        playTone(900, 250);
    }

    public void playTetris() {
        playTone(1000, 300);
    }

    public void playTSpin() {
        playTone(1100, 250);
    }

    public void playHardDrop() {
        playTone(300, 150);
    }

    public void playHold() {
        playTone(550, 100);
    }

    public void playGameOver() {
        playTone(200, 500);
    }

    public void playAttack() {
        playTone(850, 200);
    }

    public void playGarbageReceived() {
        playTone(250, 200);
    }

    public void playCombo(int combo) {
        if (combo < 1)
            return;
        // Scale frequency from 400Hz to 1200Hz based on combo (max 30)
        int freq = 400 + (Math.min(combo, 30) * 20);
        playTone(freq, 100 + (combo * 5));
    }

    public void playVoice(String type) {
        if (!soundEnabled)
            return;
        AudioClip clip = voiceCache.get(type);
        if (clip == null) {
            try {
                // Try wav first, then mp3
                String[] exts = { ".wav", ".mp3" };
                for (String ext : exts) {
                    URL resource = getClass().getResource("/sounds/" + type + ext);
                    if (resource != null) {
                        clip = new AudioClip(resource.toExternalForm());
                        voiceCache.put(type, clip);
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not load voice: " + type);
            }
        }
        if (clip != null) {
            clip.play();
        }
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }
}
