package tetris;

import java.io.IOException;
import java.util.Optional;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import tetris.net.NetworkClient;

public class Main extends Application {

    static final int TILE = 30;
    static final int WIDTH = 10;
    static final int HEIGHT = 24; // Total height (20 visible + 4 buffer)
    static final int VISIBLE_HEIGHT = 20;
    static final int BUFFER_ZONE = 4;

    static final int CANVAS_WIDTH = 800; // Wider for MP
    static final int CANVAS_HEIGHT = 800; // Increased to fit 24 rows + margins
    static final int BOARD_OFFSET_X = 50;
    // Board drawn starting from top (Buffer visible)
    static final int BOARD_OFFSET_Y = 50;
    static final int OPPONENT_OFFSET_X = 500;

    // Game Components
    Board board;
    Tetromino currentPiece;
    Tetromino nextPiece;
    Tetromino holdPiece;
    InputHandler input = new InputHandler();
    NetworkClient netClient;

    // Game State
    int score = 0;
    int level = 1;
    int linesCleared = 0;
    int combo = -1;
    boolean isGameOver = false;
    boolean backToBack = false;
    boolean holdUsed = false;
    boolean isMultiplayer = false;
    boolean gameStarted = false; // Wait for room start

    // Opponent State
    int[][] opponentBoard; // Minimal representation
    boolean opponentGameOver = false;

    // Timing
    long lastUpdate = 0;
    long lockTimer = 0;
    double dropInterval = 1000;
    boolean touchingGround = false;
    int lockDelayMs = 500;
    long lastArrUpdate = 0; // For ARR throttling

    // UI
    Stage primaryStage;
    Scene menuScene;
    Scene gameScene;
    Pane gameRoot;
    GraphicsContext gc;
    AnimationTimer gameLoop;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        createMenuScene();

