package darian.saric.rasus.util;

import java.util.Objects;

public class ScalarTimestamp implements Comparable<ScalarTimestamp> {
    private long timestamp;

    public ScalarTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(ScalarTimestamp o) {
        return Long.compare(timestamp, o.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScalarTimestamp that = (ScalarTimestamp) o;
        return timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }
}
