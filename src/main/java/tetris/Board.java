package tetris;

public class Board {
    public final int width;
    public final int height;
    public int[][] grid;

    public Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new int[height][width];
    }

    public boolean canMove(Tetromino piece, int nextX, int nextY) {
        int[][] shape = piece.shape;
        int n = shape.length;

        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                if (shape[row][col] != 0) {
                    int boardX = nextX + col;
                    int boardY = nextY + row;

                    // Bounds Check
                    if (boardX < 0 || boardX >= width || boardY >= height) {
                        return false;
                    }
                    // Collision Check (only if inside board from top)
                    // Note: boardY < 0 is allowed for spawning above sky
                    if (boardY >= 0 && grid[boardY][boardX] != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void lock(Tetromino piece) {
        int[][] shape = piece.shape;
        int n = shape.length;
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                if (shape[row][col] != 0) {
                    int boardY = piece.y + row;
                    int boardX = piece.x + col;
                    if (boardY >= 0 && boardY < height && boardX >= 0 && boardX < width) {
                        // For simplicity, using '1' or valid color index?
                        // Using '1' for now, or could use 'type.ordinal() + 1'
                        grid[boardY][boardX] = 1;
                    }
                }
            }
        }
    }

    public int clearLines() {
        int cleared = 0;
        for (int y = height - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                for (int ty = y; ty > 0; ty--) {
                    System.arraycopy(grid[ty - 1], 0, grid[ty], 0, width);
                }
                grid[0] = new int[width];
                y++;
            }
        }
        return cleared;
    }
}
