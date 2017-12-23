package de.mpii.embedding;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/13/17.
 */

// Support L1 norm only.
public class TransEClient implements EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(TransEClient.class.getName());
    private int nEntities, nRelations, eLength;
    private String norm;
    private DoubleVector[] entitiesEmbedding, relationsEmbedding;
    private FactEncodedSetPerPredicate[] trueFacts;
    private ConcurrentHashMap<Long, Double>[] cachedRankQueries;

    public TransEClient(String workspace, String norm) {
        LOGGER.info("Loading embedding TransE client from '" + workspace + "' with norm " + norm + ".");
        this.norm = norm;
        if (!norm.equals("L1")) {
            throw new RuntimeException("Support L1 norm only.");
        }
        try {
            // Read nEntities, nRelations, eLength.
            Scanner metaIn = new Scanner(new File(workspace + "/meta.txt"));
            nEntities = metaIn.nextInt();
            nRelations = metaIn.nextInt();
            int nClasses = metaIn.nextInt();
            metaIn.close();
            trueFacts = new FactEncodedSetPerPredicate[nRelations];
            cachedRankQueries = new ConcurrentHashMap[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                trueFacts[i] = new FactEncodedSetPerPredicate();
                cachedRankQueries[i] = new ConcurrentHashMap<>();
            }
            // Read embeddings.
            DataInputStream eIn = new DataInputStream(new FileInputStream(
                    new File(workspace + "/transe")));
            eLength = (int) (eIn.readDouble() + 1e-6);
            entitiesEmbedding = new DoubleVector[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                entitiesEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    entitiesEmbedding[i].value[j] = eIn.readDouble();
                }
            }
            relationsEmbedding = new DoubleVector[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                relationsEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    relationsEmbedding[i].value[j] = eIn.readDouble();
                }
            }
            eIn.close();
            // Read true facts;
            Scanner fIn = new Scanner(new File(workspace + "/train.txt"));
            while (fIn.hasNext()) {
                int s = fIn.nextInt(), p = fIn.nextInt(), o = fIn.nextInt();
                trueFacts[p].addFact(s, o);
            }
            fIn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double getScore(int subject, int predicate, int object) {
        double score = 0;
        for (int i = 0; i < eLength; ++i) {
            score += Math.abs(entitiesEmbedding[subject].value[i] + relationsEmbedding[predicate].value[i] -
                    entitiesEmbedding[object].value[i]);
        }
        return -score;
    }

    @Override
    public double getInvertedRank(int subject, int predicate, int object) {
        long encoded = FactEncodedSetPerPredicate.encode(subject, object);
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
            if (!trueFacts[predicate].containFact(i, object) && getScore(i, predicate, object) > score) {
                ++rankH;
            }
            if (!trueFacts[predicate].containFact(subject, i) && getScore(subject, predicate, i) > score) {
                ++rankT;
            }
        }
        double irank = 0.5 / rankH + 0.5 / rankT;
        cachedRankQueries[predicate].put(encoded, irank);
        return irank;
    }

    private static class FactEncodedSetPerPredicate {
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

    public static void main(String[] args) {
        new TransEClient("../data/imdb/", "L1");
    }
}
