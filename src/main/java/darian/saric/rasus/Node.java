package darian.saric.rasus;

import darian.saric.rasus.network.EmulatedSystemClock;
import darian.saric.rasus.network.SimpleSimulatedDatagramSocket;
import darian.saric.rasus.service.ServerThread;
import darian.saric.rasus.util.ScalarTimestamp;
import darian.saric.rasus.util.TimeClockDecorator;
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
    //todo: popravak greške da se izgubi točno jedno mjerenje vektorskim oznakama
    //todo: threshold za broj retransmisija
    public static final String RECEIVED_SIGNAL = "received";
    public static final int BUFFER_SIZE = 256; // received bytes
    /**
     * {@link Path} do podataka o mjerenjima
     */
    private static final Path DATA_PATH = Paths.get("src/main/resources/mjerenja.csv");
    private static final Path NETWORK_CONFIG_PATH = Paths.get("src/main/resources/network.config");
    private static final long MEASUREMENT_TIME_THRESHOLD_MILIS = 20000;
    private static final long GENERATE_MEASUREMENT_INTERVAL_SECONDS = 5;
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
    //    private ExecutorService pool;
    private List<Integer> measurements = new CopyOnWriteArrayList<>();
    private Map<ScalarTimestamp, Integer> scalarTimestamps = new ConcurrentHashMap<>();
    private Map<VectorTimestamp, Integer> vectorTimestamps = new ConcurrentHashMap<>();
    private int nodeNumber;
    private int nodeIndex = 0;
    private VectorTimestamp lastTimestamp;
    private AtomicInteger eventCount = new AtomicInteger();
    private TimeClockDecorator systemClock = new TimeClockDecorator(new EmulatedSystemClock());
    private int port;
    private double lossRate;
    private int averageDelay;
    private ScheduledExecutorService service = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() / 2);
    private Timer calcTimer = new Timer(true);
//    private ServerThread serverThread = new ServerThread(this);

    private Node(String name) throws IOException {
        configureNode(name);
        lastTimestamp = new VectorTimestamp(new int[nodeNumber]);
//        pool = Executors.newFixedThreadPool(neighbourPackets.size());
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
        Scanner scanner = new Scanner(System.in);
        boolean c = true;
        while (c) {
            switch (scanner.nextLine().toLowerCase()) {
                case "end":
                    c = false;
                    break;
                case "start":
                    node.startup();
                    break;
            }
        }

        scanner.close();
        node.shutdown();
    }

    private void shutdown() {
        service.shutdown();
        calcTimer.cancel();
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

    private void startup() {
        Thread t = new Thread(new ServerThread(this));
        t.setDaemon(true);
        t.start();
        service.scheduleAtFixedRate(new MeasureTask(),
                GENERATE_MEASUREMENT_INTERVAL_SECONDS,
                GENERATE_MEASUREMENT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        calcTimer.scheduleAtFixedRate(new ProccessTask(),
                MEASUREMENT_TIME_THRESHOLD_MILIS,
                MEASUREMENT_TIME_THRESHOLD_MILIS);
    }

    private void storeMeasurement(int i) {
        if (addMeasurement(i)) {
            lastTimestamp.setVectorParameter(nodeIndex, eventCount.incrementAndGet());
            scalarTimestamps.put(new ScalarTimestamp(systemClock.currentTimeMillis()), i);
            vectorTimestamps.put(lastTimestamp, i);
            System.out.println("-------------------POHRANA----------------------------------------------");
            System.out.println("generirano mjerenje: " + i + " u " + lastTimestamp.getVector());
            System.out.println("mapa: " + vectorTimestamps.values());
            System.out.println("-------------------POHRANA----------------------------------------------");
        }
    }

    private boolean addMeasurement(int m) {
        return measurements.add(m);
    }

    public synchronized void storeMeasurement(int m, long scalar, int[] vector) {
        if (addMeasurement(m)) {
            vector[nodeIndex] = eventCount.incrementAndGet();
            lastTimestamp = new VectorTimestamp(vector);
            scalarTimestamps.put(new ScalarTimestamp(scalar), m);
            vectorTimestamps.put(lastTimestamp, m);
            System.out.println("-------------------POHRANA----------------------------------------------");
            System.out.println("primljeno mjerenje: " + m + " u " + lastTimestamp.getVector());
            System.out.println("mapa: " + vectorTimestamps.values());
            System.out.println("-------------------POHRANA----------------------------------------------");
        }
    }

    public int getNodeNumber() {
        return nodeNumber;
    }

//    public int getNodeIndex() {
//        return nodeIndex;
//    }
//
//    public void noteEvent() {
//        eventCount.incrementAndGet();
//    }
//
//    private int getEventCount() {
//        return eventCount.get();
//    }

    public long getSystemTime() {
        return systemClock.currentTimeMillis();
    }

    public void setOffset(long l) {
        systemClock.setOffset(l);
    }

    private class ProccessTask extends TimerTask {

        @Override
        public void run() {
//            if(port == 10001) {
//                return;
//            }
            System.out.println("Obrađujem podatke...");
            double average = measurements
//                    .subList(measurements.size() - 5, measurements.size())
                    .stream()
                    .mapToInt(Integer::intValue).average().orElse(0);

            Map<ScalarTimestamp, Integer> map1 = new TreeMap<>(scalarTimestamps);
            Map<VectorTimestamp, Integer> map2 = new TreeMap<>(vectorTimestamps);
            synchronized (this) {
                scalarTimestamps.clear();
                vectorTimestamps.clear();
                measurements.clear();
            }

            System.out.println("Prosječno mjerenje za protekli period: " + average);
            System.out.println("Redoslijed mjerenja skalarnim oznakama: " + map1.values());
            System.out.println("Redoslijed mjerenja vektorskim oznakama: " + map2.values());


        }

//        private synchronized void processData(List<Integer> measurements, Map<ScalarTimestamp, Measurement>)
    }

    private class MeasureTask implements Runnable {
        private List<Future<Void>> results = new ArrayList<>(neighbourPackets.size());
        private ExecutorService pool = Executors.newFixedThreadPool(neighbourPackets.size());

        @Override
        public void run() {
//            if(port == 10001) {
//                return;
//            }
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
            object.put("scalartimestamp", systemClock.currentTimeMillis());
            lastTimestamp.setVectorParameter(nodeIndex, eventCount.incrementAndGet());
            object.put("vectortimestamp", lastTimestamp.getVector().toArray());
            ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
            b.putInt(measurement);
            packet.setData(object.toString().getBytes());
            while (!sendingComplete) {
                System.out.println("------------------SLANJE-" + packet.getPort() + "----------------------------------------------");
                socket.send(packet);
                System.out.println("poslan paket portu: " + packet.getPort() + ": " + object);
                try {
                    DatagramPacket rcvPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
                    socket.receive(rcvPacket);
                    String reception = new String(rcvPacket.getData(), rcvPacket.getOffset(), rcvPacket.getLength());

                    if (reception.toLowerCase().equals(RECEIVED_SIGNAL)) {
                        sendingComplete = true;
                        System.out.println("primljena potvrda");
                    }
                } catch (SocketTimeoutException ignored) {
                    System.out.println("Nije primljena potvrda, šaljem ponovno");
                }

            }
            System.out.println("------------------SLANJE-" + packet.getPort() + "----------------------------------------------");
            return null;
        }
    }
}
