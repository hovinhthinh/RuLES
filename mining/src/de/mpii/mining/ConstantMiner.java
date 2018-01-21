package de.mpii.mining;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.embedding.HolEClient;
import de.mpii.embedding.SSPClient;
import de.mpii.embedding.TransEClient;
import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.InstanceAtom;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.SOInstance;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 1/20/18.
 * ConstantMiner is used for disjunction (with fast implementation).
 */
class ConstantRuleComparator implements Comparator<ConstantRule> {
    @Override
    public int compare(ConstantRule o1, ConstantRule o2) {
        return o1.atoms.size() - o2.atoms.size();
    }
}

class ConstantRuleQueue {
    public static final Logger LOGGER = Logger.getLogger(ConstantRuleQueue.class.getName());

    private static final int OPERATION_LOG_INTERVAL = 100000;

    // Synchronized set.
    private Set<Long> enqueuedRuleCode;

    // Synchronized queue.
    private PriorityBlockingQueue<ConstantRule> rulesQueue;

    private int enqueueLimit;
    private int enqueueCount;
    private int operationCount;

    public ConstantRuleQueue(int enqueueLimit) {
        enqueuedRuleCode = Collections.synchronizedSet(new HashSet<>());
        rulesQueue = new PriorityBlockingQueue<>(11, new ConstantRuleComparator());

        this.enqueueLimit = enqueueLimit;
        enqueueCount = 0;
        operationCount = 0;
    }

    public int size() {
        return rulesQueue.size();
    }

    public boolean isEmpty() {
        return rulesQueue.size() == 0;
    }

    public boolean enqueue(ConstantRule r) {
        if (enqueueCount >= enqueueLimit) {
            return false;
        }
        long code = r.encode();
        if (enqueuedRuleCode.contains(code)) {
            return false;
        }
        enqueuedRuleCode.add(code);
        rulesQueue.add(r);
        ++enqueueCount;
        ++operationCount;
        if (operationCount % OPERATION_LOG_INTERVAL == 0) {
            LOGGER.info("RuleBodyQueueSize: " + rulesQueue.size());
        }
        return true;
    }

