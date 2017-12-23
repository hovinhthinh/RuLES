import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * Created by hovinhthinh on 10/17/17.
 */
public class RuleStatsLocalLambda {
    public static class Rule {
        public Rule(String rule, double conv, int numFact, double nullRate) {
            this.conv = conv;
            this.rule = rule;
            this.numFact = numFact;
            this.nullRate = nullRate;
            this.mrr = 0;
        }

        String rule;
        double conv;
        int numFact;
        double nullRate;
        int numPositive;

        double mrr;
    }

    // args: <workspace>
    public static void main(String[] args) throws Exception {
//        args = new String[] {"../data/imdb"};
        HashMap<String, Double> lambdaMap = new HashMap<>();

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0] + "/pr_rc_input_local_lambda")
        )));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String arr[] = line.split("\t");
            lambdaMap.put(arr[1], Double.parseDouble(arr[2]));
        }
        in.close();

        TreeMap<Integer, Rule> ruleMap = new TreeMap<>();

        in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0] + "/revised.txt")
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
            Double targetMrr = lambdaMap.get(arr[2]);
            if (targetMrr == null) {
                continue;
            }
            double rank = Integer.parseInt(arr[5]);
            if (1.0f / (rank + 1) > targetMrr) {
                ++r.numPositive;
            }
            r.mrr += 1.0f/(rank + 1);
        }
        in.close();
        for (int i : ruleMap.keySet()) {
            Rule r = ruleMap.get(i);
            System.out.printf("%d\t%s\t%.3f\t%d\t%.2f\t%d\t%.2f\t%.2f\n", i, r.rule, r.conv, r.numFact, r.nullRate, r
                    .numPositive, (r
                    .numPositive *
                    1.0f) / r.numFact, r.mrr / r.numFact);
        }
    }
}
