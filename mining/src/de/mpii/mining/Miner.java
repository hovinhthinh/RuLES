package de.mpii.mining;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.embedding.HolEClient;
import de.mpii.embedding.SSPClient;
import de.mpii.embedding.TransEClient;
import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.InstantiatedAtom;
import de.mpii.mining.atom.UnaryAtom;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.*;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/14/17.
 */

public class Miner implements Runnable {
    public static final Logger LOGGER = Logger.getLogger(Miner.class.getName());
    private static final int MATCH_RULE_LOG_INTERVAL = 10000;

    public int matchedRule;

    public EmbeddingClient embeddingClient;
    public KnowledgeGraph knowledgeGraph;
    public RuleQueue ruleQueue;

    public MinerConfig config;

    public PrintWriter output;

    public Miner(String workspace, MinerConfig config, PrintWriter output) {
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
        ruleQueue = new RuleQueue(config.enqueueLimit);
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

    private void recur(Rule rule, int position, int variableValues[], RuleStats stats) {
        if (position == rule.atoms.size()) {
            rule.extensible = true;
            if (rule.closed) {
                stats.headInstances.add(new SOInstance(variableValues[0], variableValues[1]));
            }
            // Extension info.
            for (int i = 0; i < rule.nVariables; ++i) {
                for (int j = i + 1; j < rule.nVariables; ++j) {
                    if (variableValues[i] != -1 && variableValues[j] != -1) {
                        rule.extensionInfo.addClosingPids(i, j, knowledgeGraph.getPidList(variableValues[i], variableValues[j]));
                        rule.extensionInfo.addClosingPids(j, i, knowledgeGraph.getPidList(variableValues[j], variableValues[i]));
                    } else if (variableValues[i] == -1 && variableValues[j] == -1) {
                        if (rule.extensionInfo.binaryClosingPids[i][j] == null) {
                            rule.extensionInfo.binaryClosingPids[i][j] = new HashSet<>();
                        }
                        if (rule.extensionInfo.binaryClosingPids[j][i] == null) {
                            rule.extensionInfo.binaryClosingPids[j][i] = new HashSet<>();
                        }
                        for (int k = 0; k < knowledgeGraph.nRelations; ++k) {
                            rule.extensionInfo.binaryClosingPids[i][j].add(k);
                            rule.extensionInfo.binaryClosingPids[j][i].add(k);
                        }
                    } else {
                        // i->j
                        if (variableValues[j] == -1) {
                            if (rule.extensionInfo.binaryClosingPids[i][j] == null) {
                                rule.extensionInfo.binaryClosingPids[i][j] = new HashSet<>();
                            }
                            for (int k : knowledgeGraph.danglingPids[variableValues[i]]) {
                                if (k >= 0) {
                                    rule.extensionInfo.binaryClosingPids[i][j].add(k);
                                }
                            }
                        } else { // j->i
                            if (rule.extensionInfo.binaryClosingPids[j][i] == null) {
                                rule.extensionInfo.binaryClosingPids[j][i] = new HashSet<>();
                            }
                            for (int k : knowledgeGraph.danglingPids[variableValues[j]]) {
                                if (k >= 0) {
                                    rule.extensionInfo.binaryClosingPids[j][i].add(k);
                                }
                            }

                        }
                    }
                }
                if (variableValues[i] != -1) {
                    if (config.maxNumUnaryPositiveAtoms > 0 || (config.maxNumExceptionAtoms > 0 && config
                            .maxNumUnaryExceptionAtoms > 0)) {
                        rule.extensionInfo.addTypes(i, knowledgeGraph.types[variableValues[i]]);
                    }
                    if (config.maxNumExceptionAtoms > 0 && config.maxNumInstantiatedExceptionAtoms > 0) {
                        rule.extensionInfo.addInstantiatedLinks(i, knowledgeGraph.outEdges[variableValues[i]]);
                    }
                }

                if (variableValues[i] == -1) {
                    if (rule.extensionInfo.binaryDanglingPids[i] == null) {
                        rule.extensionInfo.binaryDanglingPids[i] = new HashSet<>();
                    }
                    // If variable is unset, danging pids are unlimited.
                    for (int j = 0; j < knowledgeGraph.nRelations; ++j) {
                        rule.extensionInfo.binaryDanglingPids[i].add(j);
                        rule.extensionInfo.binaryDanglingPids[i].add(-j - 1);
                    }
                } else {
                    rule.extensionInfo.addDanglingPids(i, knowledgeGraph.danglingPids[variableValues[i]]);
                }
            }
            return;
        }
        Atom a = rule.atoms.get(position);
        if (a instanceof InstantiatedAtom) {
            InstantiatedAtom atom = (InstantiatedAtom) a;
            if (variableValues[a.sid] == -1) {
                // This case only happens for positive atom.
                variableValues[a.sid] = -1;
                throw new RuntimeException("To be implemented");
            } else {
                boolean hasEdge = atom.reversed ? knowledgeGraph.trueFacts.containFact(atom.value,
                        atom.pid, variableValues[atom.sid]) : knowledgeGraph.trueFacts.containFact
                        (variableValues[atom.sid], atom.pid, atom.value);
                if (hasEdge == a.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, stats);
            }
        } else if (a instanceof UnaryAtom) {
            if (variableValues[a.sid] == -1) {
                // This case only happens for positive atom.
                for (int t : knowledgeGraph.typeInstances[a.pid]) {
                    if (duplicatedVar(variableValues, t)) {
                        continue;
                    }
                    variableValues[a.sid] = t;
                    recur(rule, position + 1, variableValues, stats);
                }
                variableValues[a.sid] = -1;
            } else {
                boolean hasType = knowledgeGraph.trueTypes.containType(variableValues[a.sid], a.pid);
                if (hasType == a.negated) {
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

    public static ArrayList<SOInstance> samplingSOHeadInstances(ArrayList<SOInstance> instances) {
        if (instances.size() > RuleStats.MRR_SAMPLE_SIZE) {
            Collections.shuffle(instances);
            ArrayList<SOInstance> result = new ArrayList<>();
            for (int i = 0; i < RuleStats.MRR_SAMPLE_SIZE; ++i) {
                result.add(instances.get(i));
            }
            return result;
        } else {
            return instances;
        }
    }

    // Process and fill the 'stats'. If after matching, stats is still null, then it is not processed, scr should be
    // consider as infinity.
    public void matchRule(Rule r) {
        r.closed = r.isClosed();
        if (r.nVariables == 0) {
            r.extensible = true;
            return;
        }
        int[] variableValues = new int[r.nVariables];
        Arrays.fill(variableValues, -1);
        RuleStats stats = new RuleStats(r.sourceScr);
        r.extensionInfo = new RuleExtensionInfo(r.nVariables);
        recur(r, 1, variableValues, stats);

        // If the monotonic part is closed, then set stats.
        if (r.closed) {
            stats.simplify(knowledgeGraph, embeddingClient, config, config.disjunction && (r.atoms.size() < config
                    .maxNumAtoms ? true : false));
            r.stats = stats;
        }

        ++matchedRule;
        if (matchedRule % MATCH_RULE_LOG_INTERVAL == 0) {
            LOGGER.info("MatchedRuleBodyCount: " + matchedRule);
        }
    }

    public void run() {
        while (true) {
            Rule r = ruleQueue.dequeue();
            if (r == null) {
                break;
            }
            matchRule(r);
            // Output disjunction.
            if (r.stats != null && r.stats.disjunctionStats != null) {
                for (RuleStats.DisjunctionStats dstats : r.stats.disjunctionStats) {
                    System.out.printf("%s\thc:\t%.3f\t%sconf:\t%.3f\tmrr:\t%.3f\tscr:\t%.3f\tinc:\t%.3f\n", r
                                    .getDisjunctionString
                                            (dstats.pid1, dstats.pid2, knowledgeGraph.relationsString, knowledgeGraph
                                                    .typesString, knowledgeGraph.entitiesString),
                            dstats.hc, config.usePCAConf ? "pca" : "", dstats.conf, dstats.mrr, dstats.scr,
                            dstats.inreaseScr);
                    synchronized (output) {
                        output.printf("%s\thc:\t%.3f\t%sconf:\t%.3f\tmrr:\t%.3f\tscr:\t%.3f\tinc:\t%.3f\n", r
                                        .getDisjunctionString
                                                (dstats.pid1, dstats.pid2, knowledgeGraph.relationsString, knowledgeGraph
                                                        .typesString, knowledgeGraph.entitiesString),
                                dstats.hc, config.usePCAConf ? "pca" : "", dstats.conf, dstats.mrr, dstats.scr,
                                dstats.inreaseScr);
                        output.flush();
                    }
                }
            }
            if (RulePruner.isContentPruned(r, config)) {
                continue;
            }
            if (r.stats != null) {
                if (!config.disjunction) {
                    for (int pid = 0; pid < knowledgeGraph.nRelations; ++pid) {
                        if (r.stats.scr[pid] != -1) {
                            r.atoms.get(0).pid = pid;
                            String result = String.format(
                                    "%s\thc:\t%.3f\t%sconf:\t%.3f\tmrr:\t%.3f\tscr:\t%.3f\tsup:\t%d\tec:\t%.3f",
                                    r.getString(knowledgeGraph.relationsString, knowledgeGraph.typesString, knowledgeGraph.entitiesString),
                                    r.stats.headCoverage[pid],
                                    config.usePCAConf ? "pca" : "",
                                    r.stats.confidence[pid],
                                    r.stats.mrr[pid],
                                    r.stats.scr[pid],
                                    r.stats.ruleSupport[pid],
                                    r.stats.ec[pid]);
                            System.out.println(result);
                            synchronized (output) {
                                output.println(result);
                                output.flush();
                            }
                        }
                    }
                    r.atoms.get(0).pid = -1;
                }
            }
            if (config.disjunction && r.atoms.size() >= config.maxNumAtoms - 1) {
                continue;
            }
            if (r.atoms.size() >= config.maxNumAtoms) { // TODO: Migated from Pruner.
                continue;
            }
            int state = r.getState();
            // Type of last atom:
            // empty: -1 -> dangling(0) -> binary closed(1) -> unary closed(2) -> unary exception(3) -> binary exception(4).
            if (state <= 0 && r.nVariables < config.maxNumVariables && r.getNumBinaryPositiveAtoms() < config
                    .maxNumBinaryPositiveAtoms && r.atoms.size() < config.maxNumAtoms - 1) {
                // TODO:
                // Migated from Pruner.
                // Add dangling atoms.
                if (r.atoms.isEmpty()) {
                    // First binary atom has anonymous pid.
                    Rule newR = r.addDanglingAtom(-1, -1, true);
                    ruleQueue.enqueue(newR);
                } else {
                    for (int v = 0; v < r.nVariables; ++v) {
                        if (config.xyz && v > 0) {
                            // only add dangling to 0
                            continue;
                        }
                        if (v == 1) {
                            // not adding dangling to 1;
                            continue;
                        }
                        if (r.extensionInfo.binaryDanglingPids[v] == null) {
                            continue;
                        }
                        for (int i : r.extensionInfo.binaryDanglingPids[v]) {
                            if (i >= 0) {
                                Rule newR = r.addDanglingAtom(v, i, true);
                                if (!RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                    ruleQueue.enqueue(newR);
                                }
                            } else {
                                if (config.xyz) {
                                    // only add forward edge to 0
                                    continue;
                                }
                                Rule newR = r.addDanglingAtom(v, -i - 1, false);
                                if (!RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                    ruleQueue.enqueue(newR);
                                }
                            }
                        }
                    }
                }
            }
            if (state <= 1 && r.getNumBinaryPositiveAtoms() < config.maxNumBinaryPositiveAtoms) {
                // Add closing binary atoms.
                for (int i = 0; i < r.nVariables; ++i) {
                    for (int j = 0; j < r.nVariables; ++j) {
                        if (config.xyz && (i != 2 || j != 1)) {
                            continue;
                        }
                        if (i == j || r.extensionInfo.binaryClosingPids[i][j] == null) {
                            continue;
                        }
                        for (int k : r.extensionInfo.binaryClosingPids[i][j]) {
                            Rule newR = r.addClosingBinaryAtom(i, k, j, false);
                            if (newR != null && !RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                ruleQueue.enqueue(newR);
                            }
                        }
                    }
                }
            }

            // Add other atoms only when the binary monotonic parts are closed.
            if (r.isBinaryClosed()) {
                // Add closing unary atoms.
                if (state <= 2 && r.getNumUnaryPositiveAtoms() < config.maxNumUnaryPositiveAtoms) {
                    for (int i = 0; i < r.nVariables; ++i) {
                        for (Map.Entry<Integer, Integer> e : r.extensionInfo.unaryTypes[i].entrySet()) {
                            int j = e.getKey();
                            Rule newR = r.addClosingUnaryAtom(i, j, false);
                            if (newR != null && !RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                ruleQueue.enqueue(newR);
                            }
                        }
                    }
                }
                int nInstantiatedExceptions = r.getNumInstantiatedExceptionAtoms();
                int nUnaryExceptions = r.getNumUnaryExceptionAtoms();
                int nBinaryExceptions = r.getNumBinaryExceptionAtoms();
                if (nInstantiatedExceptions + nUnaryExceptions + nBinaryExceptions < config.maxNumExceptionAtoms) {
                    // Add exception instantiated atoms.
                    if (state <= 3 && nInstantiatedExceptions < config.maxNumInstantiatedExceptionAtoms) {
                        for (int i = 0; i < r.nVariables; ++i) {
                            for (KnowledgeGraph.OutgoingEdge e : r.extensionInfo.getTopInstantiatedLinksForVariable(i)) {
                                Rule newR = e.pid >= 0 ? r.addClosingInstantiatedAtom(i, e.pid, e.oid, true, false) : r
                                        .addClosingInstantiatedAtom(i, -1 - e.pid, e.oid, true, true);
                                if (newR != null && !RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                    ruleQueue.enqueue(newR);
                                }
                            }
                        }
                    }

                    // Add exception unary atoms.
                    if (state <= 3 && nUnaryExceptions < config.maxNumUnaryExceptionAtoms) {
                        for (int i = 0; i < r.nVariables; ++i) {
                            for (int j : r.extensionInfo.getTopTypesForVariable(i)) {
                                Rule newR = r.addClosingUnaryAtom(i, j, true);
                                if (newR != null && !RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                    ruleQueue.enqueue(newR);
                                }
                            }
                        }
                    }
                    // Add exception binary atoms.
                    if (state <= 4 && nBinaryExceptions < config.maxNumBinaryExceptionAtoms) {
                        for (int i = 0; i < r.nVariables; ++i) {
                            for (int j = 0; j < r.nVariables; ++j) {
                                if (i == j || r.extensionInfo.binaryClosingPids[i][j] == null) {
                                    continue;
                                }
                                for (int k : r.extensionInfo.binaryClosingPids[i][j]) {
                                    Rule newR = r.addClosingBinaryAtom(i, k, j, true);
                                    if (newR != null && !RulePruner.isFormatPruned(newR, knowledgeGraph, config)) {
                                        ruleQueue.enqueue(newR);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LOGGER.info("A worker is shutting down.");
    }

    public void mine() {
        LOGGER.info("Mining.");
        Rule emptyRule = new Rule(knowledgeGraph.nRelations);
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
