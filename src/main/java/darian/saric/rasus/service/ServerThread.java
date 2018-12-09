package darian.saric.rasus.service;

import darian.saric.rasus.Node;
import darian.saric.rasus.network.SimpleSimulatedDatagramSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static darian.saric.rasus.Node.BUFFER_SIZE;

public class ServerThread implements Runnable {
    private static final byte[] RECEIVED_SIGNAL_BYTES = Node.RECEIVED_SIGNAL.getBytes();
    private Node main;
    private ExecutorService threadPool = Executors.newFixedThreadPool
            (Runtime.getRuntime().availableProcessors() / 2);
    private int port;
    private boolean active = true;

    public ServerThread(Node main) {
        this.main = main;
        setPort(main.getPort());
    }

    public void setMain(Node main) {
        this.main = main;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void shutdown() {
        active = false;
        threadPool.shutdown();
    }

    @Override
    public void run() {
        try (DatagramSocket datagramSocket = new SimpleSimulatedDatagramSocket(
                port, main.getLossRate(), main.getAverageDelay())) {

//            datagramSocket.bind(new InetSocketAddress("localhost", port));
            while (active) {
                byte[] rcvBuf = new byte[BUFFER_SIZE]; // received bytes
                DatagramPacket packet = new DatagramPacket(rcvBuf, rcvBuf.length);
                datagramSocket.receive(packet);
                threadPool.submit(new ClientWorker(datagramSocket, packet));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientWorker implements Runnable {
        private DatagramSocket socket;
        private DatagramPacket packet;
//        private BufferedReader reader;
//        private PrintWriter writer;


        ClientWorker(DatagramSocket socket, DatagramPacket packet) {
            this.socket = socket;
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                JSONObject json = new JSONObject(new String(packet.getData(), packet.getOffset(),
                        packet.getLength()));
//                main.noteEvent();
                // encode a String into a sequence of bytes using the platform's
                // default charset
//                sendBuf = rcvStr.toUpperCase().getBytes();
//                System.out.println("Server sends: " + rcvStr.toUpperCase());

                // create a DatagramPacket for sending packets
                DatagramPacket sendPacket = new DatagramPacket(RECEIVED_SIGNAL_BYTES,
                        RECEIVED_SIGNAL_BYTES.length, packet.getAddress(), packet.getPort());
                // send packet
                socket.send(sendPacket); //SENDTO
//                System.out.println("----------------------PRIMANJE-" + packet.getPort() + "-------------------");
                storeMeasurement(json);
//                System.out.println("----------------------PRIMANJE-" + packet.getPort() + "-------------------");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void storeMeasurement(JSONObject json) {
            int co = json.getInt("co");
            long scalarTimestamp = synchronizeTimestamp(json.getLong("scalartimestamp"));
            int[] vector = new int[main.getNodeNumber()];
            JSONArray array = json.getJSONArray("vectortimestamp");

            if (array.length() != main.getNodeNumber()) {
                System.out.println("Poslana vektorska oznaka vremena je neispravnih dimenzija");
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                vector[i] = array.getInt(i);
            }
//            vector[main.getNodeIndex()] = main.getEventCount();

            main.storeMeasurement(co, scalarTimestamp, vector);


        }

        private synchronized long synchronizeTimestamp(long scalartimestamp) {
            long ts = main.getSystemTime();
            if (scalartimestamp > ts) {
                main.setOffset(scalartimestamp - ts + 1);
            }
            return scalartimestamp + 1;
        }
    }
}
