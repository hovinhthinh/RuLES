package de.mpii.util.tmp;

import de.mpii.mining.graph.KnowledgeGraph;
import de.mpii.util.IO;

import java.io.PrintWriter;

/**
 * Created by hovinhthinh on 4/5/18.
 */
public class Neural {
    public static void main(String[] args) throws Exception {
        for (String line : IO.readlines("../data/family/neurallp.txt")) {
            for (int i = 0; i < line.length(); ++i) {
                if (line.startsWith("inv_", i)) {
                    int a = i, b = i;
                    while (line.charAt(a) != '(') {
                        ++a;
                    }
                    while (line.charAt(b) != ')') {
                        ++b;
                    }
                    StringBuilder sb = new StringBuilder(line);
                    char x = sb.charAt(a + 1);
                    sb.setCharAt(a + 1, sb.charAt(b - 1));
                    sb.setCharAt(b - 1, x);
                    line = sb.toString();
                    line = line.substring(0, i) + line.substring(i + 4);
                }
            }
            System.out.println(line);
        }
    }
}
