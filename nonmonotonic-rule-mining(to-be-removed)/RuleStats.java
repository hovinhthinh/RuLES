import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.TreeMap;

/**
 * Created by hovinhthinh on 10/17/17.
 */
public class RuleStats {
    public static class Rule {
        public Rule(String rule, double conv, int numFact, double nullRate) {
            this.conv = conv;
            this.rule = rule;
            this.numFact = numFact;
            this.nullRate = nullRate;
        }

        String rule;
        double conv;
        int numFact;
        double nullRate;
        int numPositive;
    }

    // args: <workspace> <lambda+>
    public static void main(String[] args) throws Exception {
//        args = new String[] {"../data/imdb", "6500"};
        int lamda = Integer.parseInt(args[1]);

        TreeMap<Integer, Rule> ruleMap = new TreeMap<>();
        String line;
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0] + "/revised.txt")
        )));
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String arr[] = line.split("\t");
            Rule r = new Rule(arr[1], Double.parseDouble(arr[2]), Integer.parseInt(arr[3]), Double.parseDouble(arr[4]));
            ruleMap.put(Integer.parseInt(arr[0]), r);
        }
        in.close();
        in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0] + "/new.facts.txt"))));
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String arr[] = line.split("\t");
            Rule r = ruleMap.get(Integer.parseInt(arr[0]));
            if (r == null) {
                continue;
            }
            double score = Integer.parseInt(arr[5]);
            if (score <= lamda) {
                ++r.numPositive;
            }
        }
        in.close();
        for (int i : ruleMap.keySet()) {
            Rule r = ruleMap.get(i);
            System.out.printf("%d\t%s\t%.3f\t%d\t%.2f\t%d\t%.2f\n", i, r.rule, r.conv, r.numFact, r.nullRate, r.numPositive, (r
                    .numPositive *
                    1.0f) / r.numFact);
        }
    }
}
