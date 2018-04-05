package de.mpii.util.tmp;

import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.mining.rule.Rule;
import de.mpii.mining.rule.SOInstance;
import de.mpii.util.IO;
import de.mpii.util.Infer;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by hovinhthinh on 4/3/18.
 */
public class ExceptionPostFiltering {
    public static KnowledgeGraph knowledgeGraph;

    // <workspace> <sorted_file_output_from_system>
    public static final double EXCEPTION_CONFIDENCE = 0.05;
    public static final double HORN_MIN_CONF = 0.0;
    public static final double HORN_MAX_CONF = 0.8;

    public static void main(String[] args) throws Exception {
        args = ("../data/fb15k-new/ ../exp3_new/fb.xyzna4nv3nna1ec0mc.1hc.01ms10ew.3.exceptionpostfilter.sorted " +
                "../exp3_new/fb15k.xyz.conf08.ec01").split
                ("\\s++");
//        args = ("../data/wiki44k/ ../exp3_new/wiki.na4nv3nna1ec0mc.1hc.01ms10ew.3.exceptionpostfilter.sorted " +
//                "../exp3_new/wiki.xyz.conf08.ec01").split
//                ("\\s++");
        knowledgeGraph = Infer.knowledgeGraph = new KnowledgeGraph(args[0]);

        PrintWriter out = IO.openForWrite(args[2]);
        int processed = 0;
        Set<String> chosenHorn = new HashSet<>();
        for (String line : IO.readlines(args[1])) {
            ++processed;
            if (processed % 1000 == 0) {
                System.out.println("Done: " + processed);
            }
            if (!line.contains(", not")) {
                continue;
            }
            String hornString = line.substring(0, line.indexOf(", not"));
            if (chosenHorn.contains(hornString)) {
                continue;
            }
            // process
            String rule = line.split("\t")[0];

            Rule r = Infer.parseRule(knowledgeGraph, rule);
            int pid = r.atoms.get(0).pid;
            //

            Rule horn = r.cloneRule();
            for (int i = horn.atoms.size() - 1; i >= 0; --i) {
                if (horn.atoms.get(i).negated) {
                    horn.atoms.remove(i);
                }
            }

            HashSet<SOInstance> hornSO = Infer.matchRule(horn);
            int hornSup = 0;
            for (SOInstance so : hornSO) {
                if (knowledgeGraph.trueFacts.containFact(so.subject, pid, so.object)) {
                    ++hornSup;
                }
            }
            double hornConf = (double) hornSup / hornSO.size();
            if (hornConf < HORN_MIN_CONF || hornConf > HORN_MAX_CONF) {
                continue;
            }
            HashSet<SOInstance> exceptionSO = Infer.matchRule(r);

            double econf = (double) (hornSO.size() - exceptionSO.size()) / (hornSO.size() - hornSup);
            if (econf < EXCEPTION_CONFIDENCE) {
                continue;
            }

            System.out.println(line + "\teconf:" + "\t" + econf);
            out.println(line + "\teconf:" + "\t" + econf);
            chosenHorn.add(hornString);
        }
        out.close();
    }
}
