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
        public String rule;
        public double pcaconf;
        public double conf;
        public double mmr;
        public double quality;
        public double conv;

        public RuleStats(String rule, double pcaconf, double conf, double mmr, double quality, double conv) {
            this.rule = rule;
            this.pcaconf = pcaconf;
            this.conf = conf;
            this.mmr = mmr;
            this.quality = quality;
            this.conv = conv;
        }

        public double chosenMetric;
    }

    static class CompareF implements Comparator<RuleStats> {
        double lambda;

        public CompareF(double lambda) {
            this.lambda = lambda;
        }

        @Override
        public int compare(RuleStats o1, RuleStats o2) {
            return Double.compare(o2.chosenMetric * (1 - lambda) + o2.mmr * lambda, o1.chosenMetric * (1 - lambda) + o1.mmr * lambda);
        }
    }

    public static void process(String outputFile, double[] weights, ArrayList<RuleStats> stats, int range) throws Exception {
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile))));

        double[][] econf = new double[weights.length][stats.size() / range];
        
        for (int k = 0; k < weights.length; ++k) {
            Collections.shuffle(stats);
            Collections.sort(stats, new CompareF(weights[k]));
            double avg = 0;
            for (int i = 0; i < stats.size(); ++i) {
                avg += stats.get(i).quality;
                if (i % range == range - 1) {
                    econf[k][i / range] = avg / (i + 1);
                }
            }

        }
        out.print("ew");
        for (double w : weights) {
            out.printf("\t%.3f", w);
        }
        out.print("\n");
        for (int k = 0; k < stats.size() / range; ++k) {
            out.printf("top_%d", (k + 1) * range);
            for (int i = 0; i < weights.length; ++i) {
                out.printf("\t%.3f", econf[i][k]);
            }
            out.print("\n");
        }
        out.close();
    }

    // args: <workspace> <file> <range> <output>
    public static void main(String[] args) throws Exception {
//        args = "../data/fb15k-new/ ../data/fb15k-new/amie.xyz.hole.sp10 10 ../data/fb15k-new/amie.xyz.hole.sp10.txt"
//                .split
//                ("\\s++");
        Infer.knowledgeGraph = knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
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
                    .parseDouble(arr[4]), quality, Double.parseDouble(arr[5])));
        }
        in.close();
        //
        double[] weights = new double[11];
        for (int i = 0; i < 11; ++i) {
            weights[i] = 0.1f * i;
        }
        for (RuleStats s : stats) {
            s.chosenMetric = s.conf;
        }
        process(args[3] + ".conf.txt", weights, stats, range);
        for (RuleStats s : stats) {
            s.chosenMetric = s.pcaconf;
        }
        process(args[3] + ".pca.txt", weights, stats, range);
        //
//        weights = new double[]{0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.925, 0.95, 0.975, 1};
        for (RuleStats s : stats) {
            s.chosenMetric = s.conv;
        }
        process(args[3] + ".conv.txt", weights, stats, range);
    }
}
