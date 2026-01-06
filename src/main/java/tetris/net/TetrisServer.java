package tetris.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TetrisServer {

    private static final int PORT = 9999;
    private static Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private static Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private static int roomIdCounter = 1;

    public static void main(String[] args) {
        System.out.println("Tetris Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler client = new ClientHandler(socket);
                clients.add(client);
                new Thread(client).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized String createRoom(ClientHandler host) {
        String roomId = String.valueOf(roomIdCounter++);
        GameRoom room = new GameRoom(roomId, host);
        rooms.put(roomId, room);

        host.room = room;
        host.opponent = null;

        return roomId;
    }

    public static synchronized boolean joinRoom(String roomId, ClientHandler joiner) {
        GameRoom room = rooms.get(roomId);
        if (room != null && !room.isFull()) {
            room.addPlayer(joiner);
            return true;
        }
        return false;
    }

    public static synchronized void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    // --- Inner Classes ---
    static class GameRoom {

        String id;
        ClientHandler player1;
        ClientHandler player2;

        public GameRoom(String id, ClientHandler player1) {
            this.id = id;
            this.player1 = player1;
        }

        public boolean isFull() {
            return player2 != null;
        }

        public void addPlayer(ClientHandler player2) {
            this.player2 = player2;
            // Notify both
            player1.sendMessage("OPPONENT_JOINED");
            player2.sendMessage("JOIN_SUCCESS:" + id); // Tell P2 they joined
            player2.sendMessage("GAME_START");
            player1.sendMessage("GAME_START");
            // Set opponents
            player1.opponent = player2;
            player2.opponent = player1;

            player1.room = this;
            player2.room = this;
        }

        public void broadcast(String msg, ClientHandler sender) {
            if (player1 != null && player1 != sender) {
                player1.sendMessage(msg);
            }
            if (player2 != null && player2 != sender) {
                player2.sendMessage(msg);
            }
        }
    }

    static class ClientHandler implements Runnable {

        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        GameRoom room;
        ClientHandler opponent;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected");
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String msg) {
            System.out.println("Received: " + msg);
            String[] parts = msg.split(":", 2);
            String type = parts[0];
            String payload = parts.length > 1 ? parts[1] : "";

            switch (type) {
                case "CREATE_ROOM":
                    String roomId = TetrisServer.createRoom(this);
                    sendMessage("ROOM_CREATED:" + roomId);
                    break;
                case "JOIN_ROOM":
                    boolean success = TetrisServer.joinRoom(payload, this);
                    if (!success) {
                        sendMessage("JOIN_FAIL");
                    }
                    break;
                case "STATE":
                    // Forward board state to opponent
                    if (opponent != null) {
                        opponent.sendMessage("OPPONENT_STATE:" + payload);
                    }
                    break;
                case "ATTACK":
                    // Forward garbage to opponent
                    if (opponent != null) {
                        opponent.sendMessage("GARBAGE:" + payload);
                    }
                    break;
                case "GAME_OVER":
                    if (opponent != null) {
                        opponent.sendMessage("OPPONENT_GAME_OVER");
                    }
                    break;
            }
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }

        private void cleanup() {
            try {
                socket.close();
            } catch (IOException e) {
            }
            clients.remove(this);
            if (room != null) {
                if (opponent != null) {
                    opponent.sendMessage("OPPONENT_DISCONNECTED");
                    opponent.room = null;
                    opponent.opponent = null;
                }
                TetrisServer.removeRoom(room.id);
            }
        }
    }
}
