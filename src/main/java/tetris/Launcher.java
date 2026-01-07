package tetris;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Launcher {
    public static void main(String[] args) {
        try {
            Main.main(args);
        } catch (Throwable t) {
            try (PrintWriter out = new PrintWriter(new FileWriter("crash.log", true))) {
                out.println("--- CRASH LOG ---");
                out.println("Time: " + new java.util.Date());
                t.printStackTrace(out);
                out.println("------------------\n");
            } catch (Exception e) {
                e.printStackTrace();
            }

            String message = "Application failed to start!\n\n" +
                    "Error: " + t.toString() + "\n\n" +
                    "A crash.log file has been created with details.";

            try {
                javax.swing.JOptionPane.showMessageDialog(null,
                        message,
                        "Startup Error",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) {
            }

            System.err.println("\n--- STARTUP ERROR ---");
            t.printStackTrace();
            System.err.println("\nCheck crash.log for details.");
            System.err.println("Press ENTER to exit...");

            try {
                new Scanner(System.in).nextLine();
            } catch (Exception e) {
            }

            System.exit(1);
        }
    }
}
