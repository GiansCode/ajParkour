package us.ajg0702.parkour.top;

public record TopEntry(int position, String name, int score, int time) {

    public static TopEntry empty(int position) {
        return new TopEntry(position, "--", -1, -1);
    }

    public boolean isEmpty() {
        return name.equals("--");
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public int getTime() {
        return time;
    }
}
