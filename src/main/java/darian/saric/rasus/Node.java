package darian.saric.rasus;

import darian.saric.rasus.network.EmulatedSystemClock;
import darian.saric.rasus.network.SimpleSimulatedDatagramSocket;
import darian.saric.rasus.util.ScalarTimestamp;
import darian.saric.rasus.util.VectorTimestamp;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Node {
    //todo: podešavanje i pokretanje poslužitelja
    //ToDo: testovi na 2,3 čvora
    public static final String RECEIVED_SIGNAL = "received";
    public static final int BUFFER_SIZE = 256; // received bytes
    /**
     * {@link Path} do podataka o mjerenjima
     */
    private static final Path DATA_PATH = Paths.get("src/main/resources/mjerenja.csv");
    private static final Path NETWORK_CONFIG_PATH = Paths.get("src/main/resources/network.config");
    private static final long MEASUREMENT_TIME_THRESHOLD_MILIS = 5000;
    private static final long GENERATE_MEASUREMENT_INTERVAL_MILIS = 1000;
    private static final int NETWORK_CONFIG_PARAM_COUNT = 4;
    private static InetAddress SYSTEM_HOST_ADDRESS;

    static {
        try {
            SYSTEM_HOST_ADDRESS = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private final List<Integer> MEASUREMENTS = fillMeasurements();
    private List<DatagramPacket> neighbourPackets = new LinkedList<>();
    private ExecutorService pool;
    private Set<Integer> measurements = new CopyOnWriteArraySet<>();
    private Map<ScalarTimestamp, Integer> scalarTimestamps = new ConcurrentHashMap<>();
    private Map<VectorTimestamp, Integer> vectorTimestamps = new ConcurrentHashMap<>();
    private int nodeNumber;
    private int nodeIndex = 0;
    private VectorTimestamp lastTimestamp;
    private AtomicInteger eventCount = new AtomicInteger();
    private EmulatedSystemClock systemClock = new EmulatedSystemClock();
    private int port;
    private double lossRate;
    private int averageDelay;

    private Node(String name) throws IOException {
        configureNode(name);
        lastTimestamp = new VectorTimestamp(new int[nodeNumber]);
        pool = Executors.newFixedThreadPool(neighbourPackets.size());
    }

    private static List<Integer> fillMeasurements() throws IOException {
        final String first = "Temperature,Pressure,Humidity,CO,NO2,SO2,";
        List<Integer> list = new LinkedList<>();

        for (String s : Files.readAllLines(DATA_PATH)) {
            if (s.equals(first)) {
                continue;
            }

            String[] n = s.replaceAll(",", ", ").split(",");
            list.add(n[3].trim().isEmpty() ? null : Integer.parseInt(n[3].trim()));
        }

        return list;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Očekivan jedan argument: naziv čvora");
            return;
        }

        Node node = new Node(args[0]);

//        node.storeMeasurement(1);
        node.startTimers();


        node.pool.shutdown();
    }

    private void configureNode(String name) throws IOException {
        List<String> readAllLines = Files.readAllLines(NETWORK_CONFIG_PATH);
        nodeNumber = readAllLines.size();
        for (int i = 0; i < nodeNumber; i++) {
            String l = readAllLines.get(i);
            String[] tokens = l.split("\\s+");
            if (tokens.length != NETWORK_CONFIG_PARAM_COUNT) {
                throw new IOException("neispravan redak konfiguracijske datoteke" + Arrays.toString(tokens));
            }

            try {
                if (tokens[0].equals(name)) {
                    port = Integer.parseInt(tokens[1]);
                    lossRate = Double.parseDouble(tokens[2]);
                    averageDelay = Integer.parseInt(tokens[3]);
                    nodeIndex = i;
                } else {
                    neighbourPackets.add(
                            new DatagramPacket(
                                    new byte[BUFFER_SIZE], BUFFER_SIZE, SYSTEM_HOST_ADDRESS,
                                    Integer.parseInt(tokens[1]))); // predajem prazan buffer kako bih ga kasnije napunio
                }
            } catch (NumberFormatException e) {
                throw new IOException("neispravan redak konfiguracijske datoteke" + Arrays.toString(tokens), e);
            }
        }
    }

    public int getPort() {
        return port;
    }

    public double getLossRate() {
        return lossRate;
    }

    public int getAverageDelay() {
        return averageDelay;
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

    public int getNodeIndex() {
        return nodeIndex;
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
        private List<Future<Void>> results = new ArrayList<>(neighbourPackets.size());

        @Override
        public void run() {
            int m = MEASUREMENTS.get(Math.toIntExact((getSystemTime() % 100) + 2));
            storeMeasurement(m);

            for (DatagramPacket packet : neighbourPackets) {
                results.add(pool.submit(new SendWork(packet, m)));
            }

            for (Future<Void> r : results) {
                try {
                    r.get();
                } catch (InterruptedException | ExecutionException ignored) {
                }
            }
        }
    }

    private class SendWork implements Callable<Void> {
        private DatagramPacket packet;
        private int measurement;

        private SendWork(DatagramPacket packet, int measurement) {
            this.packet = packet;
            this.measurement = measurement;
        }

        @Override
        public Void call() throws Exception {
            DatagramSocket socket = new SimpleSimulatedDatagramSocket(lossRate, averageDelay);
            boolean sendingComplete = false;
            JSONObject object = new JSONObject();
            object.put("co", measurement);
            object.put("scalartimestamp", nodeIndex);
            lastTimestamp.setVectorParameter(nodeIndex, eventCount.incrementAndGet());
            object.put("vectortimestamp", lastTimestamp.getVector().toArray());
            ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
            b.putInt(measurement);
            packet.setData(object.toString().getBytes());
            while (!sendingComplete) {
                socket.send(packet);
                DatagramPacket rcvPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
                String reception = new String(rcvPacket.getData(), rcvPacket.getOffset(), rcvPacket.getLength());

                if (reception.toLowerCase().equals(RECEIVED_SIGNAL)) {
                    sendingComplete = true;
                }
                try {
                    socket.receive(rcvPacket);
                } catch (SocketTimeoutException ignored) {
                }
            }

            return null;
        }
    }
}
