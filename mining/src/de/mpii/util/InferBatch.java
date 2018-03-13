package de.mpii.util;

import de.mpii.mining.atom.Atom;
import de.mpii.mining.atom.BinaryAtom;
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
public class InferBatch {
    public static final Logger LOGGER = Logger.getLogger(InferBatch.class.getName());

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
                    if (duplicatedVar(variableValues, so.subject) || duplicatedVar(variableValues, so.object) || so
                            .subject == so.object) {
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


    static class RuleStats {
        String rule;
        public double pcaconf;
        public double conf;
        public double mmr;
        public double quality;

        public RuleStats(String rule, double pcaconf, double conf, double mmr, double quality) {
            this.rule = rule;
            this.pcaconf = pcaconf;
            this.conf = conf;
            this.mmr = mmr;
            this.quality = quality;
        }
    }

    static class CompareF implements Comparator<RuleStats> {
        double lambda;

        public CompareF(double lambda) {
            this.lambda = lambda;
        }

        @Override
        public int compare(RuleStats o1, RuleStats o2) {
            return Double.compare(o2.conf * (1 - lambda) + o2.mmr * lambda, o1.conf * (1 - lambda) + o1.mmr * lambda);
        }
    }

    // args: <workspace> <file> <range> <output>
    public static void main(String[] args) throws Exception {
//        args = "../data/fb15k-new/ ../data/fb15k-new/amie.xyz.hole.sp10 10 ../data/fb15k-new/amie.xyz.hole.sp10.txt"
//                .split
//                ("\\s++");
        knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]))));
        String line;
        ArrayList<RuleStats> stats = new ArrayList<>();
        int range = Integer.parseInt(args[2]);
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            String[] arr = line.split("\t");
            Rule r = parseRule(knowledgeGraph, arr[0]);
            LOGGER.info("Inferring rule: " + arr[0]);
            HashSet<SOInstance> instances = matchRule(r);
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
                }
            }
            double quality = ((double) localNumTrue) / localPredict;
            stats.add(new RuleStats(arr[0], Double.parseDouble(arr[3]), Double.parseDouble(arr[2]), Double
                    .parseDouble(arr[4]), quality));
        }
        in.close();
        //
        double[] pca = new double[stats.size() / range];
        double[][] econf = new double[11][stats.size() / range];

        Collections.sort(stats, new Comparator<RuleStats>() {
            @Override
            public int compare(RuleStats o1, RuleStats o2) {
                return Double.compare(o2.pcaconf, o1.pcaconf);
            }
        });
        double avg = 0;
        for (int i = 0; i < stats.size(); ++i) {
            avg += stats.get(i).quality;
            if (i % range == range - 1) {
                pca[i / range] = avg / (i + 1);
            }
        }
        for (int k = 0; k < 11; ++k) {
            Collections.sort(stats, new CompareF(k * 0.1));
            avg = 0;
            for (int i = 0; i < stats.size(); ++i) {
                avg += stats.get(i).quality;
                if (i % range == range - 1) {
                    econf[k][i / range] = avg / (i + 1);
                }
            }
        }

        out.print("ew\t0\t0.1\t0.2\t0.3\t0.4\t0.5\t0.6\t0.7\t0" +
                ".8\t0.9\t1.0\tpcaconf\n");
        for (int k = 0; k < stats.size() / range; ++k) {
            out.printf("top_%d\t", (k + 1) * range);
            for (int i = 0; i < 11; ++i) {
                out.printf("%.3f\t", econf[i][k]);
            }
            out.printf("%.3f\n", pca[k]);
        }
        out.close();
    }
}
