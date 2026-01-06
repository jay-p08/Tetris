package tetris;

import javafx.scene.paint.Color;

public enum TetrominoType {
    I(Color.CYAN, 4),
    J(Color.BLUE, 3),
    L(Color.ORANGE, 3),
    O(Color.YELLOW, 2),
    S(Color.GREEN, 3),
    T(Color.MAGENTA, 3),
    Z(Color.RED, 3);

    public final Color color;
    public final int dimension; // N x N size

    TetrominoType(Color color, int dimension) {
        this.color = color;
        this.dimension = dimension;
    }
}
