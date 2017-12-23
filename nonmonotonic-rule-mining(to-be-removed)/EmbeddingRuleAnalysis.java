import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;



public class EmbeddingRuleAnalysis {
    static class Embedding {
        public Embedding(int nComp) {
            value = new double[nComp];
        }

        double[] value;
    }

    static class Fact {
        public static final long ENCODE_BASE = 1000000000;

        String subject, predicate, object;
        int sid, pid, oid;
        double distance;
        int rank;

        String verdict = null;

        public static long encode(int sid, int pid, int oid) {
            long r = 0;
            r += sid;
            r *= ENCODE_BASE;
            r += pid;
            r *= ENCODE_BASE;
            r += oid;
            return r;
        }

        public static double distance(Embedding subject, Embedding predicate,
                                      Embedding object) {
            double r = 0;
            for (int i = 0; i < predicate.value.length; ++i) {
                r += Math.abs(subject.value[i] + predicate.value[i]
                        - object.value[i]);
            }
            return r;
        }
    }

    static class Worker implements Runnable {
        AtomicInteger counter;
        ArrayList<Fact> facts;
        HashSet<Long> facts_encoded_set;
        Embedding[] entitiesEmbedding, relationsEmbedding;
        int start, jump;

        public Worker(ArrayList<Fact> facts, HashSet<Long> facts_encoded_set,
                      Embedding[] entitiesEmbedding, Embedding[] relationsEmbedding,
                      int start, int jump, AtomicInteger counter) {
            this.facts = facts;
            this.facts_encoded_set = facts_encoded_set;
            this.entitiesEmbedding = entitiesEmbedding;
            this.relationsEmbedding = relationsEmbedding;
            this.start = start;
            this.jump = jump;
            this.counter = counter;
        }

        @Override
        public void run() {
            for (int i = start; i < facts.size(); i += jump) {
                Fact f = facts.get(i);
                f.distance = Fact.distance(entitiesEmbedding[f.sid],
                        relationsEmbedding[f.pid], entitiesEmbedding[f.oid]);
                f.rank = 0;
                for (int k = 0; k < entitiesEmbedding.length; ++k) {
                    if (k == f.sid || k == f.oid)
                        continue;
                    if (!facts_encoded_set.contains(Fact.encode(k, f.pid, f.oid))
                            && Fact.distance(entitiesEmbedding[k],
                            relationsEmbedding[f.pid],
                            entitiesEmbedding[f.oid]) < f.distance) {
                        ++f.rank;
                    }
                    if (!facts_encoded_set.contains(Fact.encode(f.sid, f.pid, k))
                            && Fact.distance(entitiesEmbedding[f.sid],
                            relationsEmbedding[f.pid], entitiesEmbedding[k]) < f.distance) {
                        ++f.rank;
                    }
                }
                counter.incrementAndGet();
            }
        }
    }

    public static String WORKSPACE;

    public static String META = "meta.txt";
    public static String EMBEDDING = "embedding";
    public static String TRAIN = "training.data.txt";
    public static String TRUE_FACT = "extension.opm.kg.neg.1.good";
    public static String NULL_FACT = "extension.opm.kg.neg.1.needcheck";
    public static String OUT = "out";

    public static boolean READ_COMMON = false;

    public static final int N_COMP = 50;
    public static final int N_WORKER = 50;

    static int nEntities, nRelations;
    static String[] entities, relations;
    static HashMap<String, Integer> entitiesMap = new HashMap<>(),
            relationsMap = new HashMap<>();

    static Embedding[] entitiesEmbedding, relationsEmbedding;

    static HashSet<Long> facts_encoded_set = new HashSet<>();

    ArrayList<Fact> facts = new ArrayList<>();

    AtomicInteger counter = new AtomicInteger();

