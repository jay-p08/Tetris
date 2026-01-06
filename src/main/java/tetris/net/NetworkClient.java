package tetris.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

import javafx.application.Platform;

public class NetworkClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenThread;

    // Callbacks
    public Consumer<String> onMessage;

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        listenThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    Platform.runLater(() -> {
                        if (onMessage != null) {
                            onMessage.accept(finalLine);
                        }
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (onMessage != null) {
                        onMessage.accept("DISCONNECTED");
                    }
                });
            }
        }, "NetworkListener");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public synchronized void send(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    public void createRoom() {
        send("CREATE_ROOM");
    }

    public void joinRoom(String roomId) {
        send("JOIN_ROOM:" + roomId);
    }

    public void sendState(String boardString) {
        send("STATE:" + boardString);
    }

    public void sendAttack(int lines) {
        send("ATTACK:" + lines);
    }

    public void sendGameOver() {
        send("GAME_OVER");
    }

    public void disconnect() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
