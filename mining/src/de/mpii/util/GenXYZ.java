package de.mpii.util;

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
 * Created by hovinhthinh on 3/25/18.
 */
public class GenXYZ {
    public static final Logger LOGGER = Logger.getLogger(GenXYZ.class.getName());
    public static KnowledgeGraph knowledgeGraph;

    // args: <workspace> <file> <out>
    public static void main(String[] args) throws Exception {
        Infer.knowledgeGraph = knowledgeGraph = new KnowledgeGraph(args[0]);
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[2]))));
        String line;

        BlockingQueue<Pair<Rule, String>> queue = new LinkedBlockingQueue<Pair<Rule, String>>();
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            String arr[] = line.split("\t");
            String rule = arr[0];
            Rule r = Infer.parseRule(knowledgeGraph, rule);
            queue.add(new Pair<>(r, rule));
        }
        in.close();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            futures.add(executor.submit(new GenXYZ.Runner(queue, out)));
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

    public static class Runner implements Runnable {
        BlockingQueue<Pair<Rule, String>> queue;
        PrintWriter out;

        public Runner(BlockingQueue<Pair<Rule, String>> queue, PrintWriter out) {
            this.queue = queue;
            this.out = out;
        }

        @Override
        public void run() {
            for (; ; ) {
                Pair<Rule, String> front = null;
                try {
                    front = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (front == null) {
                    return;
                }
                Rule r = front.first;
                HashSet<SOInstance> instances = Infer.matchRule(r, true);
                int pid = r.atoms.get(0).pid;
                int sup = 0;
                int pcaBodySup = 0;
                HashSet<Integer> goodS = new HashSet<>();
                for (SOInstance so : instances) {
                    if (knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                        ++sup;
                        goodS.add(so.subject);
                    }
                }
                for (SOInstance so : instances) {
                    if (goodS.contains(so.subject)) {
                        ++pcaBodySup;
                    }
                }
                if (sup == instances.size() || instances.size() == 0) {
                    continue;
                }
                double conf = ((double) sup) / instances.size();
                double pcaconf = pcaBodySup == 0 ? 0 : ((double) sup) / pcaBodySup;
                double hc = knowledgeGraph.pidSOInstances[pid].size() == 0 ? 0 : ((double) sup) / knowledgeGraph.pidSOInstances[pid].size();
                synchronized (out) {
                    out.printf("%s\t%d\t%.9f\t%.9f\t%.9f\n", front.second, sup, conf, pcaconf, hc);
                    out.flush();
                }
            }
        }
    }
}
