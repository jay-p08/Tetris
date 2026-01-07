package tetris;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import tetris.net.NetworkClient;

public class Main extends Application {

    static final int TILE = 30;
    static final int BUFFER_ZONE = 4;

    int width = 10;
    int height = 24;
    int visibleHeight = 20;

    static final int CANVAS_WIDTH = 1000;
    static final int CANVAS_HEIGHT = 800;
    static final int BOARD_OFFSET_Y = 50;
    // Dynamic offsets calculated in draw()
    int boardOffsetX = 50;
    int opponentOffsetX = 700;

    // Game Components
    Board board;
    Tetromino currentPiece;
    Queue<Tetromino> nextPieces = new LinkedList<>(); // Show 4 next pieces
    Tetromino holdPiece;
    InputHandler input = new InputHandler();
    NetworkClient netClient;
    SoundManager soundManager = new SoundManager();
    List<TextPopup> textPopups = new ArrayList<>();

    // 7-Bag System
    Queue<TetrominoType> pieceBag = new LinkedList<>();
    Random random = new Random();

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
    int softDropDistance = 0; // Track soft drop distance for scoring

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
    private Stage primaryStage;
    private Scene loginScene, registerScene, menuScene, settingsScene, gameScene, lobbyScene;
    private javafx.collections.ObservableList<RoomInfo> roomList = javafx.collections.FXCollections
            .observableArrayList();
    private javafx.scene.control.TableView<RoomInfo> roomTable;
    private Label lblConnectionStatus;
    private Pane gameRoot;
    private GraphicsContext gc;
    AnimationTimer gameLoop;

    private String nickname = "Guest";
    private DatabaseManager db = DatabaseManager.getInstance();

    private String serverIp = "localhost";

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        soundManager.setSoundEnabled(GameSettings.isSoundEnabled());
        createLoginScene();
        createMenuScene();

