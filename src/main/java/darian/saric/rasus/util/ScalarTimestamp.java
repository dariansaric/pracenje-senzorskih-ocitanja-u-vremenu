package darian.saric.rasus.util;

public class ScalarTimestamp implements Comparable<ScalarTimestamp> {
    private int timestamp;

    public ScalarTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(ScalarTimestamp o) {
        return Integer.compare(timestamp, o.timestamp);
    }
}
