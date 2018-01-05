package de.mpii;

import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
import de.mpii.mining.atom.UnaryAtom;
import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/27/17.
 */
public class InferDisjunction {
    public static class FactEncodedSet {
        private static final long BASE = 1000000000;
        private static final long BASE2 = 10000;
        private HashSet<Long> set = new HashSet<>();

        public static long encode(int subject, int predicate1, int predicate2, int object) {
            if (predicate1 > predicate2) {
                int swap = predicate1;
                predicate1 = predicate2;
                predicate2 = swap;
            }
            return (((long) subject) * BASE + ((long) predicate1) * BASE2 + predicate2) * BASE + object;
        }

        public void addFact(int subject, int predicate1, int predicate2, int object) {
            set.add(encode(subject, predicate1, predicate2, object));
        }

        public boolean containFact(int subject, int predicate1, int predicate2, int object) {
            return set.contains(encode(subject, predicate1, predicate2, object));
        }
    }

    public static final Logger LOGGER = Logger.getLogger(InferDisjunction.class.getName());

    public static KnowledgeGraph knowledgeGraph;

    public static Rule parseRule(KnowledgeGraph graph, String ruleString) {
        ruleString = ruleString.substring(ruleString.indexOf(":") + 3);
        Rule r = new Rule(0);
        HashMap<String, Integer> varsMap = new HashMap();
        varsMap.put("V0", 0);
        varsMap.put("V1", 1);
        r.atoms.add(new BinaryAtom(false, false, 0, -1, 1));
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
                // Binary
                String subject = vars.substring(1, index);
                if (!varsMap.containsKey(subject)) {
                    varsMap.put(subject, varsMap.size());
                }
                String object = vars.substring(index + 2, vars.length() - 1);
                if (!varsMap.containsKey(object)) {
                    varsMap.put(object, varsMap.size());
                }
                int sid = varsMap.get(subject),
                        pid = graph.relationsStringMap.get(predicate),
                        oid = varsMap.get(object);
                r.atoms.add(new BinaryAtom(false, negated, sid, pid, oid));
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
        if (a instanceof UnaryAtom) {
            if (variableValues[a.sid] == -1) {
                // This case only happens for positive atom.
                for (int t : knowledgeGraph.typeInstances[a.pid]) {
                    if (duplicatedVar(variableValues, t)) {
                        continue;
                    }
                    variableValues[a.sid] = t;
                    recur(rule, position + 1, variableValues, headInstances);
                }
                variableValues[a.sid] = -1;
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
                    if (duplicatedVar(variableValues, so.subject) || duplicatedVar(variableValues, so.object)) {
                        continue;
                    }
                    variableValues[atom.sid] = so.subject;
                    variableValues[atom.oid] = so.object;
                    recur(rule, position + 1, variableValues, headInstances);
                }
                variableValues[atom.sid] = variableValues[atom.oid] = -1;
            } else if (variableValues[atom.sid] == -1 || variableValues[atom.oid] == -1) {
                if (variableValues[atom.oid] == -1) {
                    for (KnowledgeGraph.OutgoingEdge e : knowledgeGraph.outEdges[variableValues[atom.sid]]) {
                        if (e.pid != atom.pid) {
                            continue;
                        }
                        if (duplicatedVar(variableValues, e.oid)) {
                            continue;
                        }
                        variableValues[atom.oid] = e.oid;
                        recur(rule, position + 1, variableValues, headInstances);
                    }
                    variableValues[atom.oid] = -1;
                } else {
                    for (KnowledgeGraph.OutgoingEdge e : knowledgeGraph.outEdges[variableValues[atom.oid]]) {
                        if (-e.pid - 1 != atom.pid) {
                            continue;
                        }
                        if (duplicatedVar(variableValues, e.oid)) {
                            continue;
                        }
                        variableValues[atom.sid] = e.oid;
                        recur(rule, position + 1, variableValues, headInstances);
                    }
                    variableValues[atom.sid] = -1;
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

    // args: <workspace> <file> <top> <new_facts>
    // Process first <top> rules of the <file> (top by lines, not by scr)
    public static void main(String[] args) throws Exception {
//        args = new String[]{"../data/imdb/", "../data/imdb/disjunction.ew08.txt.rm0.03",
//                "20", "tmp"};
        int top = Integer.parseInt(args[2]);
        knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]))));
        String line;
        int ruleCount = 0;

        FactEncodedSet mined = new FactEncodedSet();
        int unknownNum = 0;
        int total = 0;
        double totalInc = 0, totalConf = 0;
        while ((line = in.readLine()) != null) {
            ++ruleCount;
            if (line.isEmpty() || ruleCount > top) {
                break;
            }
            String[] arr = line.split("\t");
            totalInc += Double.parseDouble(arr[10]);
            totalConf += Double.parseDouble(arr[8]);
            String rule = arr[0];
            LOGGER.info("Inferring rule: " + rule);
            Rule r = parseRule(knowledgeGraph, rule);
            HashSet<SOInstance> instances = matchRule(r);
            System.out.println("body_support: " + instances.size());
            rule = rule.substring(0, rule.indexOf(" :"));
            String pid1String = rule.substring(0, rule.indexOf("("));
            String pid2String = rule.substring(rule.indexOf("OR") + 3, rule.lastIndexOf("("));

            int pid1 = knowledgeGraph.relationsStringMap.get(pid1String);
            int pid2 = knowledgeGraph.relationsStringMap.get(pid2String);

            boolean reversed = false;
            if (rule.contains("(V1, V0)")) {
                reversed = true;
            }
            for (SOInstance so : instances) {
                if (!reversed) {
                    if (!mined.containFact(so.subject, pid1, pid2, so.object) && !knowledgeGraph.trueFacts.containFact(so
                            .subject, pid1, so.object) && !knowledgeGraph.trueFacts.containFact(so
                            .subject, pid2, so.object)) {
                        mined.addFact(so.subject, pid1, pid2, so.object);
                        ++total;
                        boolean unknown = !knowledgeGraph.idealFacts.containFact(so.subject, pid1, so.object) &&
                                !knowledgeGraph.idealFacts.containFact(so.subject, pid2, so.object);
                        if (unknown) {
                            ++unknownNum;
                        }
                        out.printf("%s\t%s\t%s\t%s\t%s\n", knowledgeGraph.entitiesString[so.subject], knowledgeGraph
                                .relationsString[pid1], knowledgeGraph.relationsString[pid2], knowledgeGraph.entitiesString[so.object], (unknown == false) ?
                                "TRUE" : "null");
                    }
                } else {
                    if (!mined.containFact(so.subject, pid1, -1 - pid2, so.object) && !knowledgeGraph.trueFacts
                            .containFact(so.subject, pid1, so.object) && !knowledgeGraph.trueFacts.containFact(so
                            .object, pid2, so.subject)) {
                        mined.addFact(so.subject, pid1, -1 - pid2, so.object);
                        ++total;
                        boolean unknown = !knowledgeGraph.idealFacts.containFact(so.subject, pid1, so.object) &&
                                !knowledgeGraph.idealFacts.containFact(so.object, pid2, so.subject);
                        if (unknown) {
                            ++unknownNum;
                        }
                        out.printf("%s\t%s\t(R)%s\t%s\t%s\n", knowledgeGraph.entitiesString[so.subject], knowledgeGraph
                                .relationsString[pid1], knowledgeGraph.relationsString[pid2], knowledgeGraph.entitiesString[so.object], (unknown == false) ?
                                "TRUE" : "null");
                    }
                }
            }
        }
        in.close();
        out.close();
        LOGGER.info(String.format("#predictions = %d, unknown_rate = %.3f, average_econf = %.3f, average_econf_inc = " +
                        "%.3f", total,
                (double)
                        unknownNum / total, totalConf / top, totalInc / top));
    }
}
