package de.mpii.mining.rule;

import de.mpii.mining.graph.KnowledgeGraph;

import java.util.*;

/**
 * Created by hovinhthinh on 11/16/17.
 */
public class RuleExtensionInfo {
    public static final int UNARY_TYPES_TOP_LIMIT = Integer.MAX_VALUE;
    public static final int INSTANTIATED_LINKS_TOP_LIMIT = 20; // To be fixed soon.
    public HashSet<Integer>[][] binaryClosingPids;
    public HashSet<Integer>[] binaryDanglingPids; // can have negative
    public HashMap<Integer, Integer>[] unaryTypes;
    public HashMap<Long, Integer>[] instantiatedLinks;

    public RuleExtensionInfo(int nVariables) {
        binaryClosingPids = new HashSet[nVariables][nVariables];
        binaryDanglingPids = new HashSet[nVariables];
        unaryTypes = new HashMap[nVariables];
        instantiatedLinks = new HashMap[nVariables];
    }

    public void addClosingPids(int subject, int object, List<Integer> pids) {
        if (pids == null) {
            return;
        }
        if (binaryClosingPids[subject][object] == null) {
            binaryClosingPids[subject][object] = new HashSet<>();
        }
        binaryClosingPids[subject][object].addAll(pids);
    }

    public void addDanglingPids(int subject, HashSet<Integer> pids) {
        if (pids == null) {
            return;
        }
        if (binaryDanglingPids[subject] == null) {
            binaryDanglingPids[subject] = new HashSet<>();
        }
        binaryDanglingPids[subject].addAll(pids);
    }

    public void addTypes(int subject, List<Integer> types) {
        if (types == null) {
            return;
        }
        if (unaryTypes[subject] == null) {
            unaryTypes[subject] = new HashMap<>();
        }
        for (int type : types) {
            unaryTypes[subject].put(type, unaryTypes[subject].getOrDefault(type, 0) + 1);
        }
    }

    public List<Integer> getTopTypesForVariable(int var) {
        ArrayList<Map.Entry<Integer, Integer>> arr = new ArrayList<>();
        arr.addAll(unaryTypes[var].entrySet());
        Collections.sort(arr, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return Integer.compare(o2.getValue(), o1.getValue());
            }
        });
        List<Integer> result = new LinkedList<>();
        for (int i = 0; i < arr.size(); ++i) {
            result.add(arr.get(i).getKey());
            if (i >= UNARY_TYPES_TOP_LIMIT) {
                break;
            }
        }
        return result;
    }

    public void addInstantiatedLinks(int subject, List<KnowledgeGraph.OutgoingEdge> links) {
        if (links == null) {
            return;
        }
        if (instantiatedLinks[subject] == null) {
            instantiatedLinks[subject] = new HashMap<>();
        }
        for (KnowledgeGraph.OutgoingEdge e : links) {
            long code = e.encode();
            instantiatedLinks[subject].put(code, instantiatedLinks[subject].getOrDefault(code, 0) + 1);
        }
    }

    public List<KnowledgeGraph.OutgoingEdge> getTopInstantiatedLinksForVariable(int var) {
        ArrayList<Map.Entry<Long, Integer>> arr = new ArrayList<>();
        arr.addAll(instantiatedLinks[var].entrySet());
        Collections.sort(arr, new Comparator<Map.Entry<Long, Integer>>() {
            @Override
            public int compare(Map.Entry<Long, Integer> o1, Map.Entry<Long, Integer> o2) {
                return Integer.compare(o2.getValue(), o1.getValue());
            }
        });
        List<KnowledgeGraph.OutgoingEdge> result = new LinkedList<>();
        for (int i = 0; i < arr.size(); ++i) {
            result.add(KnowledgeGraph.OutgoingEdge.fromCode(arr.get(i).getKey()));
            if (i >= INSTANTIATED_LINKS_TOP_LIMIT) {
                break;
            }
        }
        return result;
    }

}
