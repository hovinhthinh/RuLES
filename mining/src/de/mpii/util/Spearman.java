package de.mpii.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by hovinhthinh on 3/30/18.
 */
public class Spearman {
    private static class Triple {
        double first, second;
        int rank;

        public Triple(Pair<Double, Double> p) {
            first = p.first;
            second = p.second;
        }
    }

    // first: gold standard, second: value
    public static double get(ArrayList<Pair<Double, Double>> values) {
        ArrayList<Triple> t = new ArrayList<>();
        for (Pair<Double, Double> p : values) {
            t.add(new Triple(p));
        }
        Collections.sort(t, new Comparator<Triple>() {
            @Override
            public int compare(Triple o1, Triple o2) {
                return Double.compare(o1.first, o2.first);
            }
        });
        for (int i = 0; i < t.size(); ++i) {
            t.get(i).rank = i + 1;
        }
        Collections.sort(t, new Comparator<Triple>() {
            @Override
            public int compare(Triple o1, Triple o2) {
                return Double.compare(o1.second, o2.second);
            }
        });
        double spearman = 0;
        for (int i = 0; i < t.size(); ++i) {
            spearman += Math.pow((double) i + 1 - t.get(i).rank, 2);
        }
        return 1 - 6 * spearman / t.size() / ((double) t.size() * t.size() - 1);
    }
}