        stage.setScene(menuScene);
        stage.setTitle("Tetris Multiplayer");
        stage.show();
    }

    void createMenuScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #222;");

        Label title = new Label("JAVA TETRIS");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        title.setTextFill(Color.CYAN);

        Button btnSingle = createStyledButton("Single Player");
        btnSingle.setOnAction(e -> startSinglePlayer());

        Button btnCreate = createStyledButton("Create Room");
        btnCreate.setOnAction(e -> createRoom());

        Button btnJoin = createStyledButton("Join Room");
        btnJoin.setOnAction(e -> joinRoomDialog());

        root.getChildren().addAll(title, btnSingle, btnCreate, btnJoin);
        menuScene = new Scene(root, 400, 400);
    }

    Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-size: 16px; -fx-min-width: 200px;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #666; -fx-text-fill: white; -fx-font-size: 16px; -fx-min-width: 200px;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: #444; -fx-text-fill: white; -fx-font-size: 16px; -fx-min-width: 200px;"));
        return btn;
    }

    void createGameScene() {
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        gameRoot = new Pane(canvas);
        gameScene = new Scene(gameRoot);

        gameScene.setOnKeyPressed(e -> {
            if (!gameStarted && isMultiplayer)
                return;
            long now = System.currentTimeMillis();
            KeyCode code = e.getCode();
            switch (code) {
                case LEFT -> {
                    if (!input.left) { // Initial Press
                        input.pressLeft(now);
                        move(-1, 0);
                    }
                }
                case RIGHT -> {
                    if (!input.right) { // Initial Press
                        input.pressRight(now);
                        move(1, 0);
                    }
                }
                case DOWN -> input.down = true;
                case UP, X -> rotate(true);
                case Z, CONTROL -> rotate(false);
                case D -> rotate(true);
                case A -> rotate(false);
                case SPACE -> hardDrop();
                case SHIFT, C -> hold();
            }
        });

        gameScene.setOnKeyReleased(e -> {
            KeyCode code = e.getCode();
            switch (code) {
                case LEFT -> input.releaseLeft();
                case RIGHT -> input.releaseRight();
                case DOWN -> input.down = false;
            }
        });

        primaryStage.setScene(gameScene);
        primaryStage.centerOnScreen();
        gameRoot.requestFocus();

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!gameStarted && isMultiplayer) {
                    drawWaiting();
                    return;
                }
                long milliNow = System.currentTimeMillis();
                update(milliNow);
                draw();
            }
        };
        gameLoop.start();
    }

    void startSinglePlayer() {
        isMultiplayer = false;
        gameStarted = true;
        initGame();
        createGameScene();
    }

    void connectToServer() {
        if (netClient == null) {
            netClient = new NetworkClient();
            netClient.onMessage = this::handleNetworkMessage;
            try {
                netClient.connect("localhost", 9999);
            } catch (IOException e) {
                System.out.println("Connection Failed");
            }
        }
    }

    void createRoom() {
        connectToServer();
        netClient.createRoom();
        isMultiplayer = true;
        gameStarted = false;
        initGame();
        createGameScene();
    }

    void joinRoomDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Join Room");
        dialog.setHeaderText("Enter Room ID:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(id -> {
            connectToServer();
            netClient.joinRoom(id);
            isMultiplayer = true;
            gameStarted = false;
            initGame();
            createGameScene();
        });
    }

    void handleNetworkMessage(String msg) {
        System.out.println("NET: " + msg);
        if (msg.startsWith("ROOM_CREATED:")) {
            Platform.runLater(() -> primaryStage.setTitle("Room: " + msg.split(":")[1] + " (Waiting)"));
        } else if (msg.startsWith("JOIN_SUCCESS:")) {
            Platform.runLater(() -> primaryStage.setTitle("Room: " + msg.split(":")[1]));
        } else if (msg.equals("GAME_START")) {
            gameStarted = true;
        } else if (msg.startsWith("OPPONENT_STATE:")) {
            parseOpponentState(msg.substring("OPPONENT_STATE:".length()));
        } else if (msg.startsWith("GARBAGE:")) {
            int lines = Integer.parseInt(msg.split(":")[1]);
            addGarbage(lines);
        } else if (msg.equals("OPPONENT_GAME_OVER")) {
            opponentGameOver = true;
        }
    }

    void initGame() {
        board = new Board(WIDTH, HEIGHT);
        score = 0;
        level = 1;
        linesCleared = 0;
        isGameOver = false;
        opponentGameOver = false;
        dropInterval = 1000;
        spawnNextPiece();
        spawnPiece();
    }

    void update(long now) {
        if (isGameOver)
            return;

        handleInput(now);

        if (input.down) {
            double speedFactor = GameSettings.SOFT_DROP_SPEED;
            if (now - lastUpdate > dropInterval / speedFactor) {
                move(0, 1);
                score += 1;
                lastUpdate = now;
            }
        } else {
            if (now - lastUpdate > dropInterval) {
                move(0, 1);
                lastUpdate = now;
            }
        }

        // Lock / Gravity Logic
        if (!board.canMove(currentPiece, currentPiece.x, currentPiece.y + 1)) {
            if (!touchingGround) {
                touchingGround = true;
                lockTimer = System.currentTimeMillis();
            } else {
                if (System.currentTimeMillis() - lockTimer > lockDelayMs) {
                    lock();
                }
            }
        } else {
            touchingGround = false;
        }
    }

    void handleInput(long now) {
        long das = input.DAS;
        long arr = input.ARR;

        if (input.left) {
            if (now - input.leftTimer >= das) {
                if (now - lastArrUpdate >= arr) {
                    move(-1, 0);
                    lastArrUpdate = now;
                }
            }
        }
        if (input.right) {
            if (now - input.rightTimer >= das) {
                if (now - lastArrUpdate >= arr) {
                    move(1, 0);
                    lastArrUpdate = now;
                }
            }
        }
    }

    void move(int dx, int dy) {
        if (board.canMove(currentPiece, currentPiece.x + dx, currentPiece.y + dy)) {
            currentPiece.x += dx;
            currentPiece.y += dy;
            if (dx != 0 || dy != 0) {
                // Reset Lock Timer
                if (touchingGround) {
                    lockTimer = System.currentTimeMillis();
                }
                currentPiece.lastMoveWasRotate = false;
            }
        }
    }

    void rotate(boolean clockwise) {
        int[][] rotatedShape = currentPiece.getRotatedShape(clockwise);
        int nextState = clockwise ? (currentPiece.rotationState + 1) % 4 : (currentPiece.rotationState + 3) % 4;

        int[][][] table = (currentPiece.type == TetrominoType.I) ? KickData.I_KICKS : KickData.JLSTZ_KICKS;
        if (currentPiece.type == TetrominoType.O)
            table = new int[][][] { { { 0, 0 } } };

        int index = getKickIndex(currentPiece.rotationState, nextState);
        int[][] kicks = (currentPiece.type == TetrominoType.O) ? new int[][] { { 0, 0 } } : table[index];

        for (int[] k : kicks) {
            int kickX = k[0];
            int kickY = -k[1];

            Tetromino temp = new Tetromino(currentPiece.type);
            temp.shape = rotatedShape;
            temp.x = currentPiece.x + kickX;
            temp.y = currentPiece.y + kickY;

            if (board.canMove(temp, temp.x, temp.y)) {
                currentPiece.shape = rotatedShape;
                currentPiece.x += kickX;
                currentPiece.y += kickY;
                currentPiece.rotationState = nextState;
                currentPiece.lastMoveWasRotate = true;
                if (touchingGround)
                    lockTimer = System.currentTimeMillis();
                return;
            }
        }
    }

    int getKickIndex(int current, int next) {
        if (current == 0 && next == 1)
            return 0;
        if (current == 1 && next == 0)
            return 1;
        if (current == 1 && next == 2)
            return 2;
        if (current == 2 && next == 1)
            return 3;
        if (current == 2 && next == 3)
            return 4;
        if (current == 3 && next == 2)
            return 5;
        if (current == 3 && next == 0)
            return 6;
        if (current == 0 && next == 3)
            return 7;
        return 0;
    }

    void lock() {
        board.lock(currentPiece);

        // Game Over Check: Lock Out (if piece locks within buffer zone)
        for (int r = 0; r < currentPiece.shape.length; r++) {
            for (int c = 0; c < currentPiece.shape.length; c++) {
                if (currentPiece.shape[r][c] != 0) {
                    if (currentPiece.y + r < BUFFER_ZONE) {
                        gameOver();
                        return;
                    }
                }
            }
        }

        int cleared = board.clearLines();

        if (isMultiplayer)
            sendBoardState();

        boolean tspin = false;
        if (currentPiece.type == TetrominoType.T && currentPiece.lastMoveWasRotate) {
            int px = currentPiece.x;
            int py = currentPiece.y;
            int corners = 0;
            if (isOccupied(px, py))
                corners++;
            if (isOccupied(px + 2, py))
                corners++;
            if (isOccupied(px, py + 2))
                corners++;
            if (isOccupied(px + 2, py + 2))
                corners++;
            if (corners >= 3)
                tspin = true;
        }

        if (cleared > 0) {
            combo++;
            linesCleared += cleared;
            int base = 0;
            if (tspin) {
                switch (cleared) {
                    case 1:
                        base = 800;
                        break;
                    case 2:
                        base = 1200;
                        break;
                    case 3:
                        base = 1600;
                        break;
                }
            } else {
                switch (cleared) {
                    case 1:
                        base = 100;
                        break;
                    case 2:
                        base = 300;
                        break;
                    case 3:
                        base = 500;
                        break;
                    case 4:
                        base = 800;
                        break;
                }
            }
            if ((tspin || cleared == 4) && backToBack)
                base = (int) (base * 1.5);
            backToBack = (tspin || cleared == 4);
            score += base * level;
            level = linesCleared / 10 + 1;

            dropInterval = Math.max(50, 1000 - (level - 1) * 100);

            if (isMultiplayer && cleared > 1) {
                int damage = cleared - 1 + (tspin ? 1 : 0);
                if (damage > 0)
                    netClient.sendAttack(damage);
            }
        } else {
            combo = -1;
        }
        spawnPiece();
    }

    void sendBoardState() {
        if (netClient == null)
            return;
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                sb.append(board.grid[y][x] == 0 ? "0" : "1");
            }
        }
        netClient.sendState(sb.toString());
    }

    void parseOpponentState(String data) {
        if (opponentBoard == null)
            opponentBoard = new int[HEIGHT][WIDTH];
        int idx = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (idx < data.length()) {
                    opponentBoard[y][x] = data.charAt(idx++) == '1' ? 1 : 0;
                }
            }
        }
    }

    void addGarbage(int lines) {
        for (int i = 0; i < lines; i++) {
            for (int y = 0; y < HEIGHT - 1; y++) {
                board.grid[y] = board.grid[y + 1].clone();
            }
            board.grid[HEIGHT - 1] = new int[WIDTH];
            for (int x = 0; x < WIDTH; x++)
                board.grid[HEIGHT - 1][x] = 1;
            board.grid[HEIGHT - 1][new Random().nextInt(WIDTH)] = 0;
        }
    }

    boolean isOccupied(int x, int y) {
        if (x < 0 || x >= WIDTH || y >= HEIGHT)
            return true;
        if (y < 0)
            return false;
        return board.grid[y][x] != 0;
    }

    void hardDrop() {
        int dropPoints = 0;
        while (board.canMove(currentPiece, currentPiece.x, currentPiece.y + 1)) {
            currentPiece.y++;
            dropPoints += 2;
        }
        score += dropPoints;
        lock();
    }

    void hold() {
        if (holdUsed)
            return;
        if (holdPiece == null) {
            holdPiece = new Tetromino(currentPiece.type);
            spawnPiece();
        } else {
            TetrominoType temp = currentPiece.type;
            currentPiece = new Tetromino(holdPiece.type);
            currentPiece.x = WIDTH / 2 - currentPiece.shape.length / 2;
            currentPiece.y = 0;
            holdPiece = new Tetromino(temp);
        }
        holdUsed = true;
        if (!board.canMove(currentPiece, currentPiece.x, currentPiece.y)) {
            gameOver();
        }
    }

    void spawnPiece() {
        currentPiece = nextPiece;
        spawnNextPiece();
        currentPiece.x = WIDTH / 2 - currentPiece.shape.length / 2;
        currentPiece.y = 0; // Spawns at top of buffer (hidden)
        touchingGround = false;
        holdUsed = false;

        // Game Over Check: If we can't spawn at all
        if (!board.canMove(currentPiece, currentPiece.x, currentPiece.y)) {
            gameOver();
        }
    }

    void spawnNextPiece() {
        TetrominoType[] types = TetrominoType.values();
        nextPiece = new Tetromino(types[new Random().nextInt(types.length)]);
    }

    void gameOver() {
        isGameOver = true;
        if (isMultiplayer && netClient != null)
            netClient.sendGameOver();

        // Return to menu after 3 seconds
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> {
            if (gameLoop != null) {
                gameLoop.stop();
            }
            if (netClient != null) {
                netClient.disconnect();
                netClient = null;
            }
            isMultiplayer = false;
            Platform.runLater(() -> primaryStage.setScene(menuScene));
        });
        pause.play();
    }

    void draw() {
        if (!isGameOver) {
            handleInput(System.currentTimeMillis());
        }

        drawBackground();
        drawBoard(BOARD_OFFSET_X, BOARD_OFFSET_Y, board.grid, TILE);
        drawGhost();
        drawPiece(currentPiece, currentPiece.x, currentPiece.y);

        if (isMultiplayer && opponentBoard != null) {
            // Opponent board: hide buffer zone, show only visible 20 rows
            drawBoard(OPPONENT_OFFSET_X, BOARD_OFFSET_Y, opponentBoard, 20, true);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 20));
            gc.fillText("OPPONENT", OPPONENT_OFFSET_X, BOARD_OFFSET_Y - 20);
        }

        drawUI();
    }

    void drawWaiting() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(30));
        gc.fillText("Waiting for Opponent...", CANVAS_WIDTH / 2 - 150, CANVAS_HEIGHT / 2);
    }

    void drawBackground() {
        // Base: Fill everything with dark background just in case
        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // 1. Buffer Zone (Top 4 Rows) - Gray
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(BOARD_OFFSET_X, BOARD_OFFSET_Y, WIDTH * TILE, BUFFER_ZONE * TILE);

        // 2. Play Area (Remaining 20 Rows) - Black
        gc.setFill(Color.BLACK);
        gc.fillRect(BOARD_OFFSET_X, BOARD_OFFSET_Y + BUFFER_ZONE * TILE, WIDTH * TILE, VISIBLE_HEIGHT * TILE);

        // Multiplay Opponent BG
        if (isMultiplayer) {
            // Opponent background
            gc.setFill(Color.BLACK); // Keeps it simple for opponent
            gc.fillRect(OPPONENT_OFFSET_X, BOARD_OFFSET_Y, WIDTH * 20, VISIBLE_HEIGHT * 20);
        }
    }

    void drawBoard(int offsetX, int offsetY, int[][] grid, int tileSize) {
        drawBoard(offsetX, offsetY, grid, tileSize, false);
    }

    void drawBoard(int offsetX, int offsetY, int[][] grid, int tileSize, boolean hideBuffer) {
        int startY = hideBuffer ? BUFFER_ZONE : 0;

        // Draw rows
        for (int y = startY; y < grid.length; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int val = grid[y][x];

                // Calculate visual Y position
                // if hideBuffer is true, row BUFFER_ZONE should be at offsetY (index 0 relative
                // to visible)
                // if hideBuffer is false, row 0 is at offsetY
                int visualY = hideBuffer ? (y - BUFFER_ZONE) : y;

                if (val > 0) {
                    // Use stored color
                    // Ensure index is valid (val-1)
                    TetrominoType[] types = TetrominoType.values();
                    Color c = Color.GRAY;
                    if (val - 1 >= 0 && val - 1 < types.length) {
                        c = types[val - 1].color;
                    }

                    gc.setFill(c);
                    gc.fillRect(offsetX + x * tileSize, offsetY + visualY * tileSize, tileSize, tileSize);
                    gc.setStroke(Color.rgb(40, 40, 40));
                    gc.strokeRect(offsetX + x * tileSize, offsetY + visualY * tileSize, tileSize, tileSize);
                } else {
                    // Grid lines
                    gc.setStroke(Color.rgb(40, 40, 40));
                    gc.strokeRect(offsetX + x * tileSize, offsetY + visualY * tileSize, tileSize, tileSize);
                }
            }
        }
    }

    void drawPiece(Tetromino p, int boardX, int boardY) {
        if (p == null)
            return;
        int[][] shape = p.shape;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape.length; c++) {
                if (shape[r][c] != 0) {
                    // Draw all parts, even in buffer
                    drawTile(boardX + c, boardY + r, p.type.color);
                }
            }
        }
    }

    void drawGhost() {
        if (isGameOver)
            return;
        if (currentPiece == null)
            return;
        int ghostY = currentPiece.y;
        while (board.canMove(currentPiece, currentPiece.x, ghostY + 1)) {
            ghostY++;
        }
        int[][] shape = currentPiece.shape;
        gc.setFill(Color.rgb(255, 255, 255, 0.2));
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape.length; c++) {
                if (shape[r][c] != 0) {
                    gc.fillRect(BOARD_OFFSET_X + (currentPiece.x + c) * TILE,
                            BOARD_OFFSET_Y + (ghostY + r) * TILE, TILE, TILE);
                }
            }
        }
    }

    void drawTile(int x, int y, Color color) {
        // x, y here are VISIBLE coordinates (0 to 19)
        gc.setFill(color);
        gc.fillRect(BOARD_OFFSET_X + x * TILE, BOARD_OFFSET_Y + y * TILE, TILE, TILE);
        gc.setStroke(Color.WHITESMOKE);
        gc.strokeRect(BOARD_OFFSET_X + x * TILE, BOARD_OFFSET_Y + y * TILE, TILE, TILE);
    }

    void drawUI() {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        gc.fillText("SCORE: " + score, 400, 50);
        gc.fillText("LEVEL: " + level, 400, 80);

        // NEXT
        gc.fillText("NEXT", 400, 150);
        if (nextPiece != null)
            drawMini(nextPiece, 400, 180);

        // HOLD
        gc.fillText("HOLD", 400, 300);
        if (holdPiece != null)
            drawMini(holdPiece, 400, 330);

        if (isGameOver) {
            gc.setFill(Color.RED);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 40));
            gc.fillText(opponentGameOver ? "VICTORY!" : "GAME OVER", 180, 300);
        }

        if (isMultiplayer && opponentGameOver && !isGameOver) {
            gc.setFill(Color.GREEN);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 40));
            gc.fillText("VICTORY!", 180, 300);
            gameLoop.stop();
        }
    }

    void drawMini(Tetromino p, int sx, int sy) {
        int[][] shape = p.shape;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape.length; c++) {
                if (shape[r][c] != 0) {
                    gc.setFill(p.type.color);
                    gc.fillRect(sx + c * 20, sy + r * 20, 20, 20);
                }
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
