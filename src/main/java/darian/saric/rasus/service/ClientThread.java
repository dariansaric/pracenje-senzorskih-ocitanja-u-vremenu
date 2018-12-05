package darian.saric.rasus.service;

import darian.saric.rasus.Node;

import java.util.LinkedList;
import java.util.List;

public class ClientThread implements Runnable {
    //TODO: implementacija klijenta (pošiljatelja)
    private Node main;
    private List<Integer> portList = new LinkedList<>();
    private boolean active = true;

    public void setMain(Node main) {
        this.main = main;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void shutdown() {
        active = false;
    }

    @Override
    public void run() {

    }
}
