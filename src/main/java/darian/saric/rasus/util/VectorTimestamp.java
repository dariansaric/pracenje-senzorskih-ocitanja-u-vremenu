package darian.saric.rasus.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class VectorTimestamp implements Comparable<VectorTimestamp> {
    private List<Integer> vector = new LinkedList<>();


    public VectorTimestamp(int... args) {
        Arrays.stream(args).forEach(vector::add);
    }

    public List<Integer> getVector() {
        return new ArrayList<>(vector);
    }

    public void setVectorParameter(int index, int value) {
        vector.set(index, value);
    }

    public void incrementVectorParameter(int index) {
        vector.set(index, vector.get(index) + 1);
    }
    @Override
    public int compareTo(VectorTimestamp o) {
        if (o.vector.size() != vector.size()) {
            throw new IllegalArgumentException("Neodgovarajući tip timestamp");
        }

        for (int i = 0; i < vector.size(); i++) {
            int n = Integer.compare(vector.get(i), o.vector.get(i));
            if (n != 0) {
                return n;
            }
        }

        return 0;
    }
}
