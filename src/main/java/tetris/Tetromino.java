package tetris;

public class Tetromino {
    public TetrominoType type;
    public int[][] shape; // The N x N grid
    public int x, y; // Top-left position on board
    public int rotationState; // 0, 1, 2, 3
    public boolean lastMoveWasRotate;

    public Tetromino(TetrominoType type) {
        this.type = type;
        this.rotationState = 0;
        this.shape = getInitialShape(type);
    }

    // SRS Standard Initial Orientations (Flat side down usually)
    private int[][] getInitialShape(TetrominoType type) {
        switch (type) {
            case I:
                // 4x4, row 1 (index 1) filled
                return new int[][] {
                        { 0, 0, 0, 0 },
                        { 1, 1, 1, 1 },
                        { 0, 0, 0, 0 },
                        { 0, 0, 0, 0 }
                };
            case J:
                return new int[][] {
                        { 1, 0, 0 },
                        { 1, 1, 1 },
                        { 0, 0, 0 }
                };
            case L:
                return new int[][] {
                        { 0, 0, 1 },
                        { 1, 1, 1 },
                        { 0, 0, 0 }
                };
            case O:
                return new int[][] {
                        { 1, 1 },
                        { 1, 1 }
                };
            case S:
                return new int[][] {
                        { 0, 1, 1 },
                        { 1, 1, 0 },
                        { 0, 0, 0 }
                };
            case T:
                return new int[][] {
                        { 0, 1, 0 },
                        { 1, 1, 1 },
                        { 0, 0, 0 }
                };
            case Z:
                return new int[][] {
                        { 1, 1, 0 },
                        { 0, 1, 1 },
                        { 0, 0, 0 }
                };
            default:
                return new int[][] { {} };
        }
    }

    public int[][] getRotatedShape(boolean clockwise) {
        int n = shape.length;
        int[][] newShape = new int[n][n];

        // General N x N rotation
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (clockwise) {
                    newShape[c][n - 1 - r] = shape[r][c];
                } else {
                    newShape[n - 1 - c][r] = shape[r][c];
                }
            }
        }
        return newShape;
    }
}