    public void readData() throws Exception {
        if (!READ_COMMON) {
            BufferedReader in_m = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(META))));
            String[] split = in_m.readLine().trim().split("\\s++");

            nEntities = Integer.parseInt(split[0]);
            nRelations = Integer.parseInt(split[1]);

            entities = new String[nEntities];
            relations = new String[nRelations];

            for (int i = 0; i < nEntities; ++i) {
                entities[i] = in_m.readLine();
                entitiesMap.put(entities[i], i);
            }
            for (int i = 0; i < nRelations; ++i) {
                relations[i] = in_m.readLine();
                relationsMap.put(relations[i], i);
            }

            in_m.close();
        }
        if (!READ_COMMON) {
            DataInputStream in_e = new DataInputStream(new FileInputStream(
                    new File(EMBEDDING)));
            entitiesEmbedding = new Embedding[nEntities];
            relationsEmbedding = new Embedding[nRelations];
            for (int i = 0; i < nEntities; ++i) {
                entitiesEmbedding[i] = new Embedding(N_COMP);
                for (int j = 0; j < N_COMP; ++j) {
                    entitiesEmbedding[i].value[j] = in_e.readDouble();
                }
            }
            for (int i = 0; i < nRelations; ++i) {
                relationsEmbedding[i] = new Embedding(N_COMP);
                for (int j = 0; j < N_COMP; ++j) {
                    relationsEmbedding[i].value[j] = in_e.readDouble();
                }
            }
            in_e.close();
        }
        if (!READ_COMMON) {
            int n_err = 0;
            BufferedReader in_t = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(TRAIN))));
            String line;
            while ((line = in_t.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    ++n_err;
                    continue;
                }
                String[] split = line.split("\t");
                if (split.length != 3) {
                    ++n_err;
                    continue;
                }
                Fact fact = new Fact();

                fact.subject = split[0];
                fact.predicate = split[1];
                fact.object = split[2];
                Integer i = entitiesMap.get(fact.subject);
                if (i == null) {
                    ++n_err;
                    continue;
                }

                fact.sid = i;
                i = relationsMap.get(fact.predicate);
                if (i == null) {
                    ++n_err;
                    continue;
                }
                fact.pid = i;
                i = entitiesMap.get(fact.object);
                if (i == null) {
                    ++n_err;
                    continue;
                }
                fact.oid = i;

                facts_encoded_set
                        .add(Fact.encode(fact.sid, fact.pid, fact.oid));
            }
            in_t.close();
            System.out.println("Train n_err: " + n_err);
        }

        READ_COMMON = true;

        BufferedReader in_f = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(TRUE_FACT))));
        String line;
        int n_err = 0;
        while ((line = in_f.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                ++n_err;
                continue;
            }
            String[] split = line.split("\t");
            if (split.length != 3) {
                ++n_err;
                continue;
            }
            Fact fact = new Fact();

            fact.subject = split[0];
            fact.predicate = split[1];
            fact.object = split[2];

            if (fact.subject.equals(fact.object)) {
                ++n_err;
                continue;
            }
            Integer i = entitiesMap.get(fact.subject);
            if (i == null) {
                ++n_err;
                continue;
            }

            fact.sid = i;
            i = relationsMap.get(fact.predicate);
            if (i == null) {
                ++n_err;
                continue;
            }

            fact.pid = i;
            i = entitiesMap.get(fact.object);
            if (i == null) {
                ++n_err;
                continue;
            }

            fact.oid = i;
            fact.verdict = "TRUE";
            facts.add(fact);
        }
        in_f.close();
        in_f = new BufferedReader(new InputStreamReader(new FileInputStream(
                new File(NULL_FACT))));
        while ((line = in_f.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                ++n_err;
                continue;
            }
            String[] split = line.split("\t");
            if (split.length != 3) {
                ++n_err;
                continue;
            }
            Fact fact = new Fact();

            fact.subject = split[0];
            fact.predicate = split[1];
            fact.object = split[2];
            if (fact.subject.equals(fact.object)) {
                ++n_err;
                continue;
            }
            Integer i = entitiesMap.get(fact.subject);
            if (i == null) {
                ++n_err;
                continue;
            }

            fact.sid = i;
            i = relationsMap.get(fact.predicate);
            if (i == null) {
                ++n_err;
                continue;
            }

            fact.pid = i;
            i = entitiesMap.get(fact.object);
            if (i == null) {
                ++n_err;
                continue;
            }

            fact.oid = i;

            facts.add(fact);
        }
        in_f.close();
        System.out.println("Fact n_err: " + n_err);

    }

    EmbeddingRuleAnalysis() throws Exception {
        System.out.println("Reading data.");
        readData();
        System.out.println("Processing.");

        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < N_WORKER; ++i) {
            Thread t = new Thread(
                    new Worker(facts, facts_encoded_set, entitiesEmbedding,
                            relationsEmbedding, i, N_WORKER, counter));
            threads.add(t);
        }

        Thread monitor = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    System.out.printf("Processed %d/%d (%.2f)\n",
                            counter.get(), facts.size(),
                            (counter.get() * 1.0f / facts.size()));
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });

        for (Thread t : threads) {
            t.start();
        }
        monitor.start();
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("Processing done.");
        monitor.stop();
        System.out.println("Sorting.");
        Collections.sort(facts, new Comparator<Fact>() {
            @Override
            public int compare(Fact o1, Fact o2) {
                // TODO Auto-generated method stub
                return Double.compare(o1.distance, o2.distance);
            }
        });
        System.out.println("Outputting.");
        PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File(OUT)))));
        for (Fact f : facts) {
            out.printf("%s\t%s\t%s\t%.6f\t%d\t%s\n", f.subject, f.predicate,
                    f.object, f.distance, f.rank, f.verdict);
        }
        out.close();
        System.out.println("Done.");
    }

    // Params: <workspace>
    public static void main(String[] args) throws Exception {
//        args = new String[]{"../data/imdb/"};
        WORKSPACE = args[0];

        META = WORKSPACE + "/meta.txt";
        EMBEDDING = WORKSPACE + "/embedding";
        TRAIN = WORKSPACE + "/training.data.txt";
        TRUE_FACT = WORKSPACE + "/DLV/extension.opm.kg.neg.1.good";
        NULL_FACT = WORKSPACE + "/DLV/extension.opm.kg.neg.1.needcheck";
        OUT = WORKSPACE + "/out";

        Scanner in = new Scanner(new File(WORKSPACE + "/rules.txt"));
        String line;
        PrintWriter out_rule = new PrintWriter(new File(WORKSPACE + "/revised.txt"));
        PrintWriter out = new PrintWriter(new File(WORKSPACE + "/new.facts.txt"));
        int rule_count = 0;
        while (in.hasNextLine()) {
            line = in.nextLine();
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            ++rule_count;
            PrintWriter o = new PrintWriter(new File(WORKSPACE
                    + "/horn-rules.txt"));
            o.println(line);
            o.close();
            System.out.println("Process horn rule: " + line);
            Process p = Runtime.getRuntime().exec(
                    "java -jar rumis-1.0.jar -e=exp -f=" + WORKSPACE
                            + "/ -r=2 -t=1 -d");
            p.waitFor();
            new EmbeddingRuleAnalysis();

            BufferedReader in_o = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(OUT))));
            int n_new_rule = 0;
            int n_null = 0;
            while ((line = in_o.readLine()) != null) {
                if (!line.isEmpty()) {
                    ++n_new_rule;
                    if (line.endsWith("null")) {
                        ++n_null;
                    }
                    out.println(rule_count + "\t" + line);
                }
            }
            in_o.close();
            in_o = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File("revised-rules.txt"))));
            boolean ok_conv = false;
            boolean ok_rule = false;
            int after_ok_conv = 0;
            String conv = "";
            String rule = "";
            while ((line = in_o.readLine()) != null) {
                if (after_ok_conv == 2) {
                    conv = line.substring(line.lastIndexOf(' ') + 1);
                }
                if (ok_rule) {
                    rule = line;
                }
                if (line.equals("OPM Ranking:")) {
                    ok_conv = true;
                }
                if (line.equals("Chosen revised rules:")) {
                    ok_rule = true;
                }
                if (ok_conv) {
                    ++after_ok_conv;
                }
            }
            out_rule.printf("%d\t%s\t%s\t%d\t%.2f\n", rule_count, rule, conv, n_new_rule, n_null * 1.0f / n_new_rule);
            in_o.close();
            out.flush();
            out_rule.flush();
            System.out.println("Revised rule: " + rule);
        }
        in.close();
        out.close();
        out_rule.close();
        Process p = Runtime.getRuntime().exec(
                "rm " + OUT + " " + WORKSPACE + "/horn-rules.txt " + "revised-rules.txt");
        p.waitFor();
    }
}

