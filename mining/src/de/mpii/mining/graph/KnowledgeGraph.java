package de.mpii.mining.graph;

import de.mpii.mining.rule.SOInstance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/13/17.
 */

public class KnowledgeGraph {
    public static final Logger LOGGER = Logger.getLogger(KnowledgeGraph.class.getName());
    public int nEntities, nRelations, nTypes;
    public String[] entitiesString, relationsString, typesString;
    public HashMap<String, Integer> entitiesStringMap, relationsStringMap, typesStringMap;
    public List<OutgoingEdge>[] outEdges;
    public List<Integer>[] types;
    public List<SOInstance>[] pidSOInstances;
    public List<Integer>[] typeInstances;
    public FactEncodedSet trueFacts, idealFacts;
    public TypeEncodedSet trueTypes;
    public HashMap<Long, List<Integer>> soPidMap;
    public HashMap<Integer, Integer> maxVarPids;
    public HashSet<Integer>[] danglingPids;
    public double[] rSupport;

    public HashMap<Integer, Integer> pid1Pid2Count, pid1Pid2CountReversed; // handle disjunction

    public KnowledgeGraph(String workspace) {
        LOGGER.info("Loading knowledge graph from '" + workspace + "'.");
        String[] spl;
        String line = null;
        try {
            BufferedReader metaIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(workspace +
                    "/meta.txt"))));
            spl = metaIn.readLine().split("\\s++");
            nEntities = Integer.parseInt(spl[0]);
            nRelations = Integer.parseInt(spl[1]);
            nTypes = Integer.parseInt(spl[2]);

            outEdges = new List[nEntities];
            types = new List[nEntities];

            pidSOInstances = new List[nRelations];
            rSupport = new double[nRelations];
            typeInstances = new List[nTypes];

            trueTypes = new TypeEncodedSet();

            for (int i = 0; i < nEntities; ++i) {
                outEdges[i] = new ArrayList<>();
                types[i] = new ArrayList<>();
            }
            for (int i = 0; i < nRelations; ++i) {
                pidSOInstances[i] = new ArrayList<>();
            }
            for (int i = 0; i < nTypes; ++i) {
                typeInstances[i] = new ArrayList<>();
            }

            entitiesString = new String[nEntities];
            entitiesStringMap = new HashMap<>();
            for (int i = 0; i < nEntities; ++i) {
                entitiesString[i] = metaIn.readLine();
                entitiesStringMap.put(entitiesString[i], i);
            }
            relationsString = new String[nRelations];
            relationsStringMap = new HashMap<>();
            for (int i = 0; i < nRelations; ++i) {
                relationsString[i] = metaIn.readLine();
                relationsStringMap.put(relationsString[i], i);
            }
            typesString = new String[nTypes];
            typesStringMap = new HashMap<>();
            for (int i = 0; i < nTypes; ++i) {
                typesString[i] = metaIn.readLine();
                typesStringMap.put(typesString[i], i);
            }
            while ((line = metaIn.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }
                spl = line.split("\\s++");
                int s = Integer.parseInt(spl[0]), p = Integer.parseInt(spl[1]);
                types[s].add(p);
                trueTypes.addType(s, p);
                typeInstances[p].add(s);
            }
            metaIn.close();
            trueFacts = new FactEncodedSet();
            soPidMap = new HashMap<>();
            danglingPids = new HashSet[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                danglingPids[i] = new HashSet<>();
            }
            Scanner in = new Scanner(new File(workspace + "/train.txt"));

            maxVarPids = new HashMap<>();
            HashMap<Integer, HashMap<Integer, Integer>> maxVarPidsTemp = new HashMap<>();

            for (int i = 0; i < nRelations; ++i) {
                maxVarPidsTemp.put(i, new HashMap<>());
                maxVarPidsTemp.put(-i - 1, new HashMap<>());
            }

            while (in.hasNext()) {
                int s = in.nextInt(), p = in.nextInt(), o = in.nextInt();
                if (!trueFacts.addFact(s, p, o)) {
                    continue;
                }
                outEdges[s].add(new OutgoingEdge(p, o));
                outEdges[o].add(new OutgoingEdge(-p - 1, s));

                pidSOInstances[p].add(new SOInstance(s, o));

                long soCode = encodeSO(s, o);
                if (!soPidMap.containsKey(soCode)) {
                    soPidMap.put(soCode, new ArrayList<>());
                }
                List<Integer> pidList = soPidMap.get(soCode);
                pidList.add(p);
                danglingPids[s].add(p);
                danglingPids[o].add(-p - 1);

                HashMap<Integer, Integer> pidCount = maxVarPidsTemp.get(p);
                pidCount.put(s, pidCount.getOrDefault(s, 0) + 1);
                pidCount = maxVarPidsTemp.get(-p - 1);
                pidCount.put(o, pidCount.getOrDefault(o, 0) + 1);
            }

            // Calculate rSupport (used for computing conviction)
            for (int i = 0; i < nRelations; ++i) {
                HashSet<Integer> distinctS = new HashSet<>(), distinctO = new HashSet<>();
                for (SOInstance so : pidSOInstances[i]) {
                    distinctS.add(so.subject);
                    distinctO.add(so.object);
                }
                rSupport[i] = ((double) pidSOInstances[i].size()) / distinctS.size() / distinctO.size();
            }

            pid1Pid2Count = new HashMap<>();
            for (long so : soPidMap.keySet()) {
                List<Integer> pids = soPidMap.get(so);
                for (int p1 : pids) {
                    for (int p2 : pids) {
                        if (p1 < p2) {
                            int code = p1 * nRelations + p2;
                            pid1Pid2Count.put(code, pid1Pid2Count.getOrDefault(code, 0) + 1);
                        }
                    }
                }
            }
            pid1Pid2CountReversed = new HashMap<>();
            for (long so : soPidMap.keySet()) {
                int subject = (int) (so / 10000000), object = (int) (so % 10000000);
                List<Integer> pids = soPidMap.get(so);
                List<Integer> pidsR = soPidMap.get(encodeSO(object, subject));
                if (pidsR == null) {
                    continue;
                }
                for (int p1 : pids) {
                    for (int p2 : pidsR) {
                        if (p1 < p2) {
                            int code = p1 * nRelations + p2;
                            pid1Pid2CountReversed.put(code, pid1Pid2CountReversed.getOrDefault(code, 0) + 1);
                        }
                    }
                }
            }

            for (int i = 0; i < nRelations; ++i) {
                HashMap<Integer, Integer> pidCount = maxVarPidsTemp.get(i);
                int max = 0;
                for (Map.Entry<Integer, Integer> e : pidCount.entrySet()) {
                    max = Math.max(max, e.getValue());
                }
                maxVarPids.put(i, max);
                pidCount = maxVarPidsTemp.get(-i - 1);
                max = 0;
                for (Map.Entry<Integer, Integer> e : pidCount.entrySet()) {
                    max = Math.max(max, e.getValue());
                }
                maxVarPids.put(-i - 1, max);
            }

            in.close();

            idealFacts = new FactEncodedSet();
            BufferedReader idealIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(workspace +
                    "/ideal.data.txt"))));
            while ((line = idealIn.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }
                String[] arr = line.split("\t");
                if (arr[1].equals("<type>") || arr[1].equals("<subClassOf>")) {
                    continue;
                }
                if (entitiesStringMap.containsKey(arr[0]) && entitiesStringMap.containsKey(arr[2]) &&
                        relationsStringMap.containsKey(arr[1])) {
                    idealFacts.addFact(entitiesStringMap.get(arr[0]), relationsStringMap.get(arr[1]), entitiesStringMap
                            .get(arr[2]));
                }
            }
            idealIn.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long encodeSO(int subject, int object) {
        return ((long) subject) * 10000000 + object; // not change the base here.
    }

    public List<Integer> getPidList(int subject, int object) {
        return soPidMap.get(encodeSO(subject, object));
    }

    public static class OutgoingEdge {
        // Reversed edges will have pid negative.
        public int pid, oid;

        public OutgoingEdge(int pid, int oid) {
            this.pid = pid;
            this.oid = oid;
        }

        public static OutgoingEdge fromCode(long code) {
            int pid = (int) (code / 1000000000), oid = (int) (code % 1000000000);
            if (oid < 0) {
                oid += 1000000000;
                --pid;
            }
            return new OutgoingEdge(pid, oid);
        }

        public long encode() {
            return ((long) pid) * 1000000000 + oid;
        }
    }

    public static class FactEncodedSet {
        private static final long BASE = 1000000000;
        private HashSet<Long> set = new HashSet<>();

        public static long encode(int subject, int predicate, int object) {
            return (((long) subject) * BASE + predicate) * BASE + object;
        }

        public boolean addFact(int subject, int predicate, int object) {
            return set.add(encode(subject, predicate, object));
        }

        public boolean containFact(int subject, int predicate, int object) {
            return set.contains(encode(subject, predicate, object));
        }
    }

    public static class TypeEncodedSet {
        private static final long BASE = 1000000000;
        private HashSet<Long> set = new HashSet<>();

        public static long encode(int subject, int predicate) {
            return ((long) subject) * BASE + predicate;
        }

        public void addType(int subject, int predicate) {
            set.add(encode(subject, predicate));
        }

        public boolean containType(int subject, int predicate) {
            return set.contains(encode(subject, predicate));
        }
    }
}
