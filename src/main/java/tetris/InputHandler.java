package tetris;

public class InputHandler {
    public boolean left, right, down, up;
    public long leftTimer = 0;
    public long rightTimer = 0;

    // Config
    public long das = GameSettings.DAS;
    public long arr = GameSettings.ARR;

    public void refreshConfig() {
        this.das = GameSettings.getDas();
        this.arr = GameSettings.getArr();
    }

    public void pressLeft(long time) {
        left = true;
        leftTimer = time;
    }

    public void releaseLeft() {
        left = false;
        leftTimer = 0;
    }

    public void pressRight(long time) {
        right = true;
        rightTimer = time;
    }

    public void releaseRight() {
        right = false;
        rightTimer = 0;
    }
}