        stage.setScene(loginScene);
        stage.setTitle("Tetris Ultimate - Login");
        stage.show();
    }

    void createMenuScene() {
        VBox root = new VBox(25);
        root.setAlignment(Pos.CENTER);

        // Gradient background via CSS
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);");

        Label title = new Label("TETRIS ULTIMATE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 50));
        title.setTextFill(Color.web("#00d2ff"));
        title.setEffect(new javafx.scene.effect.DropShadow(10, Color.CYAN));

        Button btnSingle = createStyledButton("SINGLE PLAYER");
        btnSingle.setOnAction(e -> startSinglePlayer());

        Button btnCreate = createStyledButton("MULTIPLAYER");
        btnCreate.setOnAction(e -> showLobby());

        Button btnSettings = createStyledButton("SETTINGS");
        btnSettings.setOnAction(e -> showSettings());

        root.getChildren().addAll(title, btnSingle, btnCreate, btnSettings);
        menuScene = new Scene(root, 500, 600);
    }

    void showLobby() {
        if (lobbyScene == null)
            createLobbyScene();
        primaryStage.setScene(lobbyScene);

        // Try connecting in background if not connected
        new Thread(() -> {
            if (connectToServer(false)) { // false = don't show alert if fails silently here
                netClient.requestRoomList();
            }
        }).start();
    }

    void showSettings() {
        if (settingsScene == null) {
            createSettingsScene();
        }
        primaryStage.setScene(settingsScene);
    }

    void createSettingsScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new javafx.geometry.Insets(40));
        root.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("SETTINGS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 30));
        title.setTextFill(Color.web("#00d2ff"));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        SettingsManager sm = SettingsManager.getInstance();

        // Helper to create validated TextFields
        javafx.scene.control.TextField tfDas = createValidatedField("" + sm.getDas(), 50, 300);
        javafx.scene.control.TextField tfArr = createValidatedField("" + sm.getArr(), 0, 100);
        javafx.scene.control.TextField tfSdf = createValidatedField("" + sm.getSdf(), 5, 40);
        javafx.scene.control.TextField tfWidth = createValidatedField("" + sm.getBoardWidth(), 4, 15);
        javafx.scene.control.TextField tfHeight = createValidatedField("" + sm.getBoardHeight(), 10, 24);

        grid.add(createLabel("DAS (50-300ms)"), 0, 0);
        grid.add(tfDas, 1, 0);
        grid.add(createLabel("ARR (0-100ms)"), 0, 1);
        grid.add(tfArr, 1, 1);
        grid.add(createLabel("SDF (5-40x)"), 0, 2);
        grid.add(tfSdf, 1, 2);
        grid.add(createLabel("Board Width (4-15)"), 0, 3);
        grid.add(tfWidth, 1, 3);
        grid.add(createLabel("Board Height (10-24)"), 0, 4);
        grid.add(tfHeight, 1, 4);

        CheckBox cbGhost = new CheckBox("Enable Ghost Piece");
        cbGhost.setSelected(sm.isGhostEnabled());
        cbGhost.setTextFill(Color.WHITE);

        CheckBox cbSound = new CheckBox("Enable Sound Effects");
        cbSound.setSelected(sm.isSoundEnabled());
        cbSound.setTextFill(Color.WHITE);

        grid.add(cbGhost, 0, 5, 2, 1);
        grid.add(cbSound, 0, 6, 2, 1);

        Button btnBack = createStyledButton("SAVE & BACK");
        btnBack.setOnAction(e -> {
            sm.setDas(Long.parseLong(tfDas.getText()));
            sm.setArr(Long.parseLong(tfArr.getText()));
            sm.setSdf(Double.parseDouble(tfSdf.getText()));
            sm.setBoardWidth(Integer.parseInt(tfWidth.getText()));
            sm.setBoardHeight(Integer.parseInt(tfHeight.getText()));
            sm.setGhostEnabled(cbGhost.isSelected());
            sm.setSoundEnabled(cbSound.isSelected());

            sm.save();
            if (!nickname.equals("Guest")) {
                db.updateSettings(nickname, sm.getDas(), sm.getArr(), sm.getSdf(), sm.isGhostEnabled(),
                        sm.isSoundEnabled(), sm.getBoardWidth(), sm.getBoardHeight());
            }

            GameSettings.refresh();
            input.refreshConfig();
            soundManager.setSoundEnabled(GameSettings.isSoundEnabled());
            primaryStage.setScene(menuScene);
        });

        root.getChildren().addAll(title, grid, btnBack);
        settingsScene = new Scene(root, 500, 650);
    }

    javafx.scene.control.TextField createValidatedField(String initialValue, double min, double max) {
        javafx.scene.control.TextField tf = new javafx.scene.control.TextField(initialValue);
        tf.setMaxWidth(80);
        tf.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) { // On Blur
                try {
                    double val = Double.parseDouble(tf.getText());
                    if (val < min)
                        val = min;
                    if (val > max)
                        val = max;
                    if (min == (int) min && max == (int) max) {
                        tf.setText("" + (int) val);
                    } else {
                        tf.setText(String.format("%.1f", val));
                    }
                } catch (NumberFormatException e) {
                    tf.setText("" + (int) min);
                }
            }
        });
        return tf;
    }

    Label createLabel(String text) {
        Label lbl = new Label(text);
        lbl.setTextFill(Color.WHITE);
        lbl.setFont(Font.font("Segoe UI", 14));
        return lbl;
    }

    Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #33334d; -fx-text-fill: #e0e0e0; -fx-font-size: 16px; " +
                "-fx-min-width: 250px; -fx-background-radius: 10; -fx-border-color: #55557a; " +
                "-fx-border-radius: 10; -fx-border-width: 2; -fx-padding: 10;");

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #4a4a6a; -fx-text-fill: white; -fx-font-size: 16px; " +
                        "-fx-min-width: 250px; -fx-background-radius: 10; -fx-border-color: #00d2ff; " +
                        "-fx-border-radius: 10; -fx-border-width: 2; -fx-padding: 10;"));

        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: #33334d; -fx-text-fill: #e0e0e0; -fx-font-size: 16px; " +
                        "-fx-min-width: 250px; -fx-background-radius: 10; -fx-border-color: #55557a; " +
                        "-fx-border-radius: 10; -fx-border-width: 2; -fx-padding: 10;"));
        return btn;
    }

    void createGameScene() {
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        gc = canvas.getGraphicsContext2D();

        // Responsive Centering: Wrap canvas in a StackPane
        StackPane container = new StackPane(canvas);
        container.setStyle("-fx-background-color: #0f0c29;"); // Match game background

        gameRoot = container;
        gameScene = new Scene(gameRoot, CANVAS_WIDTH, CANVAS_HEIGHT);

        gameScene.setOnKeyPressed(e -> {
            if (!gameStarted && isMultiplayer)
                return;
            long now = System.currentTimeMillis();
            KeyCode code = e.getCode();

            // Toggle Fullscreen on F11
            if (code == KeyCode.F11) {
                primaryStage.setFullScreen(!primaryStage.isFullScreen());
                return;
            }

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
                default -> {
                }
            }
        });

        gameScene.setOnKeyReleased(e -> {
            KeyCode code = e.getCode();
            switch (code) {
                case LEFT -> input.releaseLeft();
                case RIGHT -> input.releaseRight();
                case DOWN -> input.down = false;
                default -> {
                }
            }
        });
    }

    void startGameLoop() {
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
        SettingsManager sm = SettingsManager.getInstance();
        this.width = sm.getBoardWidth();
        this.height = sm.getBoardHeight() + BUFFER_ZONE;
        this.visibleHeight = sm.getBoardHeight();

        board = new Board(width, height);
        isMultiplayer = false;
        gameStarted = true;
        resetGame();
        createGameScene();
        primaryStage.setScene(gameScene);
        startGameLoop();
    }

    boolean connectToServer() {
        return connectToServer(true);
    }

    boolean connectToServer(boolean showAlert) {
        if (netClient == null || !netClient.isConnected()) {
            netClient = new NetworkClient();
            netClient.onMessage = this::handleNetworkMessage;
            try {
                String wsUri = serverIp.replace("https://", "").replace("http://", "");
                if (!wsUri.startsWith("ws://") && !wsUri.startsWith("wss://")) {
                    if (wsUri.contains(".onrender.com") || wsUri.contains(".glitch.me")) {
                        wsUri = "wss://" + wsUri;
                    } else if (!wsUri.contains(":")) {
                        wsUri = "ws://" + wsUri + ":9999";
                    } else {
                        wsUri = "ws://" + wsUri;
                    }
                }
                System.out.println("[CLIENT] Connecting to: " + wsUri);
                netClient.connect(wsUri);

                // Wait up to 30 seconds for cloud servers to wake up (Render/Glitch)
                Platform.runLater(() -> {
                    if (lblConnectionStatus != null) {
                        lblConnectionStatus.setText("Status: Waking up server... (Please wait)");
                        lblConnectionStatus.setTextFill(Color.YELLOW);
                    }
                });

                for (int i = 0; i < 60; i++) {
                    if (netClient.isConnected())
                        break;
                    Thread.sleep(500);
                }

                if (netClient.isConnected()) {
                    updateConnectionStatus(true);
                    return true;
                } else {
                    throw new Exception("Connection timeout (Server didn't wake up in 30s)");
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] Connection Failed to " + serverIp);
                System.out.println("[DEBUG] Error Reason: " + e.getMessage());
                e.printStackTrace();
                netClient = null;
                updateConnectionStatus(false);
                if (showAlert) {
                    Platform.runLater(() -> {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.ERROR);
                        alert.setTitle("Connection Failed");
                        alert.setHeaderText("Unable to connect to server: " + serverIp);
                        alert.setContentText("Check these common issues:\n\n" +
                                "1. [Host] Check if Local Port in playit.gg is set to 9999.\n" +
                                "2. [Guest] If using playit.gg, add ':25565' to the address.\n" +
                                "3. [Guest] Ensure you typed the full link correctly.\n" +
                                "4. [Everyone] Double check your internet connection.");
                        alert.showAndWait();
                    });
                }
                return false;
            }
        }
        updateConnectionStatus(true);
        return true;
    }

    private void updateConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            if (lblConnectionStatus != null) {
                if (connected) {
                    lblConnectionStatus.setText("Status: Connected to " + serverIp);
                    lblConnectionStatus.setTextFill(Color.LIME);
                } else {
                    lblConnectionStatus.setText("Status: Disconnected (Server offline)");
                    lblConnectionStatus.setTextFill(Color.RED);
                }
            }
        });
    }

    void handleNetworkMessage(String msg) {
        if (!msg.equals("DISCONNECTED")) {
            updateConnectionStatus(true);
        }
        System.out.println("[CLIENT] Received: " + msg);
        if (msg.startsWith("ROOM_CREATED:")) {
            Platform.runLater(() -> {
                SettingsManager sm = SettingsManager.getInstance();
                this.width = sm.getBoardWidth();
                this.height = sm.getBoardHeight() + BUFFER_ZONE;
                this.visibleHeight = sm.getBoardHeight();

                primaryStage.setTitle("Room: " + msg.split(":")[1] + " (Waiting)");
                isMultiplayer = true;
                gameStarted = false;
                resetGame();
                createGameScene();
                primaryStage.setScene(gameScene);
                startGameLoop();
            });
        } else if (msg.startsWith("JOIN_SUCCESS:")) {
            Platform.runLater(() -> {
                primaryStage.setTitle("Room: " + msg.split(":")[1]);
                isMultiplayer = true;
                gameStarted = false;
                // Scene transition will happen when GAME_START is received
            });
        } else if (msg.startsWith("GAME_START:")) {
            String[] dims = msg.split(":")[1].split(",");
            int w = Integer.parseInt(dims[0]);
            int h = Integer.parseInt(dims[1]);
            Platform.runLater(() -> {
                this.width = w;
                this.visibleHeight = h;
                this.height = h + BUFFER_ZONE;
                gameStarted = true;
                resetGame();
                createGameScene();
                primaryStage.setScene(gameScene);
                startGameLoop();
            });
        } else if (msg.equals("GAME_START")) { // Legacy support
            Platform.runLater(() -> {
                gameStarted = true;
                resetGame();
                createGameScene();
                primaryStage.setScene(gameScene);
                startGameLoop();
            });
        } else if (msg.startsWith("OPPONENT_STATE:")) {
            parseOpponentState(msg.substring("OPPONENT_STATE:".length()));
        } else if (msg.startsWith("ROOM_LIST:")) {
            Platform.runLater(() -> {
                roomList.clear();
                String data = msg.substring(10);
                System.out.println("[CLIENT] Parsing Room List Data: " + data);
                if (!data.isEmpty()) {
                    String[] rooms = data.split(";");
                    for (String r : rooms) {
                        if (r.contains("|")) {
                            String[] f = r.split("\\|");
                            roomList.add(new RoomInfo(f[0], f[1], Boolean.parseBoolean(f[2]), f[3]));
                        }
                    }
                }
                System.out.println("[CLIENT] Room List Updated. Count: " + roomList.size());
            });
        } else if (msg.startsWith("GARBAGE:")) {
            int lines = Integer.parseInt(msg.split(":")[1]);
            addGarbage(lines);
        } else if (msg.startsWith("BOARD_SIZE:")) {
            String[] dims = msg.split(":");
            int w = Integer.parseInt(dims[1]);
            int h = Integer.parseInt(dims[2]);
            this.width = w;
            this.height = h + BUFFER_ZONE;
            this.visibleHeight = h;
            this.board = new Board(width, height);
            System.out.println("Resized board to: " + w + "x" + h);
        } else if (msg.equals("OPPONENT_GAME_OVER")) {
            opponentGameOver = true;
            isGameOver = true; // Stop the local game to show Victory!
            if (gameLoop != null)
                gameLoop.stop();
            Platform.runLater(() -> {
                PauseTransition pt = new PauseTransition(Duration.seconds(3));
                pt.setOnFinished(ev -> primaryStage.setScene(menuScene));
                pt.play();
            });
        }
    }

    void resetGame() { // Renamed from initGame
        board = new Board(width, height);
        score = 0;
        level = 1;
        linesCleared = 0;
        isGameOver = false;
        opponentGameOver = false;
        dropInterval = 1000;

        // Initialize next pieces queue with 4 pieces
        nextPieces.clear();
        for (int i = 0; i < 4; i++) {
            nextPieces.add(generateNextPiece());
        }

        spawnPiece();
    }

    void update(long now) {
        if (isGameOver)
            return;

        handleInput(now);

        if (input.down) {
            double speedFactor = GameSettings.SOFT_DROP_SPEED;
            if (now - lastUpdate > dropInterval / speedFactor) {
                if (move(0, 1)) {
                    softDropDistance++; // Track distance for scoring on lock
                }
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
        long das = input.das;
        long arr = input.arr;

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

    boolean move(int dx, int dy) {
        if (board.canMove(currentPiece, currentPiece.x + dx, currentPiece.y + dy)) {
            currentPiece.x += dx;
            currentPiece.y += dy;
            if (dx != 0 || dy != 0) {
                // Reset Lock Timer
                if (touchingGround) {
                    lockTimer = System.currentTimeMillis();
                }
                currentPiece.lastMoveWasRotate = false;
                // Play move sound
                if (dx != 0) {
                    soundManager.playMove();
                }
            }
            return true;
        }
        return false;
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
                soundManager.playRotate();
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
        soundManager.playLock();

        // Award soft drop points
        if (softDropDistance > 0) {
            score += softDropDistance;
            softDropDistance = 0; // Reset for next piece
        }

        // Detect spin BEFORE locking (check corners while piece is still in position)
        boolean isSpin = false;
        String spinType = "";

        if (currentPiece.lastMoveWasRotate) {
            int px = currentPiece.x;
            int py = currentPiece.y;
            int corners = 0;

            // Count occupied corners (2x2 bounding box corners)
            if (isOccupied(px, py))
                corners++;
            if (isOccupied(px + 2, py))
                corners++;
            if (isOccupied(px, py + 2))
                corners++;
            if (isOccupied(px + 2, py + 2))
                corners++;

            // Spin detection: 3+ corners occupied
            if (corners >= 3) {
                isSpin = true;
                switch (currentPiece.type) {
                    case T -> spinType = "T-SPIN";
                    case I -> spinType = "I-SPIN";
                    case J -> spinType = "J-SPIN";
                    case L -> spinType = "L-SPIN";
                    case S -> spinType = "S-SPIN";
                    case Z -> spinType = "Z-SPIN";
                    case O -> spinType = "O-SPIN";
                }
            }
        }

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

        if (cleared > 0) {
            combo++;
            linesCleared += cleared;
            int base = 0;
            String clearText = "";

            if (isSpin) {
                soundManager.playTSpin();
                switch (cleared) {
                    case 1:
                        base = 800;
                        clearText = spinType + " SINGLE";
                        break;
                    case 2:
                        base = 1200;
                        clearText = spinType + " DOUBLE";
                        soundManager.playVoice("tspin_double");
                        break;
                    case 3:
                        base = 1600;
                        clearText = spinType + " TRIPLE";
                        soundManager.playVoice("tspin_triple");
                        break;
                }
            } else {
                switch (cleared) {
                    case 1:
                        base = 100;
                        clearText = "SINGLE";
                        soundManager.playSingle();
                        break;
                    case 2:
                        base = 300;
                        clearText = "DOUBLE";
                        soundManager.playDouble();
                        break;
                    case 3:
                        base = 500;
                        clearText = "TRIPLE";
                        soundManager.playTriple();
                        break;
                    case 4:
                        base = 800;
                        clearText = "TETRIS";
                        soundManager.playTetris();
                        soundManager.playVoice("tetris");
                        break;
                }
            }

            // Add text popup
            if (!clearText.isEmpty()) {
                Color popupColor = isSpin ? Color.MAGENTA : (cleared == 4 ? Color.GOLD : Color.CYAN);
                textPopups.add(new TextPopup(clearText, 200, 400, popupColor, 1500));
            }

            if ((isSpin || cleared == 4) && backToBack) {
                base = (int) (base * 1.5);
                textPopups.add(new TextPopup("BACK-TO-BACK", 200, 450, Color.ORANGE, 1500));
            }
            backToBack = (isSpin || cleared == 4);
            score += base * level;
            level = linesCleared / 10 + 1;

            dropInterval = Math.max(50, 1000 - (level - 1) * 100);

            // Enhanced attack system
            if (isMultiplayer) {
                int damage = 0;
                if (isSpin) {
                    // Spin attack damage (T-Spin is strongest, others slightly weaker)
                    switch (cleared) {
                        case 1:
                            damage = currentPiece.type == TetrominoType.T ? 2 : 1;
                            break; // T-Spin Single: 2, others: 1
                        case 2:
                            damage = currentPiece.type == TetrominoType.T ? 4 : 3;
                            break; // T-Spin Double: 4, others: 3
                        case 3:
                            damage = currentPiece.type == TetrominoType.T ? 6 : 5;
                            break; // T-Spin Triple: 6, others: 5
                    }
                } else {
                    // Normal attack damage
                    switch (cleared) {
                        case 1:
                            damage = 0;
                            break; // Single - no attack
                        case 2:
                            damage = 1;
                            break; // Double
                        case 3:
                            damage = 2;
                            break; // Triple
                        case 4:
                            damage = 4;
                            break; // Tetris
                    }
                }

                // Back-to-back bonus: +1 damage
                if (backToBack && (isSpin || cleared == 4)) {
                    damage += 1;
                }

                if (damage > 0) {
                    netClient.sendAttack(damage);
                    soundManager.playAttack();
                    textPopups.add(new TextPopup("ATTACK: " + damage, 200, 500, Color.RED, 1000));
                }
            }

            // Combo display and sound
            if (combo > 0) {
                textPopups.add(new TextPopup("COMBO x" + combo, 200, 550, Color.YELLOW, 1000));
                if (combo >= 3) {
                    soundManager.playCombo(combo);
                }
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
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(board.grid[y][x] == 0 ? "0" : "1");
            }
        }
        netClient.sendState(sb.toString());
    }

    void parseOpponentState(String data) {
        if (opponentBoard == null)
            opponentBoard = new int[height][width];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (idx < data.length()) {
                    opponentBoard[y][x] = data.charAt(idx++) == '1' ? 1 : 0;
                }
            }
        }
    }

    void addGarbage(int lines) {
        soundManager.playGarbageReceived();
        textPopups.add(new TextPopup("GARBAGE: " + lines, 200, 350, Color.DARKRED, 1000));
        for (int i = 0; i < lines; i++) {
            for (int y = 0; y < height - 1; y++) {
                board.grid[y] = board.grid[y + 1].clone();
            }
            board.grid[height - 1] = new int[width];
            for (int x = 0; x < width; x++)
                board.grid[height - 1][x] = 1;
            board.grid[height - 1][new Random().nextInt(width)] = 0;
        }
    }

    boolean isOccupied(int x, int y) {
        if (x < 0 || x >= width || y >= height)
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
        soundManager.playHardDrop();
        lock();
    }

    void hold() {
        if (holdUsed)
            return;
        soundManager.playHold();
        if (holdPiece == null) {
            holdPiece = new Tetromino(currentPiece.type);
            spawnPiece();
        } else {
            TetrominoType temp = currentPiece.type;
            currentPiece = new Tetromino(holdPiece.type);
            currentPiece.x = width / 2 - currentPiece.shape.length / 2;
            currentPiece.y = 0;
            holdPiece = new Tetromino(temp);
        }
        holdUsed = true;
        if (!board.canMove(currentPiece, currentPiece.x, currentPiece.y)) {
            gameOver();
        }
    }

    void spawnPiece() {
        // Take the first piece from the queue
        currentPiece = nextPieces.poll();

        // Add a new piece to the end of the queue
        nextPieces.add(generateNextPiece());

        currentPiece.x = width / 2 - currentPiece.shape.length / 2;
        currentPiece.y = 0; // Spawns at top of buffer (hidden)
        touchingGround = false;
        holdUsed = false;

        // Game Over Check: If we can't spawn at all
        if (!board.canMove(currentPiece, currentPiece.x, currentPiece.y)) {
            gameOver();
        }
    }

    Tetromino generateNextPiece() {
        // 7-Bag System: Refill bag if empty
        if (pieceBag.isEmpty()) {
            refillBag();
        }

        // Get next piece from bag
        TetrominoType type = pieceBag.poll();
        return new Tetromino(type);
    }

    void refillBag() {
        // Add all 7 tetromino types to the bag
        List<TetrominoType> bag = new ArrayList<>();
        for (TetrominoType type : TetrominoType.values()) {
            bag.add(type);
        }

        // Shuffle the bag
        Collections.shuffle(bag, random);

        // Add to queue
        pieceBag.addAll(bag);
    }

    void gameOver() {
        isGameOver = true;
        soundManager.playGameOver();
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

        // Calculate Centered Offsets
        int boardWidthPx = width * TILE;
        int nextPanelWidth = 150;
        int holdPanelWidth = 80; // Smaller mini-panel on left
        int totalWidthPx = boardWidthPx + nextPanelWidth + holdPanelWidth + 60; // plus margins

        if (isMultiplayer) {
            // In MP, balance the two boards
            boardOffsetX = 120; // Enough room for Hold on the left
            opponentOffsetX = 660; // Spread out but visible
        } else {
            boardOffsetX = (CANVAS_WIDTH - totalWidthPx) / 2 + holdPanelWidth + 20;
        }

        drawBackground(boardOffsetX);

        // Main Board container
        drawBoardContainer(boardOffsetX, BOARD_OFFSET_Y, width, height);
        drawBoard(boardOffsetX, BOARD_OFFSET_Y, board.grid, TILE);

        if (GameSettings.isGhostEnabled()) {
            drawGhost(boardOffsetX);
        }

        drawPiece(currentPiece, boardOffsetX, BOARD_OFFSET_Y, TILE);

        if (isMultiplayer) {
            // Opponent background
            gc.setFill(Color.BLACK);
            gc.fillRect(opponentOffsetX, BOARD_OFFSET_Y, width * 20, visibleHeight * 20);
        }
        if (isMultiplayer && opponentBoard != null) {
            drawBoard(opponentOffsetX, BOARD_OFFSET_Y, opponentBoard, 20, true);
            gc.setFill(Color.web("#00d2ff"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
            gc.fillText("OPPONENT", opponentOffsetX, BOARD_OFFSET_Y - 20);
        }

        drawHold(boardOffsetX - 100, BOARD_OFFSET_Y + 100);
        drawUI(boardOffsetX);
        drawTextPopups(boardOffsetX);
    }

    void drawBoardContainer(int x, int y, int w, int h) {
        gc.setStroke(Color.web("#33334d"));
        gc.setLineWidth(4);
        gc.strokeRoundRect(x - 5, y - 5, w * TILE + 10, h * TILE + 10, 15, 15);
        gc.setLineWidth(1);
    }

    void drawWaiting() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(30));
        gc.fillText("Waiting for Opponent...", CANVAS_WIDTH / 2 - 150, CANVAS_HEIGHT / 2);
    }

    void drawBackground(int offsetX) {
        // Dark premium background
        gc.setFill(Color.web("#0f0c29")); // Deep space blue
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // Add subtle grid pattern to background
        gc.setStroke(Color.rgb(255, 255, 255, 0.05));
        for (int i = 0; i < CANVAS_WIDTH; i += 50) {
            gc.strokeLine(i, 0, i, CANVAS_HEIGHT);
        }
        for (int i = 0; i < CANVAS_HEIGHT; i += 50) {
            gc.strokeLine(0, i, CANVAS_WIDTH, i);
        }

        // 1. Buffer Zone Overlay
        gc.setFill(Color.web("#1e1e2e", 0.5));
        gc.fillRect(offsetX, BOARD_OFFSET_Y, width * TILE, BUFFER_ZONE * TILE);

        // 2. Play Area Shadow
        gc.setFill(Color.BLACK);
        gc.fillRect(offsetX, BOARD_OFFSET_Y + BUFFER_ZONE * TILE, width * TILE, visibleHeight * TILE);
    }

    void drawBoard(int offsetX, int offsetY, int[][] grid, int tileSize) {
        drawBoard(offsetX, offsetY, grid, tileSize, false);
    }

    void drawBoard(int offsetX, int offsetY, int[][] grid, int tileSize, boolean hideBuffer) {
        int startY = hideBuffer ? BUFFER_ZONE : 0;

        // Draw rows
        for (int y = startY; y < grid.length; y++) {
            for (int x = 0; x < width; x++) {
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

    void drawPiece(Tetromino p, int offsetX, int offsetY, int tileSize) {
        if (p == null)
            return;
        int[][] shape = p.shape;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape.length; c++) {
                if (shape[r][c] != 0) {
                    drawTile(offsetX + (p.x + c) * tileSize, offsetY + (p.y + r) * tileSize, p.type.color, tileSize);
                }
            }
        }
    }

    void drawGhost(int offsetX) {
        if (isGameOver || currentPiece == null)
            return;

        int ghostY = currentPiece.y;
        while (board.canMove(currentPiece, currentPiece.x, ghostY + 1)) {
            ghostY++;
        }

        int[][] shape = currentPiece.shape;
        Color baseColor = currentPiece.type.color;
        gc.setStroke(Color.color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0.5));
        gc.setLineWidth(2);

        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape.length; c++) {
                if (shape[r][c] != 0) {
                    gc.strokeRoundRect(offsetX + (currentPiece.x + c) * TILE + 2,
                            BOARD_OFFSET_Y + (ghostY + r) * TILE + 2,
                            TILE - 4, TILE - 4, 5, 5);
                }
            }
        }
        gc.setLineWidth(1);
    }

    void drawTile(double ox, double oy, Color color, int tileSize) {
        // Gradient effect
        Stop[] stops = new Stop[] { new Stop(0, color.deriveColor(0, 1, 1.2, 1)),
                new Stop(1, color.deriveColor(0, 1, 0.8, 1)) };
        LinearGradient lg = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, stops);

        gc.setFill(lg);
        gc.fillRoundRect(ox + 1, oy + 1, tileSize - 2, tileSize - 2, 8, 8);

        // Inner highlight
        gc.setStroke(Color.rgb(255, 255, 255, 0.3));
        gc.strokeRoundRect(ox + 3, oy + 3, tileSize - 6, tileSize - 6, 6, 6);

        gc.setStroke(color.darker());
        gc.strokeRoundRect(ox + 1, oy + 1, tileSize - 2, tileSize - 2, 8, 8);
    }

    void drawUI(int offsetX) {
        if (isMultiplayer && !gameStarted) {
            gc.setFill(Color.rgb(0, 0, 0, 0.8));
            gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 40));
            gc.fillText("WAITING FOR OPPONENT...", (CANVAS_WIDTH - 500) / 2, 400);
            return;
        }

        // Modern UI Panel (Right)
        int uiX = offsetX + width * TILE + 40;
        gc.setFill(Color.web("#1e1e2e", 0.8));
        gc.fillRoundRect(uiX, BOARD_OFFSET_Y, 150, 700, 20, 20);
        gc.setStroke(Color.web("#33334d"));
        gc.strokeRoundRect(uiX, BOARD_OFFSET_Y, 150, 700, 20, 20);

        gc.setFill(Color.web("#00d2ff"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));

        gc.fillText("SCORE", uiX + 20, BOARD_OFFSET_Y + 40);
        gc.setFill(Color.WHITE);
        gc.fillText(String.format("%07d", score), uiX + 20, BOARD_OFFSET_Y + 65);

        gc.setFill(Color.web("#00d2ff"));
        gc.fillText("LEVEL", uiX + 20, BOARD_OFFSET_Y + 110);
        gc.setFill(Color.WHITE);
        gc.fillText("" + level, uiX + 20, BOARD_OFFSET_Y + 135);

        // NEXT
        gc.setFill(Color.web("#00d2ff"));
        gc.fillText("NEXT", uiX + 20, BOARD_OFFSET_Y + 190);
        int yOffset = BOARD_OFFSET_Y + 210;
        int index = 0;
        for (Tetromino piece : nextPieces) {
            if (piece != null) {
                drawMini(piece, uiX + 25, yOffset, 15);
                yOffset += 70;
            }
            index++;
            if (index >= 4)
                break;
        }

        if (isGameOver) {
            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

            if (opponentGameOver) {
                gc.setFill(Color.LIME);
            } else {
                gc.setFill(Color.web("#ff0055"));
            }

            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 60));
            String msg = opponentGameOver ? "VICTORY!" : "GAME OVER";
            gc.fillText(msg, (CANVAS_WIDTH - 300) / 2, 400);
        }
    }

    void drawHold(int sx, int sy) {
        // HOLD Box (Left)
        gc.setFill(Color.web("#1e1e2e", 0.8));
        gc.fillRoundRect(sx, sy, 80, 100, 15, 15);
        gc.setStroke(Color.web("#33334d"));
        gc.strokeRoundRect(sx, sy, 80, 100, 15, 15);

        gc.setFill(Color.web("#00d2ff"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        gc.fillText("HOLD", sx + 15, sy + 25);

        if (holdPiece != null) {
            drawMini(holdPiece, sx + 10, sy + 40, 15);
        }
    }

    void drawMini(Tetromino p, int sx, int sy, int tileSize) {
        int[][] shape = p.shape;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape.length; c++) {
                if (shape[r][c] != 0) {
                    gc.setFill(p.type.color);
                    gc.fillRoundRect(sx + c * tileSize, sy + r * tileSize, tileSize - 1, tileSize - 1, 4, 4);
                }
            }
        }
    }

    void drawTextPopups(int offsetX) {
        // Remove inactive popups
        textPopups.removeIf(popup -> !popup.isActive);

        // Draw active popups
        for (TextPopup popup : textPopups) {
            double opacity = popup.getOpacity();
            if (opacity > 0) {
                gc.setFont(Font.font("Arial", FontWeight.BOLD, 30));
                Color color = popup.color;
                gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
                gc.fillText(popup.text, offsetX + 50, popup.getCurrentY());
            }
        }
    }

    void createLoginScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #0f0c29, #302b63, #24243e);");

        Label title = new Label("TETRIS LOGIN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
        title.setTextFill(Color.WHITE);

        javafx.scene.control.TextField tfNick = new javafx.scene.control.TextField();
        tfNick.setPromptText("Nickname");
        tfNick.setMaxWidth(300);

        javafx.scene.control.PasswordField pfPass = new javafx.scene.control.PasswordField();
        pfPass.setPromptText("Password");
        pfPass.setMaxWidth(300);

        Button btnLogin = createStyledButton("LOGIN");
        btnLogin.setOnAction(e -> {
            if (db.login(tfNick.getText(), pfPass.getText())) {
                this.nickname = tfNick.getText();
                db.loadSettingsToManager(nickname, SettingsManager.getInstance());
                GameSettings.refresh();
                input.refreshConfig();
                soundManager.setSoundEnabled(GameSettings.isSoundEnabled());

                primaryStage.setScene(menuScene);
                primaryStage.setTitle("Tetris Ultimate - " + nickname);
            } else {
                tfNick.setStyle("-fx-border-color: red;");
            }
        });

        Button btnToRegister = createStyledButton("GO TO REGISTER");
        btnToRegister.setOnAction(e -> {
            if (registerScene == null)
                createRegisterScene();
            primaryStage.setScene(registerScene);
        });

        javafx.scene.control.TextField tfServer = new javafx.scene.control.TextField(serverIp);
        tfServer.setPromptText("Server IP (default: localhost)");
        tfServer.setMaxWidth(300);
        tfServer.textProperty().addListener((obs, oldV, newV) -> serverIp = newV);

        root.getChildren().addAll(title, tfNick, pfPass, btnLogin, new Label("Server Settings"), tfServer,
                btnToRegister);
        loginScene = new Scene(root, 500, 600);
    }

    void createRegisterScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #0f0c29, #302b63, #24243e);");

        Label title = new Label("TETRIS REGISTER");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
        title.setTextFill(Color.WHITE);

        javafx.scene.control.TextField tfNick = new javafx.scene.control.TextField();
        tfNick.setPromptText("Nickname");
        tfNick.setMaxWidth(300);

        javafx.scene.control.PasswordField pfPass = new javafx.scene.control.PasswordField();
        pfPass.setPromptText("Password");
        pfPass.setMaxWidth(300);

        Button btnReg = createStyledButton("REGISTER");
        btnReg.setOnAction(e -> {
            if (db.register(tfNick.getText(), pfPass.getText())) {
                primaryStage.setScene(loginScene);
            } else {
                tfNick.setStyle("-fx-border-color: red;");
            }
        });

        Button btnBack = createStyledButton("BACK TO LOGIN");
        btnBack.setOnAction(e -> primaryStage.setScene(loginScene));

        root.getChildren().addAll(title, tfNick, pfPass, btnReg, btnBack);
        registerScene = new Scene(root, 500, 600);
    }

    void createLobbyScene() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("MULTIPLAYER LOBBY");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 30));
        title.setTextFill(Color.web("#00d2ff"));

        String localIp = getLocalIp();
        Label lblIp = new Label("Your Local IP (Sharing): " + localIp);
        lblIp.setTextFill(Color.LIGHTGRAY);
        lblIp.setFont(Font.font("Segoe UI", 14));

        lblConnectionStatus = new Label("Status: Checking...");
        lblConnectionStatus.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        updateConnectionStatus(false);

        // Search Bar
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER);
        javafx.scene.control.TextField tfSearch = new javafx.scene.control.TextField();
        tfSearch.setPromptText("Search Room Name...");
        tfSearch.setPrefWidth(300);
        Button btnSearch = new Button("SEARCH");
        searchBar.getChildren().addAll(tfSearch, btnSearch);

        // Room Table
        roomTable = new TableView<>(roomList);
        roomTable.setPrefHeight(300);
        VBox.setVgrow(roomTable, javafx.scene.layout.Priority.ALWAYS);
        roomTable.setStyle("-fx-background-color: #16213e; -fx-control-inner-background: #16213e;");

        TableColumn<RoomInfo, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        colName.setPrefWidth(180);

        TableColumn<RoomInfo, String> colPass = new TableColumn<>("Locked");
        colPass.setCellValueFactory(
                d -> new javafx.beans.property.SimpleStringProperty(d.getValue().hasPass ? "🔒" : "🔓"));
        colPass.setPrefWidth(80);

        TableColumn<RoomInfo, String> colCap = new TableColumn<>("Players");
        colCap.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().players + "/2"));
        colCap.setPrefWidth(100);

        roomTable.getColumns().addAll(colName, colPass, colCap);

        FilteredList<RoomInfo> filteredData = new FilteredList<>(roomList,
                p -> true);
        tfSearch.textProperty().addListener((obs, oldV, newV) -> {
            filteredData.setPredicate(room -> {
                if (newV == null || newV.isEmpty())
                    return true;
                return room.name.toLowerCase().contains(newV.toLowerCase());
            });
        });
        roomTable.setItems(filteredData);

        // Buttons
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER);
        Button btnRefresh = createStyledButton("REFRESH");
        btnRefresh.setOnAction(e -> {
            System.out.println("[CLIENT] Requesting Room List...");
            netClient.requestRoomList();
        });

        Button btnJoin = createStyledButton("JOIN SELECTED");
        btnJoin.setOnAction(e -> {
            RoomInfo sel = roomTable.getSelectionModel().getSelectedItem();
            if (sel != null)
                joinRoomWithVerify(sel);
        });

        Button btnCreate = createStyledButton("CREATE ROOM");
        btnCreate.setOnAction(e -> createRoomDialog());

        Button btnBack = createStyledButton("BACK");
        btnBack.setOnAction(e -> primaryStage.setScene(menuScene));

        controls.getChildren().addAll(btnRefresh, btnJoin, btnCreate, btnBack);

        root.getChildren().addAll(title, lblStatusInfo(), lblIp, lblConnectionStatus, searchBar, roomTable, controls);
        lobbyScene = new Scene(root, 650, 750);

        // Auto-refresh every 3 seconds
        javafx.animation.Timeline lobbyTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), ev -> {
                    if (primaryStage.getScene() == lobbyScene && netClient != null && netClient.isConnected()) {
                        netClient.requestRoomList();
                    }
                }));
        lobbyTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        lobbyTimer.play();
    }

    private Node lblStatusInfo() {
        Label l = new Label("(To join others, change Server IP at Login screen)");
        l.setTextFill(Color.GRAY);
        l.setFont(Font.font("Segoe UI", 12));
        return l;
    }

    void joinRoomWithVerify(RoomInfo room) {
        if (room.hasPass) {
            TextInputDialog tid = new TextInputDialog();
            tid.setTitle("Password Required");
            tid.setHeaderText("Enter password for: " + room.name);
            tid.showAndWait().ifPresent(pass -> {
                netClient.joinRoom(room.id, pass);
            });
        } else {
            netClient.joinRoom(room.id, "");
        }
    }

    void createRoomDialog() {
        VBox dialogRoot = new VBox(15);
        dialogRoot.setAlignment(Pos.CENTER);
        dialogRoot.setPadding(new javafx.geometry.Insets(20));

        javafx.scene.control.TextField tfName = new javafx.scene.control.TextField(nickname + "'s Room");
        javafx.scene.control.PasswordField pfPass = new javafx.scene.control.PasswordField();
        pfPass.setPromptText("Password (Optional)");

        Button btnOk = new Button("CREATE");
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setScene(new Scene(dialogRoot));

        btnOk.setOnAction(e -> {
            // In WebSocket mode, we just send the request to the currently connected
            // server.
            // No need to start a local server if we are already connected to one (Local or
            // Render).
            if (netClient != null && netClient.isConnected()) {
                SettingsManager sm = SettingsManager.getInstance();
                netClient.createRoom(tfName.getText(), pfPass.getText(), sm.getBoardWidth(), sm.getBoardHeight());
                dialogStage.close();
            } else {
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Not Connected");
                    alert.setHeaderText("You must be connected to a server to create a room.");
                    alert.showAndWait();
                });
            }
        });

        dialogRoot.getChildren().addAll(new Label("Room Name:"), tfName, new Label("Password:"), pfPass, btnOk);
        dialogStage.show();
    }

    // Model for Room
    public static class RoomInfo {
        String id, name, players;
        boolean hasPass;

        public RoomInfo(String id, String name, boolean hasPass, String players) {
            this.id = id;
            this.name = name;
            this.hasPass = hasPass;
            this.players = players;
        }
    }

    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}