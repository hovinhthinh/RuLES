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
        double rank;

        public Triple(Pair<Double, Double> p) {
            first = p.first;
            second = p.second;
        }
    }

    public static double getPearson(ArrayList<Pair<Double, Double>> values) {
        double mux = 0, muy = 0;
        for (Pair<Double, Double> p : values) {
            mux += p.first / values.size();
            muy += p.second / values.size();
        }
        double sx = 0, sy = 0, cov = 0;
        for (Pair<Double, Double> p : values) {
            sx += Math.pow(p.first - mux, 2) / values.size();
            sy += Math.pow(p.second - muy, 2) / values.size();
            cov += (p.first - mux) * (p.second - muy) / values.size();
        }
        sx = Math.sqrt(sx);
        sy = Math.sqrt(sy);
        return cov / sx / sy;
    }

    // first: gold standard, second: value
    public static double getSpearman(ArrayList<Pair<Double, Double>> values) {
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
            int j = i;
            while (j < t.size() - 1 && Math.abs(t.get(j + 1).first - t.get(j).first) < 1e-6) {
                ++j;
            }
            double rank = (i + j + 2.0f) / 2;
            for (int k = i; k <= j; ++k) {
                t.get(k).rank = rank;
            }
            i = j;
        }
        Collections.sort(t, new Comparator<Triple>() {
            @Override
            public int compare(Triple o1, Triple o2) {
                return Double.compare(o1.second, o2.second);
            }
        });
        ArrayList<Pair<Double, Double>> ranks = new ArrayList<>();
        for (int i = 0; i < t.size(); ++i) {
            int j = i;
            while (j < t.size() - 1 && Math.abs(t.get(j + 1).second - t.get(j).second) < 1e-6) {
                ++j;
            }
            double rank = (i + j + 2.0f) / 2;
            for (int k = i; k <= j; ++k) {
                ranks.add(new Pair<>(t.get(k).rank, rank));
            }
            i = j;
        }
        return getPearson(ranks);
    }
}
