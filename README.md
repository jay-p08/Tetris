# Tetris

React frontend and Node WebSocket backend for the Tetris migration.

## Structure

```text
frontend/  React + Vite client for GitHub Pages
backend/   Node.js WebSocket server for multiplayer rooms
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Build for GitHub Pages:

```bash
cd frontend
npm run build
```

## Backend

```bash
cd backend
npm install
npm start
```

The backend listens on `PORT` or `9999` by default.
