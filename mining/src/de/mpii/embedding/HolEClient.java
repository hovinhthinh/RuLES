package de.mpii.embedding;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public class HolEClient implements EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(HolEClient.class.getName());
    private int nEntities, nRelations, eLength;
    private DoubleVector[] entitiesEmbedding, relationsEmbedding;
    private FactEncodedSetPerPredicate[] trueFacts;
    private ConcurrentHashMap<Long, Double>[] cachedRankQueries;

    public HolEClient(String workspace) {
        LOGGER.info("Loading embedding HolE client from '" + workspace + ".");

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
                    new File(workspace + "/hole")));
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
        double result = 0, y;
        int oIndex;
        for (int k = 0; k < eLength; ++k) {
            y = 0;
            for (int i = 0; i < eLength; ++i) {
                oIndex = k + i;
                if (oIndex >= eLength) {
                    oIndex -= eLength;
                }
                y += entitiesEmbedding[subject].value[i] * entitiesEmbedding[object].value[oIndex];
            }
            result += y * relationsEmbedding[predicate].value[k];
        }

        return 1.0 / (1 + Math.exp(-result));
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
        new HolEClient("../data/imdb/");
    }
}
