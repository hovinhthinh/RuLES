package de.mpii.util;

import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.InstantiatedAtom;
import de.mpii.mining.atom.UnaryAtom;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/27/17.
 */
public class Infer {
    public static final Logger LOGGER = Logger.getLogger(Infer.class.getName());

    public static KnowledgeGraph knowledgeGraph;

    public static Rule parseRule(KnowledgeGraph graph, String ruleString) {
        Rule r = new Rule(0);
        HashMap<String, Integer> varsMap = new HashMap();
        for (int i = 0; i < ruleString.length(); ++i) {
            int j = i;
            while (ruleString.charAt(j) != ')') {
                ++j;
            }
            String str = ruleString.substring(i, j + 1);
            //

            boolean negated = false;
            if (str.startsWith("not ")) {
                negated = true;
                str = str.substring(4);
            }
            int index = str.indexOf("(");
            String predicate = str.substring(0, index);
            String vars = str.substring(index);
            if (vars.contains(", ")) {
                index = vars.indexOf(", ");
                String subject = vars.substring(1, index);
                String object = vars.substring(index + 2, vars.length() - 1);
                if (subject.startsWith("%") || object.startsWith("%")) {
                    // Instantiated
                    if (object.startsWith("%")) {
                        if (!varsMap.containsKey(subject)) {
                            varsMap.put(subject, varsMap.size());
                        }
                        int sid = varsMap.get(subject),
                                pid = graph.relationsStringMap.get(predicate),
                                value = knowledgeGraph.entitiesStringMap.get(object.substring(1, object.length() - 1));
                        r.atoms.add(new InstantiatedAtom(false, negated, false, sid, pid, value));
                    } else {
                        if (!varsMap.containsKey(object)) {
                            varsMap.put(object, varsMap.size());
                        }
                        int sid = varsMap.get(object),
                                pid = graph.relationsStringMap.get(predicate),
                                value = knowledgeGraph.entitiesStringMap.get(subject.substring(1, subject.length() - 1));
                        r.atoms.add(new InstantiatedAtom(false, negated, true, sid, pid, value));
                    }
                } else {
                    // Binary
                    if (!varsMap.containsKey(subject)) {
                        varsMap.put(subject, varsMap.size());
                    }
                    if (!varsMap.containsKey(object)) {
                        varsMap.put(object, varsMap.size());
                    }
                    int sid = varsMap.get(subject),
                            pid = graph.relationsStringMap.get(predicate),
                            oid = varsMap.get(object);
                    r.atoms.add(new BinaryAtom(false, negated, sid, pid, oid));
                }
            } else {
                // Unary
                String subject = vars.substring(1, vars.length() - 1);
                if (!varsMap.containsKey(subject)) {
                    varsMap.put(subject, varsMap.size());
                }
                int sid = varsMap.get(subject),
                        pid = graph.typesStringMap.get(predicate);
                r.atoms.add(new UnaryAtom(false, negated, sid, pid));
            }
            //
            i = j + 1;
            while (i < ruleString.length() - 1 && ruleString.charAt(i) == ' ') {
                ++i;
            }
            if (i < ruleString.length()) {
                if (ruleString.charAt(i) == ':') {
                    i += 2;
                } else {
                    i += 1;
                }
            }
        }
        r.nVariables = varsMap.size();
        return r;
    }

    private static boolean duplicatedVar(int variableValue[], int newV) {
        for (int i = 0; i < variableValue.length; ++i) {
            if (variableValue[i] == newV) {
                return true;
            }
        }
        return false;
    }

    private static void recur(Rule rule, int position, int variableValues[], HashSet<SOInstance> headInstances) {
        if (position == rule.atoms.size()) {
            headInstances.add(new SOInstance(variableValues[0], variableValues[1]));
            return;
        }
        Atom a = rule.atoms.get(position);
        if (a instanceof InstantiatedAtom) {
            InstantiatedAtom atom = (InstantiatedAtom) a;
            if (variableValues[a.sid] == -1) {
                // This case only happens for positive atom.
                variableValues[a.sid] = -1;
                throw new RuntimeException("To be implemented");
            } else {
                boolean hasEdge = atom.reversed ? knowledgeGraph.trueFacts.containFact(atom.value,
                        atom.pid, variableValues[atom.sid]) : knowledgeGraph.trueFacts.containFact
                        (variableValues[atom.sid], atom.pid, atom.value);
                if (hasEdge == a.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, headInstances);
            }
        } else if (a instanceof UnaryAtom) {
            if (variableValues[a.sid] == -1) {
                // This case only happens for positive atom.
                for (int t : knowledgeGraph.typeInstances[a.pid]) {
//                    if (duplicatedVar(variableValues, t)) {
//                        continue;
//                    }
                    variableValues[a.sid] = t;
                    recur(rule, position + 1, variableValues, headInstances);
                    variableValues[a.sid] = -1;
                }
            } else {
                boolean hasType = knowledgeGraph.trueTypes.containType(variableValues[a.sid], a.pid);
                if (hasType == a.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, headInstances);
            }
        } else {
            BinaryAtom atom = (BinaryAtom) a;
            if (variableValues[atom.sid] == -1 && variableValues[atom.oid] == -1) {
                for (SOInstance so : knowledgeGraph.pidSOInstances[atom.pid]) {
//                    if (duplicatedVar(variableValues, so.subject) || duplicatedVar(variableValues, so.object) || so
//                            .subject == so.object) {
//                        continue;
//                    }
                    variableValues[atom.sid] = so.subject;
                    variableValues[atom.oid] = so.object;
                    recur(rule, position + 1, variableValues, headInstances);
                    variableValues[atom.sid] = variableValues[atom.oid] = -1;
                }
            } else if (variableValues[atom.sid] == -1 || variableValues[atom.oid] == -1) {
                if (variableValues[atom.oid] == -1) {
                    for (KnowledgeGraph.OutgoingEdge e : knowledgeGraph.outEdges[variableValues[atom.sid]]) {
                        if (e.pid != atom.pid) {
                            continue;
                        }
//                        if (duplicatedVar(variableValues, e.oid)) {
//                            continue;
//                        }
                        variableValues[atom.oid] = e.oid;
                        recur(rule, position + 1, variableValues, headInstances);
                        variableValues[atom.oid] = -1;
                    }
                } else {
                    for (KnowledgeGraph.OutgoingEdge e : knowledgeGraph.outEdges[variableValues[atom.oid]]) {
                        if (-e.pid - 1 != atom.pid) {
                            continue;
                        }
//                        if (duplicatedVar(variableValues, e.oid)) {
//                            continue;
//                        }
                        variableValues[atom.sid] = e.oid;
                        recur(rule, position + 1, variableValues, headInstances);
                        variableValues[atom.sid] = -1;
                    }
                }
            } else {
                boolean hasFact = knowledgeGraph.trueFacts.containFact(variableValues[atom.sid], atom.pid,
                        variableValues[atom.oid]);
                if (hasFact == atom.negated) {
                    return;
                }
                recur(rule, position + 1, variableValues, headInstances);
            }
        }
    }

