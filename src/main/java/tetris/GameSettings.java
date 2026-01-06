package tetris;

public class GameSettings {
    // Delayed Auto Shift (ms): Delay before auto-repeat starts
    // User requested slower than 150ms? Or adjustable. Let's start with a relaxed
    // default.
    public static long DAS = 800;

    // Auto Repeat Rate (ms): Speed of movement when holding
    // User requested slower than 40ms. Let's try 60ms.
    public static long ARR = 100;

    // Soft Drop Multiplier: 1.0 (Normal) to 20.0 (Fast) or Infinity
    public static double SOFT_DROP_SPEED = 20.0; // 20x speed default
}
