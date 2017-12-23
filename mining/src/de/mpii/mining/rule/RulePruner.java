package de.mpii.mining.rule;

import de.mpii.mining.MinerConfig;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.UnaryAtom;
import de.mpii.mining.graph.KnowledgeGraph;

import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/14/17.
 */
public class RulePruner {
    public static final Logger LOGGER = Logger.getLogger(RulePruner.class.getName());
    private static final int FORMAT_LOG_INTERVAL = 100000;
    private static final int CONTENT_LOG_INTERVAL = 10000;
    private static int FORMAT_COUNT = 0;
    private static int CONTENT_COUNT = 0;

    public static boolean isFormatPruned(Rule r, KnowledgeGraph graph, MinerConfig config) {
        boolean result = isFormatPrunedInternal(r, graph, config);
        if (result) {
            ++FORMAT_COUNT;
            if (FORMAT_COUNT % FORMAT_LOG_INTERVAL == 0) {
                // TODO: Disable logging because of migrated filters.
                // LOGGER.info("FormatPrunedCount: " + FORMAT_COUNT);
            }
        }
        return result;
    }

    private static boolean isFormatPrunedInternal(Rule r, KnowledgeGraph graph, MinerConfig config) {
        // TODO: DEPRECATED: migrated to Miner.
        if (r.nVariables > config.maxNumVariables) {
            return true;
        }
        // Total number of atoms.
        // TODO: DEPRECATED: migrated to Miner.
        if (r.atoms.size() > config.maxNumAtoms) {
            return true;
        }
        // Total number of exception atoms.
        // TODO: DEPRECATED: migrated to Miner.
        int numExceptions = 0;
        for (int i = 0; i < r.atoms.size(); ++i) {
            if (r.atoms.get(i).negated) {
                ++numExceptions;
                if (numExceptions > config.maxNumExceptionAtoms) {
                    return true;
                }
            }
        }

        // Max variable degree.
        if (r.getMaxVariableDegree() > config.maxVariableDegree) {
            return true;
        }
        // Max num unique predicate.
        if (r.getMaxNumUniquePredicate() > config.maxUniquePredicateOccurrence) {
            return true;
        }

        // Check if 2 same predicates in the body sharing the same var (at same subject or object), only process if the
        // max var contains <= 10 such predicate.
        for (int i = 1; i < r.atoms.size(); ++i) {
            if (r.atoms.get(i) instanceof UnaryAtom) {
                continue;
            }
            BinaryAtom a = (BinaryAtom) r.atoms.get(i);
            for (int j = i + 1; j < r.atoms.size(); ++j) {
                if (r.atoms.get(j) instanceof UnaryAtom) {
                    continue;
                }
                BinaryAtom b = (BinaryAtom) r.atoms.get(j);
                if (a.pid != b.pid) continue;
                if (a.sid == b.sid && graph.maxVarPids.get(a.pid) > 10) {
                    return true;
                }
                if (a.oid == b.oid && graph.maxVarPids.get(-a.pid - 1) > 10) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isContentPruned(Rule r, MinerConfig config) {
        boolean result = isContentPrunedInternal(r, config);
        if (result) {
            ++CONTENT_COUNT;
            if (CONTENT_COUNT % CONTENT_LOG_INTERVAL == 0) {
                LOGGER.info("ContentPruned: " + CONTENT_COUNT);
            }
        }
        return result;
    }

    private static boolean goodExceptionCoverage(Rule r, int pid, MinerConfig config) {
        if (r.getState() < 3) {
            // Last added atom is not exception then return true.
            return true;
        }

        return 1 - r.stats.headCoverage[pid] / r.sourceHeadCoverage[pid] >= config.minExceptionCoverage;
    }

    private static boolean isContentPrunedInternal(Rule r, MinerConfig config) {
        if (!r.extensible) {
            return true;
        }
        if (r.stats == null) return false;
        boolean hasGoodHead = false;
        for (int i = 0; i < r.nRelations; ++i) {
            // TODO: DEPRECATED: minHeadCoverage filter migrated to RuleStats.
            if (r.stats.headCoverage[i] >= config.minHeadCoverage && (r.stats.scr[i] > r.sourceScr[i] + 1e-5) && r
                    .sourceScr[i] != -1 && goodExceptionCoverage(r, i, config)) {
                hasGoodHead = true;
            } else {
                r.stats.scr[i] = -1;
            }
        }
        if (!hasGoodHead) {
            return true;
        }
        return false;
    }
}
