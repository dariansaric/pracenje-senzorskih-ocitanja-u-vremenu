package darian.saric.rasus.service;

import darian.saric.rasus.Main;
import darian.saric.rasus.network.SimpleSimulatedDatagramSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerThread implements Runnable {
    private static final int BUFFER_SIZE = 256; // received bytes
    byte[] sendBuf = new byte[256];// sent bytes
    private Main main;
    private ExecutorService threadPool = Executors.newFixedThreadPool
            (Runtime.getRuntime().availableProcessors() - 1);
    private int port;
    private boolean active = true;


    public void setMain(Main main) {
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
        try (DatagramSocket datagramSocket = new SimpleSimulatedDatagramSocket(port, 0.2, 200)) {
//            datagramSocket.bind(new InetSocketAddress("localhost", port));
            while (active) {
                byte[] rcvBuf = new byte[256]; // received bytes
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


        public ClientWorker(DatagramSocket socket, DatagramPacket packet) {
            this.socket = socket;
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                //TODO: parsiranje json objekta
                JSONObject json = new JSONObject(new String(packet.getData(), packet.getOffset(),
                        packet.getLength()));
                main.noteEvent();
                // encode a String into a sequence of bytes using the platform's
                // default charset
//                sendBuf = rcvStr.toUpperCase().getBytes();
//                System.out.println("Server sends: " + rcvStr.toUpperCase());

                // create a DatagramPacket for sending packets
                DatagramPacket sendPacket = new DatagramPacket("received".getBytes(),
                        sendBuf.length, packet.getAddress(), packet.getPort());
                // send packet
                socket.send(sendPacket); //SENDTO
                storeMeasurement(json);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void storeMeasurement(JSONObject json) {
            int co = json.getInt("co");
            // TODO: sinkronizacija skalarnih oznaka vremena uz clock
            int scalarTimestamp = json.getInt("scalartimestamp");
            int[] vector = new int[main.getNodeNumber()];
            JSONArray array = json.getJSONArray("vectortimestamp");

//                if(array.length() != main.getNodeNumber()) {
            // TODO: neka poruka da je poslan neispravan vektor
//                }
            for (int i = 0; i < array.length(); i++) {
                vector[i] = array.getInt(i);
            }
            vector[main.getNodeIndex()] = main.getEventCount();

            main.storeMeasurement(co, scalarTimestamp, vector);
        }
    }
}