    public ConstantRule dequeue() {
        try {
            ++operationCount;
            if (operationCount % OPERATION_LOG_INTERVAL == 0) {
                LOGGER.info("RuleBodyQueueSize: " + rulesQueue.size());
            }
            // Wait for 1 min before returning.
            return rulesQueue.poll(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class ConstantRuleStats {
    static class HeadStats {
        public int pid, value;
        public double hc, conf, mrr, scr;

        // pid < 0 indicates reversed head.
        public HeadStats(int pid, int value, double hc, double conf, double mrr, double scr) {
            this.pid = pid;
            this.value = value;
            this.hc = hc;
            this.conf = conf;
            this.mrr = mrr;
            this.scr = scr;
        }
    }

    // TODO: disable this bound.
    public static final int HEAD_INSTANCE_BOUND = 10000;

    public HashSet<Integer> headInstances;

    public ConstantRuleStats() {
        headInstances = new HashSet<>();
    }

    public HashSet<Long> coupleSet;

    public long encodeCouple(int pid, int value) {
        return ((long) 999983) * pid + value;
    }

    public void simplify(KnowledgeGraph graph, EmbeddingClient embeddingClient, MinerConfig config, ConstantRule
            rule, PrintWriter out) {
        int bodySupport = headInstances.size();
        coupleSet = new HashSet<>();
        List<HeadStats> goodHeads = new ArrayList<>();
        for (int head : headInstances) {
            for (KnowledgeGraph.OutgoingEdge oe : graph.outEdges[head]) {
                long code = encodeCouple(oe.pid, oe.oid);
                if (coupleSet.contains(code)) {
                    continue;
                }
                coupleSet.add(code);

                int ruleSupport = 0, pid = (oe.pid >= 0 ? oe.pid : -1 - oe.pid);
                for (int h : headInstances) {
                    boolean has = oe.pid >= 0 ? graph.trueFacts.containFact(h, pid, oe.oid) :
                            graph.trueFacts.containFact(oe.oid, pid, h);
                    if (has) {
                        ++ruleSupport;
                    }
                }
                double conf = (double) ruleSupport / bodySupport;
                double hc = (double) ruleSupport / graph.pidSOInstances[pid].size();
                if (ruleSupport == bodySupport || conf < config.minConf || ruleSupport < config.minSupport) {
                    continue;
                }

                double scr = conf * (1 - config.embeddingWeight);
                double mrr = -1;
                if (config.embeddingWeight > 0) {
                    mrr = 0;
                    for (int h : headInstances) {
                        if (oe.pid >= 0) {
                            if (!graph.trueFacts.containFact(h, pid, oe.oid)) {
                                mrr += embeddingClient.getInvertedRank(h, pid, oe.oid);
                            }
                        } else {
                            if (!graph.trueFacts.containFact(oe.oid, pid, h)) {
                                mrr += embeddingClient.getInvertedRank(oe.oid, pid, h);
                            }
                        }
                    }
                    mrr /= (bodySupport - ruleSupport);
                    scr += mrr * config.embeddingWeight;
                }
                goodHeads.add(new HeadStats(oe.pid, oe.oid, hc, conf, mrr, scr));
            }
        }
        coupleSet = null;

        // Uncomment for rule without disjunction.
//        for (HeadStats head : goodHeads) {
//            InstanceAtom atom = (InstanceAtom) rule.atoms.get(0);
//            atom.sid = 0;
//            atom.pid = head.pid >= 0 ? head.pid : -head.pid - 1;
//            atom.value = head.value;
//            atom.reversed = head.pid >= 0 ? false : true;
//            System.out.printf("%s\thc:\t%.3f\tconf:\t%.3f\tmrr:\t%.3f\tscr:\t%.3f\n", rule.getString
//                    (graph.relationsString, graph.entitiesString), head.hc, head.conf, head.mrr, head.scr);
//        }
        for (int i = 0; i < goodHeads.size(); ++i) {
            for (int j = i + 1; j < goodHeads.size(); ++j) {
                HeadStats head1 = goodHeads.get(i), head2 = goodHeads.get(j);
                int ruleSupport = 0;

                for (int h : headInstances) {
                    boolean has1 = head1.pid >= 0 ? graph.trueFacts.containFact(h, head1.pid, head1.value) :
                            graph.trueFacts.containFact(head1.value, -1 - head1.pid, h);
                    boolean has2 = head2.pid >= 0 ? graph.trueFacts.containFact(h, head2.pid, head2.value) :
                            graph.trueFacts.containFact(head2.value, -1 - head2.pid, h);
                    if (has1 || has2) {
                        ++ruleSupport;
                    }
                }
                double conf = (double) ruleSupport / headInstances.size();
                if (conf < config.minConf || ruleSupport == headInstances.size() || ruleSupport < config.minSupport) {
                    continue;
                }
                double scr = conf * (1 - config.embeddingWeight);
                double mrr = -1;
                if (config.embeddingWeight > 0) {
                    mrr = 0;
                    for (int h : headInstances) {
                        boolean has1 = head1.pid >= 0 ? graph.trueFacts.containFact(h, head1.pid, head1.value) :
                                graph.trueFacts.containFact(head1.value, -1 - head1.pid, h);
                        boolean has2 = head2.pid >= 0 ? graph.trueFacts.containFact(h, head2.pid, head2.value) :
                                graph.trueFacts.containFact(head2.value, -1 - head2.pid, h);
                        if (has1 || has2) {
                            continue;
                        }
                        double irank1 = head1.pid >= 0 ? embeddingClient.getInvertedRank(h, head1.pid, head1.value) :
                                embeddingClient.getInvertedRank(head1.value, -1 - head1.pid, h);
                        double irank2 = head2.pid >= 0 ? embeddingClient.getInvertedRank(h, head2.pid, head2.value) :
                                embeddingClient.getInvertedRank(head2.value, -1 - head2.pid, h);
                        mrr += Math.max(irank1, irank2);
                    }
                    mrr /= (bodySupport - ruleSupport);
                    scr += mrr * config.embeddingWeight;
                }
                double increaseScr = scr - Math.max(0, Math.max(head1.scr, head2.scr));
                if (increaseScr >= 1e-3) {
                    System.out.printf("%s\tsup:\t%d\tconf:\t%.3f\tmrr:\t%.3f\tscr:\t%.3f\tinc\t%.3f\n", rule
                                    .getDisjunctionString
                                            (head1, head2, graph.relationsString, graph.entitiesString), ruleSupport, conf,
                            mrr, scr, increaseScr);
                    synchronized (out) {
                        out.printf("%s\tsup:\t%d\tconf:\t%.3f\tmrr:\t%.3f\tscr:\t%.3f\tinc\t%.3f\n", rule
                                        .getDisjunctionString
                                                (head1, head2, graph.relationsString, graph.entitiesString), ruleSupport, conf,
                                mrr, scr, increaseScr);

                    }
                }
            }
        }
        headInstances = null;
    }
}


class ConstantRule {
    // Support maximum 1 million entities, relations.
    private static final long E_POW[] = new long[1000000];
    private static final long R_POW[] = new long[1000000];
    private static final long V_POW[] = new long[10];
    private static final long INSTANCE_SIGN = 15485863;
    private static final long REVERSED_SIGN = 999983;

    static {
        E_POW[0] = 1;
        R_POW[0] = 1;
        V_POW[0] = 1;
        for (int i = 1; i < E_POW.length; ++i) {
            E_POW[i] = E_POW[i - 1] * 1000000007;
        }
        for (int i = 1; i < R_POW.length; ++i) {
            R_POW[i] = R_POW[i - 1] * 1000000009;
        }
        for (int i = 1; i < V_POW.length; ++i) {
            V_POW[i] = V_POW[i - 1] * 11;
        }
    }

    public int nVariables;
    // Head variable is 0. There are only 1 or 2 body atoms. In case 2 body atoms, the first atom is binary, the
    // other is instance atom.
    public ArrayList<Atom> atoms;
    // To be filled when matching with knowledge graph.
    public ConstantRuleStats stats;
    public HashSet<Integer> extensionVars;

    public ConstantRule() {
        nVariables = 0;
        atoms = new ArrayList<>();
        stats = null;
        extensionVars = null;
    }

    public ConstantRule cloneRule() {
        ConstantRule r = new ConstantRule();
        r.nVariables = nVariables;
        r.atoms = new ArrayList<>();
        // This fix to prevent different threads from accessing the same first atom when outputting.
        if (atoms.size() > 0) {
            r.atoms.add(new InstanceAtom(true, false, false, 0, -1, -1));
            for (int i = 1; i < atoms.size(); ++i) {
                r.atoms.add(atoms.get(i));
            }
        }
        return r;
    }

    // Add a dangling atom with predicate id. Variable "forward" indicates that the predicate is linked from the old
    // variable or from the new variable. If the rule is empty, the "sid" and "forward" doesn't have effect since the
    // predicate is always linked from variable 0 to variable 1.
    public ConstantRule addDanglingAtom(int sharedVariableId, int pid, boolean forward) {
        ConstantRule r = this.cloneRule();
        if (forward) {
            r.atoms.add(new BinaryAtom(true, false, sharedVariableId, pid, nVariables));
        } else {
            r.atoms.add(new BinaryAtom(true, false, nVariables, pid, sharedVariableId));
        }
        ++r.nVariables;
        return r;
    }

    // Return null if the atom is already added with the same variable and predicate. reversed flag doesn't matter.
    public ConstantRule addInstanceAtom(int sid, int pid, int value, boolean reversed) {
        ConstantRule r = this.cloneRule();
        if (r.nVariables == 0) {
            r.nVariables = 1;
            r.atoms.add(new InstanceAtom(false, false, false, -1, -1, -1));
            return r;
        }
        for (Atom a : r.atoms) {
            if (a instanceof InstanceAtom) {
                InstanceAtom atom = (InstanceAtom) a;
                if (atom.sid == sid && atom.pid == pid && atom.value == value) {
                    return null;
                }
            }
        }
        r.atoms.add(new InstanceAtom(false, false, reversed, sid, pid, value));
        return r;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(encode()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        throw new RuntimeException("Implement this function if using hashtable to track duplicated rules.");
    }

    // nextPermutation from index start to the end.
    private boolean nextPermutation(int[] mapping, int start) {
        for (int i = mapping.length - 2; i >= start; --i) {
            if (mapping[i] < mapping[i + 1]) {
                int j = i + 1;
                while (j + 1 < mapping.length && mapping[j + 1] > mapping[i]) {
                    ++j;
                }
                int k = mapping[i];
                mapping[i] = mapping[j];
                mapping[j] = k;
                for (j = i + 1, k = mapping.length - 1; j < k; ++j, --k) {
                    int l = mapping[j];
                    mapping[j] = mapping[k];
                    mapping[k] = l;
                }
                return true;
            }
        }
        return false;
    }

    // Magic function to encode the rule with a long number.
    // Only work when having at least 1 variables which belong to the head of the rule.
    public long encode() {
        long hashCode = 1;
        int[] mapping = new int[nVariables];
        for (int i = 0; i < nVariables; ++i) {
            mapping[i] = i;
        }
        do {
            long mappingCode = 0;
            for (int i = 0; i < nVariables; ++i) {
                long varCode = 0;
                for (int j = 1; j < atoms.size(); ++j) {
                    Atom a = atoms.get(j);
                    if (mapping[a.sid] != i) {
                        continue;
                    }
                    if (a instanceof InstanceAtom) {
                        InstanceAtom atom = (InstanceAtom) a;
                        varCode += R_POW[atom.pid] * INSTANCE_SIGN * (atom.reversed ? REVERSED_SIGN : 1);
                    } else {
                        BinaryAtom atom = (BinaryAtom) a;
                        varCode += R_POW[atom.pid] * E_POW[mapping[atom.oid]];
                    }
                }
                mappingCode += varCode * V_POW[i];
            }

            hashCode *= mappingCode;
        } while (nextPermutation(mapping, 2));
        return hashCode + nVariables;
    }

    private String getAtomString(Atom a, String[] relationsString, String[] entitiesString) {
        if (a instanceof InstanceAtom) {
            InstanceAtom atom = (InstanceAtom) a;
            StringBuilder sb = new StringBuilder(atom.negated ? "not " : "");
            if (!atom.reversed) {
                sb.append(relationsString[atom.pid]).append("(V").append(atom.sid).append
                        (", %").append
                        (entitiesString[atom.value]).append
                        ("%)");
            } else {
                sb.append(relationsString[atom.pid]).append("(%").append
                        (entitiesString[atom.value]).append
                        ("%, V").append(atom.sid).append
                        (")");
            }
            return sb.toString();
        } else {
            BinaryAtom atom = (BinaryAtom) a;
            StringBuilder sb = new StringBuilder(atom.negated ? "not " : "");
            if (atom.pid < 0) {
                sb.append(relationsString[-1 - atom.pid]).append("(V").append(atom.oid).append
                        (", V").append
                        (atom.sid).append
                        (")");
            } else {
                sb.append(relationsString[atom.pid]).append("(V").append(atom.sid).append
                        (", V").append
                        (atom.oid).append
                        (")");
            }
            return sb.toString();
        }
    }

    public String getString(String[] relationsString, String[] entitiesString) {
        if (atoms.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(getAtomString(atoms.get(0), relationsString, entitiesString)).append(" :- ");
        for (int i = 1; i < atoms.size(); ++i) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(getAtomString(atoms.get(i), relationsString, entitiesString));
        }
        return sb.toString().trim();
    }

    public String getDisjunctionString(ConstantRuleStats.HeadStats head1, ConstantRuleStats.HeadStats head2, String[]
            relationsString, String[] entitiesString) {
        if (atoms.size() == 0) {
            return null;
        }
        InstanceAtom atom = (InstanceAtom) atoms.get(0);
        atom.sid = 0;
        atom.pid = head1.pid >= 0 ? head1.pid : -head1.pid - 1;
        atom.value = head1.value;
        atom.reversed = head1.pid >= 0 ? false : true;
        StringBuilder sb = new StringBuilder(getAtomString(atoms.get(0), relationsString, entitiesString)).append(
                " OR ");

        atom.pid = head2.pid >= 0 ? head2.pid : -head2.pid - 1;
        atom.value = head2.value;
        atom.reversed = head2.pid >= 0 ? false : true;
        sb.append(getAtomString(atoms.get(0), relationsString, entitiesString)).append(" :- ");
        for (int i = 1; i < atoms.size(); ++i) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(getAtomString(atoms.get(i), relationsString, entitiesString));
        }
        return sb.toString().trim();
    }
}

public class ConstantMiner implements Runnable {
    public static final Logger LOGGER = Logger.getLogger(ConstantMiner.class.getName());
    private static final int MATCH_RULE_LOG_INTERVAL = 10000;

    public int matchedRule;

    public EmbeddingClient embeddingClient;
    public KnowledgeGraph knowledgeGraph;
    public ConstantRuleQueue ruleQueue;

    public MinerConfig config;

    public PrintWriter output;

    public ConstantMiner(String workspace, MinerConfig config, PrintWriter output) {
        if (config.embeddingModel.equalsIgnoreCase("transe")) {
            embeddingClient = new TransEClient(workspace, "L1");
        } else if (config.embeddingModel.equalsIgnoreCase("hole")) {
            embeddingClient = new HolEClient(workspace);
        } else if (config.embeddingModel.equalsIgnoreCase("ssp")) {
            embeddingClient = new SSPClient(workspace);
        } else {
            throw new RuntimeException("Invalid embedding model");
        }
        knowledgeGraph = new KnowledgeGraph(workspace);
        ruleQueue = new ConstantRuleQueue(config.enqueueLimit);
        this.config = config;
        this.output = output;

        matchedRule = 0;
    }

    private boolean duplicatedVar(int variableValue[], int newV) {
        for (int i = 0; i < variableValue.length; ++i) {
            if (variableValue[i] == newV) {
                return true;
            }
        }
        return false;
    }

    private void recur(ConstantRule rule, int position, int variableValues[], ConstantRuleStats stats) {
        if (stats.headInstances.size() >= ConstantRuleStats.HEAD_INSTANCE_BOUND) {
            return;
        }
        if (position == rule.atoms.size()) {
            if (rule.atoms.get(rule.atoms.size() - 1) instanceof InstanceAtom) {
                stats.headInstances.add(variableValues[0]);
            }
            rule.extensionVars.add(variableValues[rule.nVariables - 1]);
            return;
        }
        Atom a = rule.atoms.get(position);
        if (a instanceof InstanceAtom) {
            InstanceAtom atom = (InstanceAtom) a;
            if (variableValues[a.sid] == -1) {
//                System.out.println("here " + a.sid);
                int lookingPid = !atom.reversed ? -atom.pid - 1 : atom.pid;
//                System.out.println(atom.value);
                for (KnowledgeGraph.OutgoingEdge oe : knowledgeGraph.outEdges[atom.value]) {
                    if (oe.pid == lookingPid && !duplicatedVar(variableValues, oe.oid)) {
                        variableValues[a.sid] = oe.oid;
                        recur(rule, position + 1, variableValues, stats);
                    }
                }
                variableValues[a.sid] = -1;
            } else {
                boolean hasFact = atom.reversed ? knowledgeGraph.trueFacts.containFact(variableValues[atom.sid], atom.pid, atom.value)
                        : knowledgeGraph.trueFacts.containFact(atom.value, atom.pid, variableValues[atom.sid]);
                if (!hasFact) {
                    return;
                }
                recur(rule, position + 1, variableValues, stats);
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
                    recur(rule, position + 1, variableValues, stats);
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
                        recur(rule, position + 1, variableValues, stats);
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
                        recur(rule, position + 1, variableValues, stats);
                    }
                    variableValues[atom.sid] = -1;
                }
            } else {
                boolean hasFact = knowledgeGraph.trueFacts.containFact(variableValues[atom.sid], atom.pid,
                        variableValues[atom.oid]);
                if (hasFact == atom.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, stats);
            }
        }
    }

    public void matchRule(ConstantRule r) {
        if (r.atoms.size() == 1) {
            return;
        }
        int[] variableValues = new int[r.nVariables];
        Arrays.fill(variableValues, -1);
        ConstantRuleStats stats = new ConstantRuleStats();
        r.extensionVars = new HashSet<>();
        recur(r, 1, variableValues, stats);
        r.stats = stats;

        // Closed rule.
        if (r.atoms.get(r.atoms.size() - 1) instanceof InstanceAtom) {
            r.stats.simplify(knowledgeGraph, embeddingClient, config, r, output);
        }

        ++matchedRule;
        if (matchedRule % MATCH_RULE_LOG_INTERVAL == 0) {
            LOGGER.info("MatchedRuleBodyCount: " + matchedRule);
        }
    }

    public void run() {
        while (true) {
            ConstantRule r = ruleQueue.dequeue();
            if (r == null) {
                break;
            }
            matchRule(r);
            if (r.atoms.size() == 3) {
                continue;
            }
            if (r.atoms.size() == 1) {
                for (int i = 0; i < knowledgeGraph.nEntities; ++i) {
                    for (KnowledgeGraph.OutgoingEdge oe : knowledgeGraph.outEdges[i]) {
                        ConstantRule newR = null;
                        if (oe.pid >= 0) {
                            newR = r.addInstanceAtom(0, oe.pid, oe.oid, false);
                        } else {
                            newR = r.addInstanceAtom(0, -oe.pid - 1, oe.oid, true);
                        }
                        ruleQueue.enqueue(newR);
                    }
                }
                // TODO: Uncomment for better rules (with binary atoms).
//                for (int i = 0; i < knowledgeGraph.nRelations; ++i) {
//                    ConstantRule newR = r.addDanglingAtom(0, i, true);
//                    ruleQueue.enqueue(newR);
//                    newR = r.addDanglingAtom(0, i, false);
//                    ruleQueue.enqueue(newR);
//                }
            } else if (r.atoms.size() == 2 && r.atoms.get(r.atoms.size() - 1) instanceof BinaryAtom) {
//                for (int v : r.extensionVars) {
//                    for (KnowledgeGraph.OutgoingEdge oe : knowledgeGraph.outEdges[v]) {
//                        ConstantRule newR = null;
//                        if (oe.pid >= 0) {
//                            newR = r.addInstanceAtom(1, oe.pid, oe.oid, false);
//                        } else {
//                            newR = r.addInstanceAtom(1, -oe.pid - 1, oe.oid, true);
//                        }
//                        ruleQueue.enqueue(newR);
//                    }
//                }
            }
        }
        LOGGER.info("A worker is shutting down.");
    }

    public void mine() {
        LOGGER.info("Mining.");
        ConstantRule emptyRule = new ConstantRule();
        emptyRule = emptyRule.addInstanceAtom(0, 0, 0, false);
        ruleQueue.enqueue(emptyRule);
        ExecutorService executor = Executors.newFixedThreadPool(config.numWorkers);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < config.numWorkers; ++i) {
            futures.add(executor.submit(this));
        }
        try {
            for (Future f : futures) {
                f.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        executor.shutdown();
        output.close();
    }
}
