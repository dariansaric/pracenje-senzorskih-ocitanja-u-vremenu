package darian.saric.rasus;

import darian.saric.rasus.util.ScalarTimestamp;
import darian.saric.rasus.util.VectorTimestamp;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Main {
    private static final long MEASUREMENT_TIME_THRESHOLD_MILIS = 5000;
    private static final long GENERATE_MEASUREMENT_INTERVAL_MILIS = 1000;
    private Set<Integer> measurements = new LinkedHashSet<>();
    private Map<ScalarTimestamp, Integer> scalarTimestamps = new TreeMap<>();
    private Map<VectorTimestamp, Integer> vectorTimestamps = new TreeMap<>();
    private int nodeNumber;
    private int nodeIndex;
    private int eventCount = 0;


    private boolean addMeasurement(int m) {
        return measurements.add(m);
    }

    public void storeMeasurement(int m, int scalar, int[] vector) {
        if (addMeasurement(m)) {
            scalarTimestamps.put(new ScalarTimestamp(scalar), m);
            vectorTimestamps.put(new VectorTimestamp(vector), m);
        }
    }

    public int getNodeNumber() {
        return nodeNumber;
    }

    public void setNodeNumber(int nodeNumber) {
        this.nodeNumber = nodeNumber;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

    public void setNodeIndex(int nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    public void noteEvent() {
        eventCount++;
    }

    public int getEventCount() {
        return eventCount;
    }
}
