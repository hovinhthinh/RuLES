package de.mpii.util;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.embedding.HolEClient;
import de.mpii.embedding.SSPClient;
import de.mpii.embedding.TransEClient;
import de.mpii.mining.Miner;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/27/17.
 */

// Get input from AMIE for simplicity.
public class ComputeStats {
    public static class Runner implements Runnable {
        EmbeddingClient client;
        BlockingQueue<Pair<Rule, String>> queue;
        PrintWriter out;

        public Runner(EmbeddingClient client, BlockingQueue<Pair<Rule, String>> queue, PrintWriter out) {
            this.client = client;
            this.queue = queue;
            this.out = out;
        }

        @Override
        public void run() {
            for (; ; ) {
                Pair<Rule, String> front = null;
                try {
                    front = queue.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (front == null) {
                    return;
                }
                Rule r = front.first;
                HashSet<SOInstance> instances = Infer.matchRule(r);
                int pid = r.atoms.get(0).pid;
                double mrr = 0;
                int sup = 0;
                int pcaBodySup = 0;
                HashSet<Integer> goodS = new HashSet<>();
                ArrayList<SOInstance> unknownFacts = new ArrayList<>();
                for (SOInstance so : instances) {
                    if (!knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                        unknownFacts.add(so);
                    } else {
                        ++sup;
                        goodS.add(so.subject);
                    }
                }
                for (SOInstance so : instances) {
                    if (goodS.contains(so.subject)) {
                        ++pcaBodySup;
                    }
                }
                if (unknownFacts.size() == 0 || instances.size() == 0) {
                    continue;
                }
                unknownFacts = Miner.samplingSOHeadInstances(unknownFacts);
                for (SOInstance so : unknownFacts) {
                    mrr += client.getInvertedRank(so.subject, pid, so.object);
                }
                mrr /= unknownFacts.size();
                double conf = ((double) sup) / instances.size();
                double pcaconf = pcaBodySup == 0 ? 0 : ((double) sup) / pcaBodySup;
                double conv = (1 - knowledgeGraph.rSupport[pid]) / (1 - conf);
                double hc = knowledgeGraph.pidSOInstances[pid].size() == 0 ? 0 : ((double) sup) / knowledgeGraph.pidSOInstances[pid].size();
                synchronized (out) {
                    out.printf("%s\t%d\t%.9f\t%.9f\t%.9f\t%.9f\t%.9f\n", front.second, sup, conf, pcaconf, mrr, conv, hc);
                    out.flush();
                }
            }
        }
    }

    public static final Logger LOGGER = Logger.getLogger(ComputeStats.class.getName());

    public static KnowledgeGraph knowledgeGraph;

    // args: <workspace> <client> <file> <out>
    public static void main(String[] args) throws Exception {
//        args = "../data/imdb transe ../data/imdb/amie.txt.conf tmp".split("\\s++");

        EmbeddingClient embeddingClient;
        if (args[1].equalsIgnoreCase("transe")) {
            embeddingClient = new TransEClient(args[0], "L1");
        } else if (args[1].equalsIgnoreCase("hole")) {
            embeddingClient = new HolEClient(args[0]);
        } else if (args[1].equalsIgnoreCase("ssp")) {
            embeddingClient = new SSPClient(args[0]);
        } else {
            throw new RuntimeException("Invalid embedding model");
        }

        Infer.knowledgeGraph = knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[2])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]))));
        String line;
        int ruleCount = 0;

        BlockingQueue<Pair<Rule, String>> queue = new LinkedBlockingQueue<Pair<Rule, String>>();
        while ((line = in.readLine()) != null) {
            ++ruleCount;
            if (line.isEmpty()) {
                break;
            }
            String arr[] = line.split("\t");
            String rule = arr[0];
            LOGGER.info("Loading rule: " + rule);
            Rule r = Infer.parseRule(knowledgeGraph, rule);
            queue.add(new Pair<>(r, rule));
        }
        in.close();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 50; ++i) {
            futures.add(executor.submit(new Runner(embeddingClient, queue, out)));
        }
        try {
            for (Future f : futures) {
                f.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        executor.shutdown();

        out.close();
    }
}
