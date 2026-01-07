package tetris.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javafx.application.Platform;

public class NetworkClient {

    private WebSocket webSocket;
    private final HttpClient client;

    // Callbacks
    public Consumer<String> onMessage;

    public NetworkClient() {
        this.client = HttpClient.newHttpClient();
    }

    public void connect(String serverUri) {
        client.newWebSocketBuilder()
                .buildAsync(URI.create(serverUri), new WebSocketListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    System.out.println("[WS] WebSocket object assigned.");
                })
                .exceptionally(ex -> {
                    System.err.println("[WS] Connection future failed: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }

    private class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("[WS] Connected to server");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String completeMessage = messageBuffer.toString();
                messageBuffer.setLength(0);
                Platform.runLater(() -> {
                    if (onMessage != null) {
                        onMessage.accept(completeMessage);
                    }
                });
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[WS] Disconnected: " + reason);
            Platform.runLater(() -> {
                if (onMessage != null) {
                    onMessage.accept("DISCONNECTED");
                }
            });
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[WS] Error: " + error.getMessage());
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }

    public synchronized void send(String msg) {
        if (webSocket != null) {
            webSocket.sendText(msg, true);
        }
    }

    public void createRoom(String name, String password, int width, int height) {
        send("CREATE_ROOM:" + name + "," + password + "," + width + "," + height);
    }

    public void joinRoom(String roomId, String password) {
        send("JOIN_ROOM:" + roomId + "," + password);
    }

    public void requestRoomList() {
        send("LIST_ROOMS");
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

    public boolean isConnected() {
        return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Manual disconnect");
        }
    }
}
