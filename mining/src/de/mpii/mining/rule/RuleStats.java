package de.mpii.mining.rule;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.mining.MinerConfig;
import de.mpii.mining.graph.KnowledgeGraph;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Created by hovinhthinh on 11/14/17.
 */
public class RuleStats {
    // TODO: disable this bound.
    public static final int HEAD_INSTANCE_BOUND = 10000;

    public int ruleSupport[], bodySupport;
    public double[] headCoverage, confidence, mrr, scr;

    public HashSet<SOInstance> headInstances;

    private double[] sourceScr;

    public RuleStats(double[] sourceScr) {
        this.sourceScr = sourceScr;
        ruleSupport = new int[sourceScr.length];
        bodySupport = 0;
        headCoverage = new double[sourceScr.length];
        confidence = new double[sourceScr.length];
        mrr = new double[sourceScr.length];
        Arrays.fill(mrr, -1);
        scr = new double[sourceScr.length];
        headInstances = new HashSet<>();
    }

    public void simplify(KnowledgeGraph graph, EmbeddingClient embeddingClient, MinerConfig config) {
        bodySupport = headInstances.size();
        for (int pid = 0; pid < confidence.length; ++pid) {
            if (sourceScr[pid] == -1) {
                scr[pid] = -1;
            } else {
                HashSet<Integer> goodS = null;
                for (SOInstance h : headInstances) {
                    if (graph.trueFacts.containFact(h.subject, pid, h.object)) {
                        ++ruleSupport[pid];
                        if (config.usePCAConf) {
                            if (goodS == null) {
                                goodS = new HashSet<>();
                            }
                            goodS.add(h.subject);
                        }
                    }
                }
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
                    if (bodySupport == ruleSupport[pid]) {
                        // Applying the rule doesn't extend the kg.
                        scr[pid] = -1;
                    } else {
                        scr[pid] = confidence[pid] * (1 - config.embeddingWeight);
                        if (config.embeddingWeight > 0) {
                            // Use MRR.
                            mrr[pid] = 0;
                            for (SOInstance h : headInstances) {
                                if (!graph.trueFacts.containFact(h.subject, pid, h.object)) {
                                    mrr[pid] += embeddingClient.getInvertedRank(h.subject, pid, h.object);
                                }
                            }
                            mrr[pid] /= (bodySupport - ruleSupport[pid]);
                            scr[pid] += mrr[pid] * config.embeddingWeight;
                        }
                    }
                }
            }
        }

        headInstances = null;
    }
}
