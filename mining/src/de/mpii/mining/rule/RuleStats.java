package de.mpii.mining.rule;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.mining.MinerConfig;
import de.mpii.mining.graph.KnowledgeGraph;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by hovinhthinh on 11/14/17.
 */


public class RuleStats {
    public static class DisjunctionStats {
        public int pid1, pid2;
        public double hc, conf, mrr, scr, inreaseScr;

        public DisjunctionStats(int pid1, int pid2, double hc, double conf, double mrr, double scr, double inreaseScr) {
            this.pid1 = pid1;
            this.pid2 = pid2;
            this.hc = hc;
            this.conf = conf;
            this.mrr = mrr;
            this.scr = scr;
            this.inreaseScr = inreaseScr;
        }
    }

    // TODO: disable this bound.
    public static final int HEAD_INSTANCE_BOUND = 10000;

    public int ruleSupport[], bodySupport;
    public double[] headCoverage, confidence, mrr, scr;

    public HashSet<SOInstance> headInstances;

    public List<DisjunctionStats> disjunctionStats;

    // -1 is pruned, 0 is non-closed.
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

    public void simplify(KnowledgeGraph graph, EmbeddingClient embeddingClient, MinerConfig config, boolean
            withDisjunction) {
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
                } else {
                    scr[pid] = -1;
                }
            }
        }

        // For disjunction
        if (withDisjunction) {
            disjunctionStats = new LinkedList<>();
            for (int pid1 = 0; pid1 < confidence.length; ++pid1) {
                for (int pid2 = pid1 + 1; pid2 < confidence.length; ++pid2) {
                    if (sourceScr[pid1] == -1 && sourceScr[pid2] == -1) {
                        continue;
                    }
                    HashSet<Integer> goodS = null;
                    int ruleSupport = 0;
                    for (SOInstance h : headInstances) {
                        if (graph.trueFacts.containFact(h.subject, pid1, h.object) || graph.trueFacts.containFact(h
                                .subject, pid2, h.object)) {
                            ++ruleSupport;
                            if (config.usePCAConf) {
                                if (goodS == null) {
                                    goodS = new HashSet<>();
                                }
                                goodS.add(h.subject);
                            }
                        }
                    }
                    double conf, hc;
                    if (config.usePCAConf) {
                        int pcaBodySupport = 0;
                        for (SOInstance h : headInstances) {
                            if (goodS != null && goodS.contains(h.subject)) {
                                ++pcaBodySupport;
                            }
                        }
                        conf = pcaBodySupport == 0 ? 0 : (double) ruleSupport / pcaBodySupport;
                    } else {
                        conf = bodySupport == 0 ? 0 : (double) ruleSupport / bodySupport;
                    }
                    int headSupport = graph.pidSOInstances[pid1].size() + graph.pidSOInstances[pid2].size() - graph
                            .pid1Pid2Count.getOrDefault(pid1 * graph.nRelations + pid2, 0);
                    hc = headSupport == 0 ? 0 : (double) ruleSupport / headSupport;

                    if (hc >= config.minHeadCoverage) {
                        // Call embedding service.
                        if (bodySupport != ruleSupport) {
                            double scr = conf * (1 - config.embeddingWeight);
                            double mrr = 0;
                            if (config.embeddingWeight > 0) {
                                // Use MRR.
                                for (SOInstance h : headInstances) {
                                    if (!graph.trueFacts.containFact(h.subject, pid1, h.object) && !graph.trueFacts
                                            .containFact(h.subject, pid2, h.object)) {
                                        mrr += Math.max(embeddingClient.getInvertedRank(h.subject, pid1, h.object),
                                                embeddingClient.getInvertedRank(h.subject, pid2, h.object));
                                    }
                                }
                                mrr /= (bodySupport - ruleSupport);
                                scr += mrr * config.embeddingWeight;
                            } else {
                                mrr = -1;
                            }
                            double increaseScr = scr - Math.max(0, Math.max(this.scr[pid1], this.scr[pid2]));
                            if (increaseScr >= 1e-3) {
                                // Output.
                                disjunctionStats.add(new DisjunctionStats(pid1, pid2, hc, conf, mrr, scr, increaseScr));
                            }
                        }
                    }
                }
                if (config.usePCAConf || sourceScr[pid1] == -1) {
                    continue;
                }
                for (int pid2 = pid1 + 1; pid2 < confidence.length; ++pid2) {
                    // calculate reversed score.
                    int ruleSupport2 = 0;
                    for (SOInstance h : headInstances) {
                        if (graph.trueFacts.containFact(h.object, pid2, h.subject)) {
                            ++ruleSupport2;
                        }
                    }
                    double scr2 = (bodySupport == 0 ? 0 : (double) ruleSupport2 / bodySupport) * (1 - config
                            .embeddingWeight);
                    if (config.embeddingWeight > 0 && bodySupport != ruleSupport2) {
                        double mrr2 = 0;
                        for (SOInstance h : headInstances) {
                            if (!graph.trueFacts.containFact(h.object, pid2, h.subject)) {
                                mrr2 += embeddingClient.getInvertedRank(h.object, pid2, h.subject);
                            }
                        }
                        mrr2 /= (bodySupport - ruleSupport2);
                        scr2 += mrr2 * config.embeddingWeight;
                    }

                    int ruleSupport = 0;
                    for (SOInstance h : headInstances) {
                        if (graph.trueFacts.containFact(h.subject, pid1, h.object) || graph.trueFacts.containFact(h
                                .object, pid2, h.subject)) {
                            ++ruleSupport;
                        }
                    }
                    double conf, hc;
                    conf = bodySupport == 0 ? 0 : (double) ruleSupport / bodySupport;

                    int headSupport = graph.pidSOInstances[pid1].size() + graph.pidSOInstances[pid2].size() - graph
                            .pid1Pid2CountReversed.getOrDefault(pid1 * graph.nRelations + pid2, 0);
                    hc = headSupport == 0 ? 0 : (double) ruleSupport / headSupport;

                    if (hc >= config.minHeadCoverage) {
                        // Call embedding service.
                        if (bodySupport != ruleSupport) {
                            double scr = conf * (1 - config.embeddingWeight);
                            double mrr = 0;
                            if (config.embeddingWeight > 0) {
                                // Use MRR.
                                for (SOInstance h : headInstances) {
                                    if (!graph.trueFacts.containFact(h.subject, pid1, h.object) && !graph.trueFacts
                                            .containFact(h.object, pid2, h.subject)) {
                                        mrr += Math.max(embeddingClient.getInvertedRank(h.subject, pid1, h.object),
                                                embeddingClient.getInvertedRank(h.object, pid2, h.subject));
                                    }
                                }
                                mrr /= (bodySupport - ruleSupport);
                                scr += mrr * config.embeddingWeight;
                            } else {
                                mrr = -1;
                            }
                            double increaseScr = scr - Math.max(0, Math.max(this.scr[pid1], scr2));
                            if (increaseScr >= 1e-3) {
                                // Output.
                                disjunctionStats.add(new DisjunctionStats(pid1, -pid2 - 1, hc, conf, mrr, scr,
                                        increaseScr));
                            }
                        }
                    }
                }
            }
        }

        headInstances = null;
    }
}
