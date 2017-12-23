package de.mpii.mining.rule;

import java.util.HashSet;
import java.util.List;

/**
 * Created by hovinhthinh on 11/16/17.
 */
public class RuleExtensionInfo {
    public HashSet<Integer>[][] binaryClosingPids;
    public HashSet<Integer>[] binaryDanglingPids; // can have negative
    public HashSet<Integer>[] unaryTypes;

    public RuleExtensionInfo(int nVariables) {
        binaryClosingPids = new HashSet[nVariables][nVariables];
        binaryDanglingPids = new HashSet[nVariables];
        unaryTypes = new HashSet[nVariables];
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
            unaryTypes[subject] = new HashSet<>();
        }
        unaryTypes[subject].addAll(types);
    }
}
