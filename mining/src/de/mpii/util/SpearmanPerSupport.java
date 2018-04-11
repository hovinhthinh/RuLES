package de.mpii.util;

import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/**
 * Created by hovinhthinh on 3/30/18.
 */
public class SpearmanPerSupport {
    static class Stat {
        int sup;
        double prediction_quality;
        double econf, conf;

        public Stat(int sup, double prediction_quality, double econf, double conf) {
            this.sup = sup;
            this.prediction_quality = prediction_quality;
            this.econf = econf;
            this.conf = conf;
        }
    }

    // <workspace> <computed stat file> <ew>
    static KnowledgeGraph knowledgeGraph;

    public static void main(String[] args) {
//        args = "../data/wiki44k/ ../data/wiki44k/xyz.input.small_sup.stat 0.3".split("\\s++");
        double ew = Double.parseDouble(args[2]);
        ArrayList<String> lines = IO.readlines(args[1]);
        Infer.knowledgeGraph = knowledgeGraph = new KnowledgeGraph(args[0]);

        ArrayList<Stat> stats = new ArrayList<>();

        for (String line : lines) {
            String[] arr = line.split("\t");
            double conf = Double.parseDouble(arr[2]);
            double mrr = Double.parseDouble(arr[4]);
            double econf = conf * (1 - ew) + mrr * ew;

            if (conf < 0.1) {
                continue;
            }

            String rule = arr[0];
            Rule r = Infer.parseRule(knowledgeGraph, rule);
            System.out.println("Inferring rule: " + rule);
            HashSet<SOInstance> instances = Infer.matchRule(r, true);
            int pid = r.atoms.get(0).pid;
            int localNumTrue = 0;
            int localPredict = 0;
            int support = 0;

            for (SOInstance so : instances) {
                if (!knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                    ++localPredict;
                    if (knowledgeGraph.idealFacts.containFact(so.subject, pid, so.object)) {
                        ++localNumTrue;
                    }
                } else {
                    ++support;
                }
            }
            double quality = ((double) localNumTrue) / localPredict;
            stats.add(new Stat(support, quality, econf, conf));
        }

        Collections.sort(stats, new Comparator<Stat>() {
            @Override
            public int compare(Stat o1, Stat o2) {
                return Integer.compare(o1.sup, o2.sup);
            }
        });
        ArrayList<Pair<Double, Double>> conf = new ArrayList<>(), econf = new ArrayList<>();
        System.out.println("max_ew\tconf\teconf\tdiff");
        int cur = -1;
        for (int l = 1; l <= 10; ++l) {
            conf.clear();
            econf.clear();
            while (cur + 1 < stats.size() && stats.get(cur + 1).sup <= l) {
                ++cur;
                conf.add(new Pair<>(stats.get(cur).prediction_quality, stats.get(cur).conf));
                econf.add(new Pair<>(stats.get(cur).prediction_quality, stats.get(cur).econf));
            }
            double confS = Spearman.getSpearman(conf), econfS = Spearman.getSpearman(econf), diff = econfS - confS;
            System.out.printf("%d\t%.3f\t%.3f\t%.3f\n", l, confS, econfS, diff);
        }
    }
}
