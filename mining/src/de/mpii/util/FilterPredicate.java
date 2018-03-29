package de.mpii.util;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Created by hovinhthinh on 3/8/18.
 */
public class FilterPredicate {
    static String[] preds = ("<createdBy>\n" +
            "<directedBy>\n" +
            "<created>\n" +
            "<writtenBy>\n" +
            "<directed>\n" +
            "<wrote>\n" +
            "<influences>\n" +
            "<influencedBy>\n" +
            "<hasPredecessor>\n" +
            "<hasSuccessor>\n" +
            "<hasLanguage>\n" +
            "<achievedBy>\n" +
            "<hasProductionLanguage>\n" +
            "<hasKeyword>\n" +
            "<hasWonPrize>\n" +
            "<producedIn>\n" +
            "<isCitizenOf>\n" +
            "<hasGenre>").split("\\s++");

    public static void main(String[] args) throws Exception {
        String file = "../data/imdb/amie.xyz.stats.sp10";
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file +
                "predfil"))));
        HashSet<String> x = new HashSet<>();
        x.addAll(Arrays.asList(preds));
        System.out.println(x.size());
        String line;
        while ((line = in.readLine()) != null) {
            if (x.contains(line.split("\\(")[0])) {
                out.println(line);
            }
        }
        in.close();
        out.close();
    }
}
