const WebSocket = require('ws');
const http = require('http');

const port = process.env.PORT || 9999;

// Render/Heroku/Railway require an HTTP server to respond to health checks
const server = http.createServer((req, res) => {
    res.writeHead(200);
    res.end("Tetris Server is Running");
});

const wss = new WebSocket.Server({ server });

console.log(`Tetris WebSocket Server started on port ${port}`);

server.listen(port, () => {
    console.log(`HTTP Server listening on port ${port}`);
});

const clients = new Set();
const rooms = new Map(); // roomId -> { name, password, width, height, player1, player2 }

wss.on('connection', (ws) => {
    console.log("New connection");
    clients.add(ws);
    ws.currentRoomId = null;

    ws.on('message', (message) => {
        const msg = message.toString();
        console.log("Received: " + msg);
        handleMessage(ws, msg);
    });

    ws.on('close', () => {
        console.log("Connection closed");
        clients.delete(ws);
        if (ws.currentRoomId) {
            const room = rooms.get(ws.currentRoomId);
            if (room) {
                if (room.player1 === ws) room.player1 = null;
                else if (room.player2 === ws) room.player2 = null;

                if (!room.player1 && !room.player2) {
                    rooms.delete(ws.currentRoomId);
                    console.log("Room deleted: " + ws.currentRoomId);
                } else {
                    const opponent = room.player1 || room.player2;
                    if (opponent) {
                        opponent.send("OPPONENT_DISCONNECTED");
                        opponent.send("GAME_OVER");
                    }
                }
                broadcastRoomList();
            }
        }
    });
});

function handleMessage(ws, msg) {
    const parts = msg.split(":");
    const type = parts[0];
    const payload = parts.slice(1).join(":");

    switch (type) {
        case "CREATE_ROOM":
            const [name, pass, width, height] = payload.split(",");
            const rid = (rooms.size + 1).toString();
            const newRoom = { id: rid, name, password: pass, width: parseInt(width), height: parseInt(height), player1: ws, player2: null };
            rooms.set(rid, newRoom);
            ws.currentRoomId = rid;
            ws.send("ROOM_CREATED:" + rid);
            broadcastRoomList();
            break;

        case "JOIN_ROOM":
            const [jid, jpass] = payload.split(",");
            const room = rooms.get(jid);
            if (room) {
                if (room.player2) {
                    ws.send("JOIN_FAILED:Room is full");
                } else if (room.password && room.password !== jpass) {
                    ws.send("JOIN_FAILED:Wrong password");
                } else {
                    room.player2 = ws;
                    ws.currentRoomId = jid;
                    ws.send("JOIN_SUCCESS:" + jid);

                    // Notify both players
                    const p1 = room.player1;
                    const p2 = room.player2;
                    p1.send("OPPONENT_JOINED:Player2");
                    p2.send("OPPONENT_JOINED:Player1");

                    // Start Game with board dimensions
                    const startMsg = `GAME_START:${room.width},${room.height}`;
                    p1.send(startMsg);
                    p2.send(startMsg);

                    broadcastRoomList();
                }
            } else {
                ws.send("JOIN_FAILED:Room not found");
            }
            break;

        case "LIST_ROOMS":
            ws.send(getRoomListMsg());
            break;

        case "STATE":
            broadcastToOpponent(ws, "OPPONENT_STATE:" + payload);
            break;

        case "ATTACK":
            broadcastToOpponent(ws, "GARBAGE:" + payload);
            break;

        case "GAME_OVER":
            broadcastToOpponent(ws, "OPPONENT_GAME_OVER");
            break;
    }
}

function broadcastToOpponent(ws, msg) {
    if (ws.currentRoomId) {
        const room = rooms.get(ws.currentRoomId);
        if (room) {
            const opponent = (room.player1 === ws) ? room.player2 : room.player1;
            if (opponent) {
                opponent.send(msg);
            }
        }
    }
}

function getRoomListMsg() {
    let sb = "ROOM_LIST:";
    for (const [id, gr] of rooms.entries()) {
        if (!gr.player1 || !gr.player2) {
            const hasPass = gr.password ? "true" : "false";
            const players = (gr.player1 ? 1 : 0) + (gr.player2 ? 1 : 0);
            sb += `${id}|${gr.name}|${hasPass}|${players};`;
        }
    }
    return sb;
}

function broadcastRoomList() {
    const msg = getRoomListMsg();
    for (const client of clients) {
        client.send(msg);
    }
}