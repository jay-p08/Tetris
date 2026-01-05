package tetris;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Main extends Application {

    static final int TILE = 30;
    static final int WIDTH = 10;
    static final int HEIGHT = 20;

    int blockX = 4;
    int blockY = 0;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH * TILE, HEIGHT * TILE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(500); } catch (Exception ignored) {}
                blockY++;
                draw(gc);
            }
        }).start();

        draw(gc);

        Pane root = new Pane(canvas);
        stage.setScene(new Scene(root));
        stage.setTitle("Java Tetris");
        stage.show();
    }

    void draw(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH * TILE, HEIGHT * TILE);

        gc.setFill(Color.CYAN);
        gc.fillRect(blockX * TILE, blockY * TILE, TILE, TILE);
    }

    public static void main(String[] args) {
        launch();
    }
}
