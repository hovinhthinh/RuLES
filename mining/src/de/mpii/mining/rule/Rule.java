package de.mpii.mining.rule;

import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.InstantiatedAtom;
import de.mpii.mining.atom.UnaryAtom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Rule {
    // Support maximum 1 million entities, relations.
    private static final long E_POW[] = new long[1000000];
    private static final long R_POW[] = new long[1000000];
    private static final long V_POW[] = new long[10];
    private static final long NEGATION_SIGN = 999983;
    private static final long UNARY_SIGN = 15485863;
    private static final long INSTANTIATED_SIGN = 10000033;
    private static final long REVERSED_SIGN = 1000003;

    static {
        E_POW[0] = 1;
        R_POW[0] = 1;
        V_POW[0] = 1;
        for (int i = 1; i < E_POW.length; ++i) {
            E_POW[i] = E_POW[i - 1] * 1000000007;
        }
        for (int i = 1; i < R_POW.length; ++i) {
            R_POW[i] = R_POW[i - 1] * 1000000009;
        }

        for (int i = 1; i < V_POW.length; ++i) {
            V_POW[i] = V_POW[i - 1] * 11;
        }
    }

    // If sourceScr == -1, it is filtered.
    public double[] sourceScr;

    public double[] sourceHeadCoverage;

    public boolean extensible;

    public int nVariables;
    // First atom must be binary and have sid = 0 and oid = 1.
    public ArrayList<Atom> atoms;
    // To be filled when matching with knowledge graph.
    public RuleStats stats;

    public RuleExtensionInfo extensionInfo;

    public boolean closed;

    public int nRelations;

    public Rule(int nRelations) {
        sourceScr = null;
        sourceHeadCoverage = null;
        this.nRelations = nRelations;
        nVariables = 0;
        atoms = new ArrayList<>();
        stats = null;
        extensionInfo = null;
        extensible = false;
    }

    public int getNumUnaryPositiveAtoms() {
        int num = 0;
        for (Atom a : atoms) {
            if (!a.negated && (a instanceof UnaryAtom)) {
                ++num;
            }
        }
        return num;
    }

    public int getNumBinaryPositiveAtoms() {
        int num = 0;
        for (Atom a : atoms) {
            if (!a.negated && (a instanceof BinaryAtom)) {
                ++num;
            }
        }
        return num;
    }

    public int getNumUnaryExceptionAtoms() {
        int num = 0;
        for (Atom a : atoms) {
            if (a.negated && (a instanceof UnaryAtom)) {
                ++num;
            }
        }
        return num;
    }

    public int getNumBinaryExceptionAtoms() {
        int num = 0;
        for (Atom a : atoms) {
            if (a.negated && (a instanceof BinaryAtom)) {
                ++num;
            }
        }
        return num;
    }

    public int getNumInstantiatedPositiveAtoms() {
        int num = 0;
        for (Atom a : atoms) {
            if (!a.negated && (a instanceof InstantiatedAtom)) {
                ++num;
            }
        }
        return num;
    }

    public int getNumInstantiatedExceptionAtoms() {
        int num = 0;
        for (Atom a : atoms) {
            if (a.negated && (a instanceof InstantiatedAtom)) {
                ++num;
            }
        }
        return num;
    }

    // Type of last atom:
    // empty: -1 -> dangling(0) -> binary closed(1) -> unary closed(2) -> unary/instance exception(3) -> binary
    // exception(4).
    public int getState() {
        if (atoms.size() == 0) {
            return -1;
        }
        Atom a = atoms.get(atoms.size() - 1);
        if (a.dangling) {
            return 0;
        } else if (!a.negated) {
            return (a instanceof BinaryAtom) ? 1 : 2;
        } else {
            return (a instanceof BinaryAtom) ? 4 : 3;
        }
    }

    public int getMaxVariableDegree() {
        int[] deg = new int[nVariables];
        for (Atom a : atoms) {
            if (a instanceof UnaryAtom || a instanceof InstantiatedAtom) {
                ++deg[a.sid];
            } else {
                BinaryAtom atom = (BinaryAtom) a;
                ++deg[atom.sid];
                ++deg[atom.oid];
            }
        }
        int r = -1;
        for (int i = 0; i < nVariables; ++i) {
            r = Math.max(r, deg[i]);
        }
        return r;
    }

    public int getMaxNumUniquePredicate() {
        HashMap<Integer, Integer> predicateCount = new HashMap<>();
        int r = 0;
        for (Atom a : atoms) {
            int pid = a.pid;
            if (a instanceof UnaryAtom) {
                pid += 1000000000;
            }
            int newCount = predicateCount.getOrDefault(pid, 0) + 1;
            r = Math.max(newCount, r);
            predicateCount.put(pid, newCount);
        }
        return r;
    }

    // Check if the body monotonic part is connected.
    public boolean bodyConnected() {
        int[] dad = new int[nVariables];
        for (int i = 0; i < nVariables; ++i) {
            dad[i] = -1;
        }
        for (int i = 1; i < atoms.size(); ++i) {
            if (atoms.get(i).negated || (atoms.get(i) instanceof UnaryAtom) || (atoms.get(i) instanceof InstantiatedAtom)) {
                continue;
            }
            BinaryAtom a = (BinaryAtom) atoms.get(i);
            int s = a.sid, o = a.oid;
            while (dad[s] >= 0) {
                s = dad[s];
            }
            while (dad[o] >= 0) {
                o = dad[o];
            }
            if (s != o) {
                dad[s] = o;
            }
        }
        int s = 0, o = 1;
        while (dad[s] >= 0) {
            s = dad[s];
        }
        while (dad[o] >= 0) {
            o = dad[o];
        }
        return s == o;
    }

    // Check if the binary monotonic part is closed.
    public boolean isBinaryClosed() {
        if (nVariables == 0) {
            return false;
        }
        if (!bodyConnected()) {
            return false;
        }
        int[] deg = new int[nVariables];
        for (Atom a : atoms) {
            if (a.negated || (a instanceof UnaryAtom) || (a instanceof InstantiatedAtom)) {
                continue;
            }
            BinaryAtom atom = (BinaryAtom) a;
            ++deg[atom.sid];
            ++deg[atom.oid];
        }
        for (int i = 0; i < nVariables; ++i) {
            if (deg[i] < 2) {
                return false;
            }
        }
        return true;
    }

    // Check if the monotonic part is closed.
    public boolean isClosed() {
        if (nVariables == 0) {
            return false;
        }
        if (!bodyConnected()) {
            return false;
        }
        int[] deg = new int[nVariables];
        for (Atom a : atoms) {
            if (a.negated) {
                continue;
            }
            if (a instanceof UnaryAtom || a instanceof InstantiatedAtom) {
                ++deg[a.sid];
            } else {
                BinaryAtom atom = (BinaryAtom) a;
                ++deg[atom.sid];
                ++deg[atom.oid];
            }
        }
        for (int i = 0; i < nVariables; ++i) {
            if (deg[i] < 2) {
                return false;
            }
        }
        return true;
    }

    public Rule cloneRule() {
        Rule r = new Rule(nRelations);
        r.sourceScr = new double[nRelations];
        r.sourceHeadCoverage = new double[nRelations];
        Arrays.fill(r.sourceHeadCoverage, 1e9); // Some very big number
        if (stats != null) {
            for (int i = 0; i < nRelations; ++i) {
                r.sourceScr[i] = stats.scr[i];
                r.sourceHeadCoverage[i] = stats.headCoverage[i];
            }
        } else {
            if (sourceScr != null) {
                for (int i = 0; i < nRelations; ++i) {
                    r.sourceScr[i] = sourceScr[i];
                }
            }
            if (sourceHeadCoverage != null) {
                // TODO
                // This piece of code should not be executed because exception is only added after the rule is actually
                // matched.
                for (int i = 0; i < nRelations; ++i) {
                    r.sourceHeadCoverage[i] = sourceHeadCoverage[i];
                }
            }
        }
        r.nVariables = nVariables;
        r.nRelations = nRelations;
        r.atoms = new ArrayList<>();
        // This fix to prevent different threads from accessing the same first atom when outputting.
        if (atoms.size() > 0) {
            r.atoms.add(new BinaryAtom(true, false, 0, -1, 1));
            for (int i = 1; i < atoms.size(); ++i) {
                r.atoms.add(atoms.get(i));
            }
        }
        return r;
    }

    // Add a dangling atom with predicate id. Variable "forward" indicates that the predicate is linked from the old
    // variable or from the new variable. If the rule is empty, the "sid" and "forward" doesn't have effect since the
    // predicate is always linked from variable 0 to variable 1.
    public Rule addDanglingAtom(int sharedVariableId, int pid, boolean forward) {
        Rule r = this.cloneRule();
        if (nVariables == 0) {
            r.atoms.add(new BinaryAtom(true, false, 0, pid, 1));
            r.nVariables = 2;
        } else {
            if (forward) {
                r.atoms.add(new BinaryAtom(true, false, sharedVariableId, pid, nVariables));
            } else {
                r.atoms.add(new BinaryAtom(true, false, nVariables, pid, sharedVariableId));
            }
            ++r.nVariables;
        }
        return r;
    }

    // Return null if the atom is already added in either negated or non-negated version.
    // If negated is true, this is actually exception atom.
    public Rule addClosingInstantiatedAtom(int sid, int pid, int value, boolean negated, boolean reversed) {
        Rule r = this.cloneRule();
        for (Atom a : r.atoms) {
            if (a instanceof InstantiatedAtom) {
                InstantiatedAtom atom = (InstantiatedAtom) a;
                if (atom.sid == sid && atom.pid == pid && atom.value == value) {
                    return null;
                }
            }
        }
        r.atoms.add(new InstantiatedAtom(false, negated, reversed, sid, pid, value));
        return r;
    }

    // Return null if the atom is already added in either negated or non-negated version.
    // If negated is true, this is actually exception atom.
    public Rule addClosingUnaryAtom(int sid, int pid, boolean negated) {
        Rule r = this.cloneRule();
        for (Atom a : r.atoms) {
            if (a instanceof UnaryAtom) {
                UnaryAtom atom = (UnaryAtom) a;
                if (atom.sid == sid && atom.pid == pid) {
                    return null;
                }
            }
        }
        r.atoms.add(new UnaryAtom(false, negated, sid, pid));
        return r;
    }

    // Return null if the atom is already added in either negated or non-negated version.
    // If negated is true, this is actually exception atom.
    public Rule addClosingBinaryAtom(int sid, int pid, int oid, boolean negated) {
        Rule r = this.cloneRule();
        // Checking if there exist another edge in the body (any predicate/direction) between the sid and oid of the
        // closing binary atom.
        if (!negated) {
            for (int i = 1; i < r.atoms.size(); ++i) {
                Atom a = r.atoms.get(i);
                if (a instanceof BinaryAtom) {
                    BinaryAtom atom = (BinaryAtom) a;
                    if ((atom.sid == sid && atom.oid == oid) || (atom.sid == oid && atom.oid == sid)) {
                        return null;
                    }
                }
            }
        }
        for (Atom a : r.atoms) {
            if (a instanceof BinaryAtom) {
                BinaryAtom atom = (BinaryAtom) a;
                if (atom.sid == sid && atom.pid == pid && atom.oid == oid) {
                    return null;
                }
            }
        }
        r.atoms.add(new BinaryAtom(false, negated, sid, pid, oid));
        if (sid == 0 && oid == 1) {
            r.sourceScr[pid] = -1; // Not compute rules having this head.
        }
        return r;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(encode()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        throw new RuntimeException("Implement this function if using hashtable to track duplicated rules.");
    }

    // nextPermutation from index start to the end.
    private boolean nextPermutation(int[] mapping, int start) {
        for (int i = mapping.length - 2; i >= start; --i) {
            if (mapping[i] < mapping[i + 1]) {
                int j = i + 1;
                while (j + 1 < mapping.length && mapping[j + 1] > mapping[i]) {
                    ++j;
                }
                int k = mapping[i];
                mapping[i] = mapping[j];
                mapping[j] = k;
                for (j = i + 1, k = mapping.length - 1; j < k; ++j, --k) {
                    int l = mapping[j];
                    mapping[j] = mapping[k];
                    mapping[k] = l;
                }
                return true;
            }
        }
        return false;
    }

    // Magic function to encode the rule with a long number.
    // Only work when having at least 2 variables which belong to the head of the rule.
    public long encode() {
        long hashCode = 1;
        int[] mapping = new int[nVariables];
        for (int i = 0; i < nVariables; ++i) {
            mapping[i] = i;
        }
        do {
            long mappingCode = 0;
            for (int i = 0; i < nVariables; ++i) {
                long varCode = 0;
                for (int j = 1; j < atoms.size(); ++j) {
                    Atom a = atoms.get(j);
                    if (mapping[a.sid] != i) {
                        continue;
                    }
                    if (a instanceof InstantiatedAtom) {
                        InstantiatedAtom atom = (InstantiatedAtom) a;
                        varCode += R_POW[atom.pid] * E_POW[atom.value] * INSTANTIATED_SIGN * (atom.negated ?
                                NEGATION_SIGN : 1) * (atom
                                .reversed ? REVERSED_SIGN : 1);
                    } else
                    if (a instanceof UnaryAtom) {
                        UnaryAtom atom = (UnaryAtom) a;
                        varCode += R_POW[atom.pid] * UNARY_SIGN * (atom.negated ? NEGATION_SIGN : 1);
                    } else {
                        BinaryAtom atom = (BinaryAtom) a;
                        varCode += R_POW[atom.pid] * E_POW[mapping[atom.oid]] * (atom.negated ? NEGATION_SIGN : 1);
                    }
                }
                mappingCode += varCode * V_POW[i];
            }

            hashCode *= mappingCode;
        } while (nextPermutation(mapping, 2));
        return hashCode + nVariables;
    }


    private String getAtomString(Atom a, String[] relationsString, String[] typesString, String[] entitiesString) {
        if (a instanceof InstantiatedAtom) {
            InstantiatedAtom atom = (InstantiatedAtom) a;
            StringBuilder sb = new StringBuilder(atom.negated ? "not " : "");
            if (atom.reversed) {
                sb.append(relationsString[atom.pid]).append("(%").append(entitiesString[atom.value])
                        .append("%, V").append(atom.sid).append(")");
            } else {
                sb.append(relationsString[atom.pid]).append("(V").append(atom.sid).append(", %")
                        .append(entitiesString[atom.value]).append("%)");
            }
            return sb.toString();
        } else if (a instanceof UnaryAtom) {
            UnaryAtom atom = (UnaryAtom) a;
            StringBuilder sb = new StringBuilder(atom.negated ? "not " : "");
            sb.append(typesString[atom.pid]).append("(V").append(atom.sid).append(")");
            return sb.toString();
        } else {
            BinaryAtom atom = (BinaryAtom) a;
            StringBuilder sb = new StringBuilder(atom.negated ? "not " : "");
            if (atom.pid < 0) { // This is never executed. atom.pid is always >= 0.
                sb.append(relationsString[-1 - atom.pid]).append("(V").append(atom.oid).append
                        (", V").append
                        (atom.sid).append
                        (")");
            } else {
                sb.append(relationsString[atom.pid]).append("(V").append(atom.sid).append
                        (", V").append
                        (atom.oid).append
                        (")");
            }
            return sb.toString();
        }
    }

    public String getString(String[] relationsString, String[] typesString, String[] entitiesString) {
        if (atoms.size() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(getAtomString(atoms.get(0), relationsString, typesString, entitiesString)).append(" " +
                ":- ");
        for (int i = 1; i < atoms.size(); ++i) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(getAtomString(atoms.get(i), relationsString, typesString, entitiesString));
        }
        return sb.toString().trim();
    }

    public String getDisjunctionString(int pid1, int pid2, String[] relationsString, String[] typesString, String[]
            entitiesString) {
        if (atoms.size() == 0) {
            return null;
        }
        int oldPid = atoms.get(0).pid;
        atoms.get(0).pid = pid1;
        StringBuilder sb = new StringBuilder(getAtomString(atoms.get(0), relationsString, typesString, entitiesString)).append(" " +
                "OR ");
        atoms.get(0).pid = pid2;
        sb.append(getAtomString(atoms.get(0), relationsString, typesString, entitiesString)).append(" :- ");
        atoms.get(0).pid = oldPid;
        for (int i = 1; i < atoms.size(); ++i) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(getAtomString(atoms.get(i), relationsString, typesString, entitiesString));
        }
        return sb.toString().trim();
    }

}
