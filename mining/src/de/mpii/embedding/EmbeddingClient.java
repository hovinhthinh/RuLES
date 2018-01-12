package de.mpii.embedding;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by hovinhthinh on 11/13/17.
 */

// Embeddding Client should support concurrent queries.
public abstract class EmbeddingClient {
    protected int nEntities, nRelations, eLength;
    protected FactEncodedSetPerPredicate[] trueFacts;
    protected ConcurrentHashMap<Long, Double>[] cachedRankQueries;

    // Higher score indicates higher plausibility.
    public abstract double getScore(int subject, int predicate, int object);

    public double getInvertedRank(int subject, int predicate, int object) {
        long encoded = EmbeddingClient.FactEncodedSetPerPredicate.encode(subject, object);
        if (cachedRankQueries[predicate].containsKey(encoded)) {
            return cachedRankQueries[predicate].get(encoded);
        }
        int rankH = 1;
        int rankT = 1;
        double score = getScore(subject, predicate, object);
        for (int i = 0; i < nEntities; ++i) {
            if (i == subject || i == object) {
                continue;
            }
            if (!trueFacts[predicate].containFact(i, object) && getScore(i, predicate, object) > score + 1e-6) {
                ++rankH;
            }
            if (!trueFacts[predicate].containFact(subject, i) && getScore(subject, predicate, i) > score + 1e-6) {
                ++rankT;
            }
        }
        double irank = 0.5 / rankH + 0.5 / rankT;
        cachedRankQueries[predicate].put(encoded, irank);
        return irank;
    }

    protected static class FactEncodedSetPerPredicate {
        private static final long BASE = 1000000000;
        private HashSet<Long> set = new HashSet<>();

        public static long encode(int subject, int object) {
            return ((long) subject) * BASE + object;
        }

        public void addFact(int subject, int object) {
            set.add(encode(subject, object));
        }

        public boolean containFact(int subject, int object) {
            return set.contains(encode(subject, object));
        }
    }

}
