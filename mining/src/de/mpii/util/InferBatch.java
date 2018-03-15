package de.mpii.util;

import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/27/17.
 */
public class InferBatch {
    public static final Logger LOGGER = Logger.getLogger(InferBatch.class.getName());

    public static KnowledgeGraph knowledgeGraph;


    static class RuleStats {
        String rule;
        public double pcaconf;
        public double conf;
        public double mmr;
        public double quality;

        public RuleStats(String rule, double pcaconf, double conf, double mmr, double quality) {
            this.rule = rule;
            this.pcaconf = pcaconf;
            this.conf = conf;
            this.mmr = mmr;
            this.quality = quality;
        }
    }

    static class CompareF implements Comparator<RuleStats> {
        double lambda;

        public CompareF(double lambda) {
            this.lambda = lambda;
        }

        @Override
        public int compare(RuleStats o1, RuleStats o2) {
            return Double.compare(o2.conf * (1 - lambda) + o2.mmr * lambda, o1.conf * (1 - lambda) + o1.mmr * lambda);
        }
    }

    // args: <workspace> <file> <range> <output>
    public static void main(String[] args) throws Exception {
//        args = "../data/fb15k-new/ ../data/fb15k-new/amie.xyz.hole.sp10 10 ../data/fb15k-new/amie.xyz.hole.sp10.txt"
//                .split
//                ("\\s++");
        knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]))));
        String line;
        ArrayList<RuleStats> stats = new ArrayList<>();
        int range = Integer.parseInt(args[2]);
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            String[] arr = line.split("\t");
            Rule r = Infer.parseRule(knowledgeGraph, arr[0]);
            LOGGER.info("Inferring rule: " + arr[0]);
            HashSet<SOInstance> instances = Infer.matchRule(r);
            int pid = r.atoms.get(0).pid;
            int localNumTrue = 0;
            int localPredict = 0;

            for (SOInstance so : instances) {
                if (!knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                    ++localPredict;
                    boolean unknown = !knowledgeGraph.idealFacts.containFact(so.subject, pid, so.object);
                    if (!unknown) {
                        ++localNumTrue;
                    }
                }
            }
            double quality = ((double) localNumTrue) / localPredict;
            stats.add(new RuleStats(arr[0], Double.parseDouble(arr[3]), Double.parseDouble(arr[2]), Double
                    .parseDouble(arr[4]), quality));
        }
        in.close();
        //
        double[] pca = new double[stats.size() / range];
        double[][] econf = new double[11][stats.size() / range];

        Collections.shuffle(stats);
        Collections.sort(stats, new Comparator<RuleStats>() {
            @Override
            public int compare(RuleStats o1, RuleStats o2) {
                return Double.compare(o2.pcaconf, o1.pcaconf);
            }
        });
        double avg = 0;
        for (int i = 0; i < stats.size(); ++i) {
            avg += stats.get(i).quality;
            if (i % range == range - 1) {
                pca[i / range] = avg / (i + 1);
            }
        }
        for (int k = 0; k < 11; ++k) {
            Collections.sort(stats, new CompareF(k * 0.1));
            avg = 0;
            for (int i = 0; i < stats.size(); ++i) {
                avg += stats.get(i).quality;
                if (i % range == range - 1) {
                    econf[k][i / range] = avg / (i + 1);
                }
            }
        }

        out.print("ew\t0\t0.1\t0.2\t0.3\t0.4\t0.5\t0.6\t0.7\t0" +
                ".8\t0.9\t1.0\tpcaconf\n");
        for (int k = 0; k < stats.size() / range; ++k) {
            out.printf("top_%d\t", (k + 1) * range);
            for (int i = 0; i < 11; ++i) {
                out.printf("%.3f\t", econf[i][k]);
            }
            out.printf("%.3f\n", pca[k]);
        }
        out.close();
    }
}
