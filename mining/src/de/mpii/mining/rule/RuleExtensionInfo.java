package de.mpii.mining.rule;

import java.util.*;

/**
 * Created by hovinhthinh on 11/16/17.
 */
public class RuleExtensionInfo {
    public static final int UNARY_TYPES_TOP_LIMIT = 50;
    public HashSet<Integer>[][] binaryClosingPids;
    public HashSet<Integer>[] binaryDanglingPids; // can have negative
    public HashMap<Integer, Integer>[] unaryTypes;

    public RuleExtensionInfo(int nVariables) {
        binaryClosingPids = new HashSet[nVariables][nVariables];
        binaryDanglingPids = new HashSet[nVariables];
        unaryTypes = new HashMap[nVariables];
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
}
