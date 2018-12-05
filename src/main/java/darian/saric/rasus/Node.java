package darian.saric.rasus;

import darian.saric.rasus.network.EmulatedSystemClock;
import darian.saric.rasus.util.ScalarTimestamp;
import darian.saric.rasus.util.VectorTimestamp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

public class Node {
    public static final int BUFFER_SIZE = 256; // received bytes
    private static final List<Integer> MEASUREMENTS = fillMeasurements();
    private static final long MEASUREMENT_TIME_THRESHOLD_MILIS = 5000;
    private static final long GENERATE_MEASUREMENT_INTERVAL_MILIS = 1000;
    private Set<Integer> measurements = new CopyOnWriteArraySet<>();
    private Map<ScalarTimestamp, Integer> scalarTimestamps = new ConcurrentHashMap<>();
    private Map<VectorTimestamp, Integer> vectorTimestamps = new ConcurrentHashMap<>();
    private int nodeNumber;
    private int nodeIndex = 0;
    private VectorTimestamp lastTimestamp = new VectorTimestamp(0, 0, 0);
    private AtomicInteger eventCount = new AtomicInteger();
    private EmulatedSystemClock systemClock = new EmulatedSystemClock();

    private static List<Integer> fillMeasurements() {
        return new ArrayList<>();
    }

    public static void main(String[] args) {
        Node node = new Node();
        node.storeMeasurement(1);
        node.startTimers();

    }

    private void startTimers() {
        Timer calcTimer = new Timer(true);
        calcTimer.scheduleAtFixedRate(new ProccessTask(), 0, MEASUREMENT_TIME_THRESHOLD_MILIS);
        Timer measureTimer = new Timer(true);
        measureTimer.scheduleAtFixedRate(new MeasureTask(), 0, GENERATE_MEASUREMENT_INTERVAL_MILIS);
    }

    private void storeMeasurement(int i) {
        if (addMeasurement(i)) {
            lastTimestamp.setVectorParameter(nodeIndex, eventCount.incrementAndGet());
//            scalarTimestamps.put(new ScalarTimestamp(eventCount.get()), i);
            vectorTimestamps.put(lastTimestamp, i);
        }
    }


    private boolean addMeasurement(int m) {
        return measurements.add(m);
    }

    public synchronized void storeMeasurement(int m, int scalar, int[] vector) {
        if (addMeasurement(m)) {
            vector[nodeIndex] = eventCount.incrementAndGet();
            lastTimestamp = new VectorTimestamp(vector);
//            scalarTimestamps.put(new ScalarTimestamp(scalar), m);
            vectorTimestamps.put(lastTimestamp, m);
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
        eventCount.incrementAndGet();
    }

    private int getEventCount() {
        return eventCount.get();
    }

    public long getSystemTime() {
        return systemClock.currentTimeMillis();
    }

    private class ProccessTask extends TimerTask {

        @Override
        public void run() {
            System.out.println("Obrađujem podatke...");
            double average = measurements.stream()
                    .mapToInt(Integer::intValue).average().orElse(0);

            Map<ScalarTimestamp, Integer> map1 = new TreeMap<>(scalarTimestamps);
            Map<VectorTimestamp, Integer> map2 = new TreeMap<>(vectorTimestamps);
            synchronized (this) {
                scalarTimestamps.clear();
                vectorTimestamps.clear();
                measurements.clear();
            }

            //TODO: ispis mjerenja i prosjeka
            System.out.println("Prosječno mjerenje za protekli period: " + average);
            System.out.println("Redoslijed mjerenja skalarnim oznakama: " + map1);
            System.out.println("Redoslijed mjerenja vektorskim oznakama: " + map2);


        }

//        private synchronized void processData(List<Integer> measurements, Map<ScalarTimestamp, Measurement>)
    }

    private class MeasureTask extends TimerTask {

        @Override
        public void run() {
            storeMeasurement(MEASUREMENTS.get(Math.toIntExact((getSystemTime() % 100) + 2)));
            //TODO: svima pošalji generirano mjerenje
        }
    }
}
