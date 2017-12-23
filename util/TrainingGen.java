import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Created by hovinhthinh on 10/18/17.
 */
public class TrainingGen {
    public static double TRAINING_RATE = 0.8;

    // args: <workspace>
    public static void main(String[] args) throws Exception {
//        args = new String[] {"../data/wn18"};
        HashMap<String, ArrayList<String>> map = new HashMap<>();

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0]
                + "/ideal.data.txt"))));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) continue;
            String predicate = line.split("\t")[1];
            if (!map.containsKey(predicate)) {
                map.put(predicate, new ArrayList<String>());
            }
            map.get(predicate).add(line);
        }
        in.close();
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File
                (args[0] + "/training.data.txt")))));
        PrintWriter out_t = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File
                (args[0] + "/test.data.txt")))));
        for (String key : map.keySet()) {
            ArrayList<String> arr = map.get(key);
            Collections.shuffle(arr);
            int lim = (int) Math.ceil(arr.size() * TRAINING_RATE);
            for (int i = 0; i < lim; ++i) {
                out.println(arr.get(i));
            }
            for (int i = lim; i < arr.size(); ++i) {
                out_t.println(arr.get(i));
            }
        }
        out.close();
        out_t.close();
    }
}
