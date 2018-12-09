package darian.saric.rasus.util;

public class ScalarTimestamp implements Comparable<ScalarTimestamp> {
    private long timestamp;

    public ScalarTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(ScalarTimestamp o) {
        return Long.compare(timestamp, o.timestamp);
    }
}