    public static HashSet<SOInstance> matchRule(Rule r) {
        HashSet<SOInstance> headInstances = new HashSet<>();
        int[] variableValues = new int[r.nVariables];
        Arrays.fill(variableValues, -1);
        recur(r, 1, variableValues, headInstances);

        return headInstances;
    }

    // args: <workspace> <file> <top> <new_facts> <predicate>
    // Process first <top> rules of the <file> (top by lines, not by scr)
    public static void main(String[] args) throws Exception {
//        args = "../data/fb15k-new/ ../msarin/fb15k.amie.pca.2 50 tmp -s10".split("\\s++");

        int mins = 0;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].startsWith("-s")) {
                mins = Integer.parseInt(args[i].substring(2));
                String[] temp = args;
                args = new String[temp.length - 1];
                int count = 0;
                for (int j = 0; j < temp.length; ++j) {
                    if (!temp[j].startsWith("-s")) {
                        args[count++] = temp[j];
                    }
                }
                break;
            }
        }

        int top = Integer.parseInt(args[2]);
        knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]))));
        String line;
        int ruleCount = 0;
        String predicate = null;
        if (args.length > 4) {
            predicate = args[4];
        }
        KnowledgeGraph.FactEncodedSet mined = new KnowledgeGraph.FactEncodedSet();
        int unknownNum = 0;
        int total = 0;
        double averageQuality = 0;
        ArrayList<Pair<Double, Integer>> spearman = new ArrayList<>();
        while ((line = in.readLine()) != null) {
            if (line.isEmpty() || ruleCount >= top) {
                break;
            }
            String rule = line.split("\t")[0];
            Rule r = parseRule(knowledgeGraph, rule);
            if (predicate != null && !knowledgeGraph.relationsString[r.atoms.get(0).pid].equals(predicate)) {
                continue;
            }
            ++ruleCount;
            LOGGER.info("Inferring rule: " + rule);
            HashSet<SOInstance> instances = matchRule(r);
            System.out.println("body_support: " + instances.size());
            int pid = r.atoms.get(0).pid;
            int localNumTrue = 0;
            int localPredict = 0;

            for (SOInstance so : instances) {
                if (!knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                    ++localPredict;
                    boolean unknown = !knowledgeGraph.idealFacts.containFact(so.subject, pid, so.object);
                    if (!unknown) {
                        ++localNumTrue;
                    }
                    if (!mined.containFact(so.subject, pid, so.object)) {
                        mined.addFact(so.subject, pid, so.object);
                        ++total;
                        if (unknown) {
                            ++unknownNum;
                        }
                        out.printf("%s\t%s\t%s\t%s\n", knowledgeGraph.entitiesString[so.subject], knowledgeGraph
                                .relationsString[pid], knowledgeGraph.entitiesString[so.object], (unknown == false) ?
                                "TRUE" : "null");
                    }
                }
            }
            if (localPredict == 0 || localNumTrue < mins) {
                --ruleCount;
                continue;
            }
            averageQuality += ((double) localNumTrue) / localPredict;
            LOGGER.info(String.format("quality = %.3f", ((double) localNumTrue) / localPredict));
            spearman.add(new Pair<>(((double) localNumTrue) / localPredict, 1 + top - ruleCount));
        }
        Collections.sort(spearman, new Comparator<Pair<Double, Integer>>() {
            @Override
            public int compare(Pair<Double, Integer> o1, Pair<Double, Integer> o2) {
                return Double.compare(o1.first, o2.first);
            }
        });
        double spearCo = 0;
        for (int i = 0; i < spearman.size(); ++i) {
            spearCo += (i + 1 - spearman.get(i).second) * (i + 1 - spearman.get(i).second);
        }
        spearCo = 1 - 6 * spearCo / top / (top * top - 1);
        in.close();
        out.close();
        LOGGER.info(String.format("#predictions = %d, known_rate = %.3f", total, 1 - ((double) unknownNum / total)));
        LOGGER.info(String.format("#average_quality = %.3f", averageQuality / top));
        LOGGER.info(String.format("Spearman = %.3f", spearCo));
        if (ruleCount != top) {
            LOGGER.warning("Not enough number of requested rules");
        }
    }
}
