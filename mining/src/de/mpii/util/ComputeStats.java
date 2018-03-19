package de.mpii.util;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.embedding.HolEClient;
import de.mpii.embedding.SSPClient;
import de.mpii.embedding.TransEClient;
import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.UnaryAtom;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.RuleStats;
import de.mpii.mining.rule.SOInstance;

import java.io.*;
import java.util.*;
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
                HashSet<SOInstance> instances = matchRule(r);
                int pid = r.atoms.get(0).pid;
                int totalUnknown = 0;
                double mrr = 0;
                int sup = 0;
                int pcaBodySup = 0;
                HashSet<Integer> goodS = new HashSet<>();
                for (SOInstance so : instances) {
                    if (!knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                        totalUnknown++;
                        mrr += client.getInvertedRank(so.subject, pid, so.object);
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
                if (totalUnknown == 0 || instances.size() == 0) {
                    continue;
                }
                mrr /= totalUnknown;
                double conf = ((double) sup) / instances.size();
                double pcaconf = pcaBodySup == 0 ? 0 : ((double) sup) / pcaBodySup;
                double conv = (1 - knowledgeGraph.rSupport[pid]) / (1 - conf);
                synchronized (out) {
                    out.printf("%s\t%d\t%.9f\t%.9f\t%.9f\t%.9f\n", front.second, sup, conf, pcaconf, mrr, conv);
                }
            }
        }
    }

    public static final Logger LOGGER = Logger.getLogger(ComputeStats.class.getName());

    public static KnowledgeGraph knowledgeGraph;

    public static Rule parseRule(KnowledgeGraph graph, String ruleString) {
        Rule r = new Rule(0);
        HashMap<String, Integer> varsMap = new HashMap();
        for (int i = 0; i < ruleString.length(); ++i) {
            int j = i;
            while (ruleString.charAt(j) != ')') {
                ++j;
            }
            String str = ruleString.substring(i, j + 1);
            //


            boolean negated = false;
            if (str.startsWith("not ")) {
                negated = true;
                str = str.substring(4);
            }
            int index = str.indexOf("(");
            String predicate = str.substring(0, index);
            String vars = str.substring(index);
            if (vars.contains(", ")) {
                index = vars.indexOf(", ");
                // Binary
                String subject = vars.substring(1, index);
                if (!varsMap.containsKey(subject)) {
                    varsMap.put(subject, varsMap.size());
                }
                String object = vars.substring(index + 2, vars.length() - 1);
                if (!varsMap.containsKey(object)) {
                    varsMap.put(object, varsMap.size());
                }
                int sid = varsMap.get(subject),
                        pid = graph.relationsStringMap.get(predicate),
                        oid = varsMap.get(object);
                r.atoms.add(new BinaryAtom(false, negated, sid, pid, oid));
            } else {
                // Unary
                String subject = vars.substring(1, vars.length() - 1);
                if (!varsMap.containsKey(subject)) {
                    varsMap.put(subject, varsMap.size());
                }
                int sid = varsMap.get(subject),
                        pid = graph.typesStringMap.get(predicate);
                r.atoms.add(new UnaryAtom(false, negated, sid, pid));
            }
            //
            i = j + 1;
            while (i < ruleString.length() - 1 && ruleString.charAt(i) == ' ') {
                ++i;
            }
            if (i < ruleString.length()) {
                if (ruleString.charAt(i) == ':') {
                    i += 2;
                } else {
                    i += 1;
                }
            }
        }
        r.nVariables = varsMap.size();
        return r;
    }

    private static boolean duplicatedVar(int variableValue[], int newV) {
        for (int i = 0; i < variableValue.length; ++i) {
            if (variableValue[i] == newV) {
                return true;
            }
        }
        return false;
    }

    private static void recur(Rule rule, int position, int variableValues[], HashSet<SOInstance> headInstances) {
        if (position == rule.atoms.size()) {
            headInstances.add(new SOInstance(variableValues[0], variableValues[1]));
            return;
        }
        Atom a = rule.atoms.get(position);
        if (headInstances.size() >= RuleStats.HEAD_INSTANCE_BOUND) {
            return;
        }
        if (a instanceof UnaryAtom) {
            if (variableValues[a.sid] == -1) {
                // This case only happens for positive atom.
                for (int t : knowledgeGraph.typeInstances[a.pid]) {
                    if (duplicatedVar(variableValues, t)) {
                        continue;
                    }
                    variableValues[a.sid] = t;
                    recur(rule, position + 1, variableValues, headInstances);
                }
                variableValues[a.sid] = -1;
            } else {
                boolean hasType = knowledgeGraph.trueTypes.containType(variableValues[a.sid], a.pid);
                if (hasType == a.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, headInstances);
            }
        } else {
            BinaryAtom atom = (BinaryAtom) a;
            if (variableValues[atom.sid] == -1 && variableValues[atom.oid] == -1) {
                for (SOInstance so : knowledgeGraph.pidSOInstances[atom.pid]) {
                    if (duplicatedVar(variableValues, so.subject) || duplicatedVar(variableValues, so.object) || so
                            .subject == so.object) {
                        continue;
                    }
                    variableValues[atom.sid] = so.subject;
                    variableValues[atom.oid] = so.object;
                    recur(rule, position + 1, variableValues, headInstances);
                    if (headInstances.size() >= RuleStats.HEAD_INSTANCE_BOUND) {
                        variableValues[atom.sid] = variableValues[atom.oid] = -1;
                        return;
                    }
                }
                variableValues[atom.sid] = variableValues[atom.oid] = -1;
            } else if (variableValues[atom.sid] == -1 || variableValues[atom.oid] == -1) {
                if (variableValues[atom.oid] == -1) {
                    for (KnowledgeGraph.OutgoingEdge e : knowledgeGraph.outEdges[variableValues[atom.sid]]) {
                        if (e.pid != atom.pid) {
                            continue;
                        }
                        if (duplicatedVar(variableValues, e.oid)) {
                            continue;
                        }
                        variableValues[atom.oid] = e.oid;
                        recur(rule, position + 1, variableValues, headInstances);
                        if (headInstances.size() >= RuleStats.HEAD_INSTANCE_BOUND) {
                            variableValues[atom.oid] = -1;
                            return;
                        }
                    }
                    variableValues[atom.oid] = -1;
                } else {
                    for (KnowledgeGraph.OutgoingEdge e : knowledgeGraph.outEdges[variableValues[atom.oid]]) {
                        if (-e.pid - 1 != atom.pid) {
                            continue;
                        }
                        if (duplicatedVar(variableValues, e.oid)) {
                            continue;
                        }
                        variableValues[atom.sid] = e.oid;
                        recur(rule, position + 1, variableValues, headInstances);
                        if (headInstances.size() >= RuleStats.HEAD_INSTANCE_BOUND) {
                            variableValues[atom.sid] = -1;
                            return;
                        }
                    }
                    variableValues[atom.sid] = -1;
                }
            } else {
                boolean hasFact = knowledgeGraph.trueFacts.containFact(variableValues[atom.sid], atom.pid,
                        variableValues[atom.oid]);
                if (hasFact == atom.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, headInstances);
            }
        }
    }

    public static HashSet<SOInstance> matchRule(Rule r) {
        HashSet<SOInstance> headInstances = new HashSet<>();
        int[] variableValues = new int[r.nVariables];
        Arrays.fill(variableValues, -1);
        recur(r, 1, variableValues, headInstances);
        return headInstances;
    }

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

        knowledgeGraph = new KnowledgeGraph(args[0]);

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
            Rule r = parseRule(knowledgeGraph, rule);
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
