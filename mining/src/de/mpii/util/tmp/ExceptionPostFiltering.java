package de.mpii.util.tmp;

import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;
import de.mpii.util.IO;
import de.mpii.util.Infer;

import java.io.PrintWriter;
import java.util.HashSet;

/**
 * Created by hovinhthinh on 4/3/18.
 */
public class ExceptionPostFiltering {
    public static KnowledgeGraph knowledgeGraph;

    // <workspace> <sorted_file_output_from_system>
    public static void main(String[] args) throws Exception {
        args = ("../data/fb15k-new/ ../exp3/fb15k.na4nv3nna1nupa0mc.2ms10ec.01ew.3hc.01nbpa3.sorted ../exp3/fb15k" +
                ".na4nv3nna1nupa0mc.2ms10ec.01ew.3hc.01nbpa3.sorted.postfiltered").split
                ("\\s++");
        knowledgeGraph = Infer.knowledgeGraph = new KnowledgeGraph(args[0]);

        PrintWriter out = IO.openForWrite(args[2]);
        int processed = 0;
        for (String line : IO.readlines(args[1])) {
            ++ processed;
            if (processed % 1000 == 0) {
                System.out.println("Done: " + processed);
            }
            if (!line.contains(", not")) {
                continue;
            }
            String rule = line.split("\t")[0];

            Rule r = Infer.parseRule(knowledgeGraph, rule);
            int pid = r.atoms.get(0).pid;

            for (int i = r.atoms.size() - 1; i >= 0; --i) {
                if (r.atoms.get(i).negated) {
                    r.atoms.get(i).negated = false;
                }
            }
            HashSet<SOInstance> hornInstances = Infer.matchRule(r);
            boolean flag = false;
            for (SOInstance instance : hornInstances) {
                if (knowledgeGraph.trueFacts.containFact(instance.subject, pid, instance.object)) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                continue;
            }
            System.out.println(line);
            out.println(line);
        }
        out.close();
    }
}
