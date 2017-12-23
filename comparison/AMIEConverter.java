import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by hovinhthinh on 12/4/17.
 */
public class AMIEConverter {
    private static class Pair {
        public double pcaconf;
        public String str;

        public Pair(double pcaconf, String str) {
            this.pcaconf = pcaconf;
            this.str = str;
        }
    }

    // args: <file> # contains lines: rule[tab]pcaconf
    public static void main(String[] args) throws Exception {
//        args = new String[]{"../data/imdb/amie.txt.sorted"};

        ArrayList<Pair> arr = new ArrayList<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String parts[] = line.split("\t");
            if (parts.length != 11) {
                continue;
            }
            try {
                Double.parseDouble(parts[parts.length - 1]);
            } catch (Exception e) {
                continue;
            }
            String r[] = parts[0].split("\\s++");
            String head = "";
            String body = "";
            for (int i = 0; i < r.length; i += 3) {
                if (r[i].equals("=>")) {
                    head = r[i + 2] + "(" + r[i + 1] + ", " + r[i + 3] + ")";
                    break;
                }
                String a = r[i + 1] + "(" + r[i] + ", " + r[i + 2] + ")";
                if (!body.isEmpty()) {
                    body += ", ";
                }
                body += a;
            }
            StringBuilder str = new StringBuilder().append(head + " :- " + body);
            for (int i = 1; i < parts.length; ++i) {
                str.append("\t" + parts[i]);
            }
            arr.add(new Pair(Double.parseDouble(parts[3]), str.toString()));
        }
        Collections.sort(arr, new Comparator<Pair>() {
            @Override
            public int compare(Pair o1, Pair o2) {
                return Double.compare(o2.pcaconf, o1.pcaconf);
            }
        });
        for (Pair p : arr) {
            System.out.println(p.str);
        }
        in.close();
    }
}

