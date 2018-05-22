package de.mpii.embedding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by hovinhthinh on 11/13/17.
 */

// Embeddding Client should support concurrent queries.
public abstract class EmbeddingClient {
    public static final int SO_RANK_LIMIT = 10000;
    protected static final int CACHE_LIMIT_PER_PREDICATE = 10000;
    private static boolean NEGATIVE_TRAINING_ONLY = false;
    protected int nEntities, nRelations, eLength;
    protected FactEncodedSetPerPredicate[] trueFacts;
    protected ConcurrentHashMap<Long, Double>[] cachedRankQueries;

    public EmbeddingClient(String workspace) {
        try {
            // Read nEntities, nRelations, eLength.
            BufferedReader metaIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(workspace +
                    "/meta.txt"))));
            String[] arr = metaIn.readLine().split("\\s++");
            nEntities = Integer.parseInt(arr[0]);
            nRelations = Integer.parseInt(arr[1]);

            HashMap<String, Integer> entitiesStringMap = new HashMap<>(), relationsStringMap = new HashMap<>();
            for (int i = 0; i < nEntities; ++i) {
                entitiesStringMap.put(metaIn.readLine(), i);
            }
            for (int i = 0; i < nRelations; ++i) {
                relationsStringMap.put(metaIn.readLine(), i);
            }
            metaIn.close();

            trueFacts = new FactEncodedSetPerPredicate[nRelations];
            cachedRankQueries = new ConcurrentHashMap[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                trueFacts[i] = new FactEncodedSetPerPredicate();
                cachedRankQueries[i] = new ConcurrentHashMap<>();
            }

            if (NEGATIVE_TRAINING_ONLY) {
                // Read true facts;
                Scanner fIn = new Scanner(new File(workspace + "/train.txt"));
                while (fIn.hasNext()) {
                    int s = fIn.nextInt(), p = fIn.nextInt(), o = fIn.nextInt();
                    trueFacts[p].addFact(s, o);
                }
                fIn.close();
            } else {
                BufferedReader idealIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(workspace +
                        "/ideal.data.txt"))));
                String line;
                while ((line = idealIn.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;
                    }
                    arr = line.split("\t");
                    if (arr[1].equals("<type>") || arr[1].equals("<subClassOf>")) {
                        continue;
                    }
                    if (entitiesStringMap.containsKey(arr[0]) && entitiesStringMap.containsKey(arr[2]) &&
                            relationsStringMap.containsKey(arr[1])) {
                        trueFacts[relationsStringMap.get(arr[1])].addFact(entitiesStringMap.get(arr[0]), entitiesStringMap
                                .get(arr[2]));
                    }
                }
                idealIn.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

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
            if (rankH < SO_RANK_LIMIT && !trueFacts[predicate].containFact(i, object) && getScore(i, predicate, object) > score + 1e-6) {
                ++rankH;
            }
            if (rankT < SO_RANK_LIMIT && !trueFacts[predicate].containFact(subject, i) && getScore(subject, predicate, i) > score + 1e-6) {
                ++rankT;
            }
            if (rankH >= SO_RANK_LIMIT && rankT >= SO_RANK_LIMIT) {
                break;
            }
        }
        double irank = 0.5 / rankH + 0.5 / rankT;
        if (cachedRankQueries[predicate].size() < CACHE_LIMIT_PER_PREDICATE) {
            cachedRankQueries[predicate].put(encoded, irank);
        }
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
