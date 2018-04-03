package de.mpii.util;

import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 3/29/18.
 */
public class InferE {
    public static final Logger LOGGER = Logger.getLogger(InferE.class.getName());

    public static KnowledgeGraph knowledgeGraph;

    // args: <workspace> <file> <top> <new_facts> <predicate>
    // Process first <top> rules of the <file> (top by lines, not by scr)
    public static void main(String[] args) throws Exception {
//        args = "../data/wiki44k/ ../exp3/wiki44k.embed.10.ec02 200 -s10 tmp".split("\\s++");
//        args = "../data/fb15k-new/ ../exp3/fb15k.revision.embed 10 tmp".split("\\s++");
//        args = "../data/wiki44k/ ../exp3/wiki44k.revision.embed 100 tmp".split("\\s++");


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
        knowledgeGraph = Infer.knowledgeGraph = new KnowledgeGraph(args[0]);

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[1])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[3]))));
        String line;
        int ruleCount = 0;
        String predicate = null;
        if (args.length > 4) {
            predicate = args[4];
        }
        KnowledgeGraph.FactEncodedSet mined = new KnowledgeGraph.FactEncodedSet();
//        KnowledgeGraph.FactEncodedSet minedHorn = new KnowledgeGraph.FactEncodedSet();
        int unknownNum = 0;
        int total = 0;
        double averageQuality = 0;
        double averageOldQuality = 0;
        double averageRevision = 0;
        ArrayList<Pair<Double, Integer>> spearman = new ArrayList<>();
        while ((line = in.readLine()) != null) {
            if (line.isEmpty() || ruleCount >= top) {
                break;
            }
            String rule = line.split("\t")[0];
            Rule r = Infer.parseRule(knowledgeGraph, rule);
            if (predicate != null && !knowledgeGraph.relationsString[r.atoms.get(0).pid].equals(predicate)) {
                continue;
            }
            ++ruleCount;
            System.out.println("Inferring rule: " + rule);
            HashSet<SOInstance> instances = Infer.matchRule(r);
            System.out.println("body_support: " + instances.size());
            int pid = r.atoms.get(0).pid;
            int localNumTrue = 0;
            int localPredict = 0;
            int support = 0;

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
                } else {
                    ++support;
                }
            }

            if (localPredict == 0 || support < mins) {
                --ruleCount;
                continue;
            }
            averageQuality += ((double) localNumTrue) / localPredict;
            // calculate revision score
            Rule horn = r.cloneRule();
            for (int i = horn.atoms.size() - 1; i >= 0; --i) {
                if (horn.atoms.get(i).negated) {
                    horn.atoms.remove(i);
                }
            }
            HashSet<SOInstance> hornInstances = Infer.matchRule(horn);
            int oldLocalPredict = 0;
            int oldLocalNumTrue = 0;
            int totalPrevented = 0;
            int totalTruePrevented = 0;
            for (SOInstance so : hornInstances) {
                if (!knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                    ++oldLocalPredict;
                    if (knowledgeGraph.idealFacts.containFact(so.subject, pid, so.object)) {
                        ++oldLocalNumTrue;
                    }
                }
                if (instances.contains(so)) {
                    continue;
                }
                if (knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                    continue;
                }
                ++totalPrevented;
                if (knowledgeGraph.idealFacts.containFact(so.subject, pid, so.object)) {
                    ++totalTruePrevented;
                }
            }
            averageOldQuality += ((double) oldLocalNumTrue) / oldLocalPredict;
            averageRevision += totalPrevented == 0 ? 0 : ((double) totalTruePrevented) / totalPrevented;
            // end of revision
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
        LOGGER.info(String.format("#average_quality = %.3f ; #avg_inc_quality = %.3f", averageQuality / top,
                (averageQuality - averageOldQuality) / top));
        LOGGER.info(String.format("#average_revision = %.3f", 1 - averageRevision / top));
        LOGGER.info(String.format("Spearman = %.3f", spearCo));
        if (ruleCount != top) {
            LOGGER.warning("Not enough number of requested rules");
        }
    }
}
