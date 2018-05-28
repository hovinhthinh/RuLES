package de.mpii.mining.rule;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.mining.Miner;
import de.mpii.mining.MinerConfig;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.util.Infer;

import java.util.*;

/**
 * Created by hovinhthinh on 11/14/17.
 */


public class RuleStats {
    public static final int MRR_SAMPLE_SIZE = 100;
    public int ruleSupport[], bodySupport;
    public double[] headCoverage, confidence, mrr, scr, ec;
    public HashSet<SOInstance> headInstances;

    private double[] sourceScr; // -1 is pruned, 0 is non-closed.

    public RuleStats(double[] sourceScr) {
        this.sourceScr = sourceScr;
        ruleSupport = new int[sourceScr.length];
        bodySupport = 0;
        headCoverage = new double[sourceScr.length];
        confidence = new double[sourceScr.length];
        ec = new double[sourceScr.length];
        Arrays.fill(ec, -1);
        mrr = new double[sourceScr.length];
        Arrays.fill(mrr, -1);
        scr = new double[sourceScr.length];
        headInstances = new HashSet<>();
    }

    public boolean goodExceptionCoverage(Rule r, int pid, MinerConfig config) {
        if (r.getState() < 3) {
            // Last added atom is not exception then return true.
            return true;
        }
        if (ec[pid] == -1) {
            ec[pid] = ((double) r.sourceBodySupport - bodySupport) / (r.sourceBodySupport - r.sourceRuleSupport[pid]);
        }
        return ec[pid] >= config.minExceptionConfidence;
    }

    public void simplify(Rule r, KnowledgeGraph graph, EmbeddingClient embeddingClient, MinerConfig config) {
        bodySupport = headInstances.size();
        for (int pid = 0; pid < confidence.length; ++pid) {
            if (sourceScr[pid] == -1 || bodySupport <= config.minSupport) {
                scr[pid] = -1;
            } else {
                if (r.atoms.get(r.atoms.size() - 1).negated) { // CHECK SUITABLE EXCEPTION.
                    r.atoms.get(r.atoms.size() - 1).negated = false;
                    HashSet<SOInstance> instances = Infer.matchRule(r, true);
                    boolean flag = true;
                    for (SOInstance so : instances) {
                        if (graph.trueFacts.containFact(so.subject, pid, so.object)) {
                            flag = false;
                            break;
                        }
                    }
                    r.atoms.get(r.atoms.size() - 1).negated = true;
                    if (!flag) {
                        scr[pid] = -1;
                        continue;
                    }
                }

                HashSet<Integer> goodS = null;
                ArrayList<SOInstance> unknownFacts = new ArrayList<>();
                for (SOInstance h : headInstances) {
                    if (graph.trueFacts.containFact(h.subject, pid, h.object)) {
                        ++ruleSupport[pid];
                        if (config.usePCAConf) {
                            if (goodS == null) {
                                goodS = new HashSet<>();
                            }
                            goodS.add(h.subject);
                        }
                    } else {
                        if (config.embeddingWeight > 0) {
                            unknownFacts.add(h);
                        }
                    }
                }
                unknownFacts = Miner.samplingSOHeadInstances(unknownFacts);
                if (config.usePCAConf) {
                    int pcaBodySupport = 0;
                    for (SOInstance h : headInstances) {
                        if (goodS != null && goodS.contains(h.subject)) {
                            ++pcaBodySupport;
                        }
                    }
                    confidence[pid] = pcaBodySupport == 0 ? 0 : (double) ruleSupport[pid] / pcaBodySupport;
                } else {
                    confidence[pid] = bodySupport == 0 ? 0 : (double) ruleSupport[pid] / bodySupport;
                }
                headCoverage[pid] = graph.pidSOInstances[pid].size() == 0 ? 0 : (double) ruleSupport[pid] / graph.pidSOInstances[pid].size();

                scr[pid] = 0;
                if (headCoverage[pid] >= config.minHeadCoverage) {
                    // Call embedding service.
                    if (bodySupport == ruleSupport[pid] || confidence[pid] < config.minConf || ruleSupport[pid] <
                            config.minSupport || !goodExceptionCoverage(r, pid, config)) {
                        // Applying the rule doesn't extend the kg.
                        // Rule is not confident (double check to reduce complexity when calling embedding model)
                        // Rule does not have enough support.
                        scr[pid] = -1;
                    } else {
                        scr[pid] = confidence[pid] * (1 - config.embeddingWeight);
                        if (config.embeddingWeight > 0) {
                            // Use MRR.
                            mrr[pid] = 0;
                            for (SOInstance h : unknownFacts) {
                                mrr[pid] += embeddingClient.getInvertedRank(h.subject, pid, h.object);
                            }
                            mrr[pid] /= unknownFacts.size();
                            scr[pid] += mrr[pid] * config.embeddingWeight;
                        }
                    }
                } else {
                    scr[pid] = -1;
                }
            }
        }

        headInstances = null;
    }
}
