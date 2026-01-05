package tetris;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    static final int TILE = 30;
    static final int WIDTH = 10;
    static final int HEIGHT = 20;

    int blockX = 4;
    int blockY = 0;
    int score = 0;

    int[][] board = new int[HEIGHT][WIDTH];
    int[][] block; // 현재 블록을 저장할 변수 추가

    int[][][] TETROMINOS = {
        // I
        {
            {1, 1, 1, 1}
        },
        // O
        {
            {1, 1},
            {1, 1}
        },
        // T
        {
            {0, 1, 0},
            {1, 1, 1}
        },
        // S
        {
            {0, 1, 1},
            {1, 1, 0}
        },
        // Z
        {
            {1, 1, 0},
            {0, 1, 1}
        },
        // J
        {
            {1, 0, 0},
            {1, 1, 1}
        },
        // L
        {
            {0, 0, 1},
            {1, 1, 1}
        }
    };

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH * TILE, HEIGHT * TILE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // 첫 블록 생성
        spawnBlock();
        Timeline gameLoop = new Timeline();
        
        gameLoop.getKeyFrames().add(
                new KeyFrame(Duration.millis(500), e -> {
                    if (canMove(blockX, blockY + 1)) {
                        blockY++;
                    } else {
                        fixBlock();
                        clearLines();
                        spawnBlock();
                        
                        // 게임 오버 체크
                        if (!canMove(blockX, blockY)) {
                            gameLoop.stop();
                            System.out.println("Game Over! Final Score: " + score);
                        }
                    }
                    draw(gc);
                })
        );
        gameLoop.setCycleCount(Timeline.INDEFINITE);
        gameLoop.play();

        draw(gc);

        Pane root = new Pane(canvas);
        root.setFocusTraversable(true);
        Scene scene = new Scene(root);

        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case LEFT:
                    if (canMove(blockX - 1, blockY)) {
                        blockX--;
                    }
                    break;
                case RIGHT:
                    if (canMove(blockX + 1, blockY)) {
                        blockX++;
                    }
                    break;
                case DOWN:
                    if (canMove(blockX, blockY + 1)) {
                        blockY++;
                    }
                    break;
                case UP:
                    int[][] rotated = rotate(block);
                    if (canRotate(rotated)) {
                        block = rotated;
                    }
                    break;
            }
            draw(gc);
        });

        stage.setScene(scene);
        stage.setTitle("Java Tetris - Score: " + score);
        stage.show();

        stage.setOnShown(e -> root.requestFocus());
    }

    void draw(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH * TILE, HEIGHT * TILE);

        // 고정된 블록
        gc.setFill(Color.GRAY);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == 1) {
                    gc.fillRect(x * TILE, y * TILE, TILE, TILE);
                    gc.setStroke(Color.DARKGRAY);
                    gc.strokeRect(x * TILE, y * TILE, TILE, TILE);
                }
            }
        }

        // 현재 블록 그리기
        gc.setFill(Color.CYAN);
        for (int y = 0; y < block.length; y++) {
            for (int x = 0; x < block[0].length; x++) {
                if (block[y][x] == 1) {
                    gc.fillRect((blockX + x) * TILE, (blockY + y) * TILE, TILE, TILE);
                    gc.setStroke(Color.BLUE);
                    gc.strokeRect((blockX + x) * TILE, (blockY + y) * TILE, TILE, TILE);
                }
            }
        }
    }

    boolean canMove(int nextX, int nextY) {
        for (int y = 0; y < block.length; y++) {
            for (int x = 0; x < block[0].length; x++) {
                if (block[y][x] == 0) {
                    continue;
                }

                int bx = nextX + x;
                int by = nextY + y;

                if (bx < 0 || bx >= WIDTH || by >= HEIGHT) {
                    return false;
                }
                if (by >= 0 && board[by][bx] == 1) {
                    return false;
                }
            }
        }
        return true;
    }

    int[][] rotate(int[][] src) {
        int h = src.length;
        int w = src[0].length;
        int[][] dst = new int[w][h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst[x][h - 1 - y] = src[y][x];
            }
        }
        return dst;
    }

    boolean canRotate(int[][] rotated) {
        for (int y = 0; y < rotated.length; y++) {
            for (int x = 0; x < rotated[0].length; x++) {
                if (rotated[y][x] == 0) {
                    continue;
                }

                int bx = blockX + x;
                int by = blockY + y;

                if (bx < 0 || bx >= WIDTH || by < 0 || by >= HEIGHT) {
                    return false;
                }
                if (board[by][bx] == 1) {
                    return false;
                }
            }
        }
        return true;
    }

    void fixBlock() {
        System.out.println("=== fixBlock() called at blockX=" + blockX + ", blockY=" + blockY + " ===");
        for (int y = 0; y < block.length; y++) {
            for (int x = 0; x < block[0].length; x++) {
                if (block[y][x] == 1) {
                    int boardY = blockY + y;
                    int boardX = blockX + x;
                    // 범위 체크
                    if (boardY >= 0 && boardY < HEIGHT && boardX >= 0 && boardX < WIDTH) {
                        board[boardY][boardX] = 1;
                        System.out.println("Fixed block at (" + boardX + ", " + boardY + ")");
                    }
                }
            }
        }
        
        // 디버깅: 보드 맨 아래 3줄 출력
        System.out.println("Bottom 3 lines of board:");
        for (int y = HEIGHT - 3; y < HEIGHT; y++) {
            System.out.print("Line " + y + ": ");
            for (int x = 0; x < WIDTH; x++) {
                System.out.print(board[y][x] + " ");
            }
            System.out.println();
        }
    }

    int[][] copyBlock(int[][] src) {
        int[][] dst = new int[src.length][src[0].length];
        for (int y = 0; y < src.length; y++) {
            System.arraycopy(src[y], 0, dst[y], 0, src[0].length);
        }
        return dst;
    }

    void spawnBlock() {
        int idx = (int) (Math.random() * TETROMINOS.length);
        block = copyBlock(TETROMINOS[idx]);
        blockX = WIDTH / 2 - block[0].length / 2;
        blockY = 0;
    }

    void clearLines() {
        int cleared = 0;
        int y = HEIGHT - 1;

        while (y >= 0) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == 0) {
                    full = false;
                    break;
                }
            }

            if (full) {
                System.out.println("=== Line " + y + " is FULL! Clearing... ===");
                // 디버깅: 해당 줄 출력
                System.out.print("Line " + y + ": ");
                for (int x = 0; x < WIDTH; x++) {
                    System.out.print(board[y][x] + " ");
                }
                System.out.println();
                
                removeLine(y);
                cleared++;
                // y는 그대로 유지 (아래로 내려온 줄을 다시 검사하기 위해)
            } else {
                y--; // 줄이 꽉 차지 않았을 때만 다음 줄로 이동
            }
        }

        // 점수 계산
        if (cleared > 0) {
            score += cleared * 100;
            System.out.println("=== Total lines cleared: " + cleared + ", Score: " + score + " ===");
        }
    }

    void removeLine(int line) {
        // 해당 라인 위의 모든 줄을 한 칸씩 아래로
        for (int y = line; y > 0; y--) {
            for (int x = 0; x < WIDTH; x++) {
                board[y][x] = board[y - 1][x];
            }
        }
        // 맨 위 줄은 비움
        for (int x = 0; x < WIDTH; x++) {
            board[0][x] = 0;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
