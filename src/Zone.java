public class Zone {
    private int id;
    private int startX, startY;
    private int endX, endY;

    public Zone(int id, int startX, int startY, int endX, int endY) {
        this.id = id;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public int getId() {
        return id;
    }

    public int getCenterX() {
        return (startX + endX) / 2;
    }

    public int getCenterY() {
        return (startY + endY) / 2;
    }

    @Override
    public String toString() {
        return "Zone " + id + ": (" + startX + "," + startY + ") to (" + endX + "," + endY + ")";
    }
}