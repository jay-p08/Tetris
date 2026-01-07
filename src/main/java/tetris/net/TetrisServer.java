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
    private static boolean running = false;
    private static ServerSocket serverSocket;

    public static void startAsync() {
        if (running)
            return;
        Thread t = new Thread(() -> {
            try {
                running = true;
                System.out.println("Tetris Internal Server started on port " + PORT);
                serverSocket = new ServerSocket(PORT);
                while (running) {
                    Socket socket = serverSocket.accept();
                    System.out.println("[SERVER] New Connection from: " + socket.getRemoteSocketAddress());
                    ClientHandler client = new ClientHandler(socket);
                    clients.add(client);
                    new Thread(client).start();
                }
            } catch (java.net.BindException e) {
                System.out.println("Tetris Server is already running on this machine (Port 9999).");
            } catch (IOException e) {
                if (running)
                    e.printStackTrace();
            } finally {
                stop();
            }
        }, "InternalServerThread");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        running = false;
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
        }
    }

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

    public static synchronized String createRoom(ClientHandler host, int width, int height) {
        String roomId = String.valueOf(roomIdCounter++);
        GameRoom room = new GameRoom(roomId, "Room " + roomId, "", host, width, height);
        rooms.put(roomId, room);

        host.room = room;
        host.opponent = null;

        return roomId;
    }

    public static synchronized boolean joinRoom(String roomId, String password, ClientHandler joiner) {
        GameRoom room = rooms.get(roomId);
        if (room != null && !room.isFull()) {
            if (room.password != null && !room.password.isEmpty()) {
                if (!room.password.equals(password)) {
                    return false; // Wrong password
                }
            }
            room.addPlayer(joiner);
            return true;
        }
        return false;
    }

    public static synchronized void removeRoom(String roomId) {
        rooms.remove(roomId);
        broadcastRoomList();
    }

    public static void broadcastRoomList() {
        StringBuilder sb = new StringBuilder("ROOM_LIST:");
        for (GameRoom gr : rooms.values()) {
            if (!gr.isFull()) {
                boolean hasPass = gr.password != null && !gr.password.isEmpty();
                sb.append(gr.id).append("|")
                        .append(gr.name).append("|")
                        .append(hasPass).append("|")
                        .append(gr.player2 == null ? 1 : 2).append(";");
            }
        }
        String listMsg = sb.toString();
        for (ClientHandler client : clients) {
            client.sendMessage(listMsg);
        }
    }

    // --- Inner Classes ---
    static class GameRoom {

        String id;
        String name;
        String password;
        ClientHandler player1;
        ClientHandler player2;
        int width;
        int height;

        public GameRoom(String id, String name, String password, ClientHandler player1, int width, int height) {
            this.id = id;
            this.name = name;
            this.password = password;
            this.player1 = player1;
            this.width = width;
            this.height = height;
        }

        public boolean isFull() {
            return player2 != null;
        }

        public void addPlayer(ClientHandler player2) {
            this.player2 = player2;
            // Notify both
            player1.sendMessage("OPPONENT_JOINED");
            player2.sendMessage("JOIN_SUCCESS:" + id); // Tell P2 they joined
            player2.sendMessage("BOARD_SIZE:" + width + ":" + height); // Sync dimensions
            player2.sendMessage("GAME_START");
            player1.sendMessage("GAME_START");
            // Set opponents
            player1.opponent = player2;
            player2.opponent = player1;

            player1.room = this;
            player2.room = this;
            TetrisServer.broadcastRoomList();
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
            System.out.println("[SERVER] Handling: " + msg);
            String[] parts = msg.split(":", 2);
            String type = parts[0];
            String payload = parts.length > 1 ? parts[1] : "";

            switch (type) {
                case "CREATE_ROOM":
                    // Format: CREATE_ROOM:name,password,width,height
                    String rName = "Room";
                    String rPass = "";
                    int rW = 10, rH = 20;

                    if (!payload.isEmpty() && payload.contains(",")) {
                        String[] config = payload.split(",");
                        if (config.length >= 1)
                            rName = config[0];
                        if (config.length >= 2)
                            rPass = config[1];
                        if (config.length >= 3)
                            rW = Integer.parseInt(config[2]);
                        if (config.length >= 4)
                            rH = Integer.parseInt(config[3]);
                    }

                    String rid = String.valueOf(TetrisServer.roomIdCounter++);
                    GameRoom newRoom = new GameRoom(rid, rName, rPass, this, rW, rH);
                    TetrisServer.rooms.put(rid, newRoom);
                    this.room = newRoom;
                    sendMessage("ROOM_CREATED:" + rid);
                    broadcastRoomList();
                    break;
                case "JOIN_ROOM":
                    // Format: JOIN_ROOM:id,password
                    String targetId = payload;
                    String targetPass = "";
                    if (payload.contains(",")) {
                        String[] jparts = payload.split(",");
                        targetId = jparts[0];
                        targetPass = jparts[1];
                    }
                    boolean success = TetrisServer.joinRoom(targetId, targetPass, this);
                    if (!success) {
                        sendMessage("JOIN_FAIL");
                    }
                    break;
                case "LIST_ROOMS":
                    StringBuilder sb = new StringBuilder("ROOM_LIST:");
                    for (GameRoom gr : TetrisServer.rooms.values()) {
                        if (!gr.isFull()) {
                            boolean hasPass = gr.password != null && !gr.password.isEmpty();
                            sb.append(gr.id).append("|")
                                    .append(gr.name).append("|")
                                    .append(hasPass).append("|")
                                    .append(gr.player2 == null ? 1 : 2).append(";");
                        }
                    }
                    System.out.println("[SERVER] Sending Room List: " + sb.toString());
                    sendMessage(sb.toString());
                    break;
                case "STATE":
                    if (opponent != null)
                        opponent.sendMessage("OPPONENT_STATE:" + payload);
                    break;
                case "ATTACK":
                    if (opponent != null)
                        opponent.sendMessage("GARBAGE:" + payload);
                    break;
                case "GAME_OVER":
                    if (opponent != null)
                        opponent.sendMessage("OPPONENT_GAME_OVER");
                    break;
            }
        }

        public void sendMessage(String msg) {
            if (out != null)
                out.println(msg);
        }

        private void cleanup() {
            try {
                if (socket != null)
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
