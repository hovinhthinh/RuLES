import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class PrecisionRecallScoreLocalLambda {
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

        public static double distance(Embedding subject, Embedding predicate, Embedding object) {
            double r = 0;
            for (int i = 0; i < predicate.value.length; ++i) {
                r += Math.abs(subject.value[i] + predicate.value[i]
                        - object.value[i]);
            }
            return r;
        }
    }

    static class Triple {
        int s, p, o;
        int rank;
        //    int sf, pf, of;
        ArrayList<Integer> rankf = new ArrayList<>();
    }

    static class LambdaWorker implements Runnable {
        public static final Random random = new Random();
        AtomicInteger counter;
        ArrayList<Triple> triples;
        HashSet<Long> facts_encoded_set;
        Embedding[] entitiesEmbedding, relationsEmbedding;
        int start, jump;

        public static int N_FALSE = 2;

        public LambdaWorker(ArrayList<Triple> triples, HashSet<Long> facts_encoded_set,
                            Embedding[] entitiesEmbedding, Embedding[] relationsEmbedding,
                            int start, int jump, AtomicInteger counter) {
            this.triples = triples;
            this.facts_encoded_set = facts_encoded_set;
            this.entitiesEmbedding = entitiesEmbedding;
            this.relationsEmbedding = relationsEmbedding;
            this.start = start;
            this.jump = jump;
            this.counter = counter;
        }

        int getRank(int s, int p, int o) {
            int r = 0;
            double d = Fact.distance(entitiesEmbedding[s],
                    relationsEmbedding[p], entitiesEmbedding[o]);
            for (int k = 0; k < entitiesEmbedding.length; ++k) {
                if (k == s || k == o)
                    continue;
                if (!facts_encoded_set.contains(Fact.encode(k, p, o))
                        && Fact.distance(entitiesEmbedding[k],
                        relationsEmbedding[p],
                        entitiesEmbedding[o]) < d) {
                    ++r;
                }
                if (!facts_encoded_set.contains(Fact.encode(s, p, k))
                        && Fact.distance(entitiesEmbedding[s],
                        relationsEmbedding[p],
                        entitiesEmbedding[k]) < d) {
                    ++r;
                }
            }
            return r;
        }

        @Override
        public void run() {
            for (int i = start; i < triples.size(); i += jump) {
                Triple f = triples.get(i);
                f.rank = getRank(f.s, f.p, f.o);

                for (int j = 0; j < N_FALSE; ++j) {
                    int x = j % 2;
                    if (x == 0) {
                        int sf;
                        do {
                            sf = random.nextInt(entitiesEmbedding.length);
                        } while (sf == f.s || sf == f.o || facts_encoded_set.contains(Fact.encode(sf, f.p, f.o)));
                        f.rankf.add(getRank(sf, f.p, f.o));
                    } else {
                        int of;
                        do {
                            of = random.nextInt(entitiesEmbedding.length);
                        } while (of == f.s || of == f.o || facts_encoded_set.contains(Fact.encode(f.s, f.p, of)));
                        f.rankf.add(getRank(f.s, f.p, of));
                    }
                }
                counter.incrementAndGet();
            }
        }
    }

    public static String WORKSPACE;

    public static String META, EMBEDDING, TRAIN, TEST;


    public static final int N_COMP = 50;
    public static final int N_WORKER = 50;
    public static final int MAX_PREDICATE_TEST = 200;
    public static final double PR_LIM = 0.95;

    static int nEntities, nRelations;

    static Embedding[] entitiesEmbedding, relationsEmbedding;

    static HashSet<Long> facts_encoded_set = new HashSet<>();

    ArrayList<Triple> triples = new ArrayList<>();
    AtomicInteger counter;
    String[] entities, relations;

    HashMap<String, Integer> entitiesMap, relationsMap;

    public void readData() throws Exception {
        entitiesMap = new HashMap<>();
        relationsMap = new HashMap<>();
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

        BufferedReader in_t = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(TRAIN))));
        String line;
        while ((line = in_t.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            split = line.split("\t");
            if (split.length != 3) {
                continue;
            }

            facts_encoded_set
                    .add(Fact.encode(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt
                            (split[2])));
        }
        in_t.close();
        in_t = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(TEST))));

        HashMap<Integer, Integer> predicateCount = new HashMap<>();

        ArrayList<Triple> testFacts = new ArrayList<>();
        while ((line = in_t.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            split = line.split("\t");
            if (split.length != 3) {
                continue;
            }
            Integer s = entitiesMap.get(split[0]);
            Integer p = relationsMap.get(split[1]);
            Integer o = entitiesMap.get(split[2]);
            if (s == null || p == null || o == null) {
                continue;
            }

            Triple t = new Triple();
            t.s = s;
            t.p = p;
            t.o = o;

            testFacts.add(t);

        }

        for (int i = 0; i < 5; ++i) {
            Collections.shuffle(testFacts);
        }

        for (Triple t : testFacts) {
            if (!predicateCount.containsKey(t.p)) {
                predicateCount.put(t.p, 0);
            }
            if (predicateCount.get(t.p) >= MAX_PREDICATE_TEST) {
                continue;
            }
            triples.add(t);
            predicateCount.put(t.p, predicateCount.get(t.p) + 1);
        }
        in_t.close();
    }

    String process(ArrayList<Triple> data) {
//        Collections.sort(data, new Comparator<Triple>() {
//            @Override
//            public int compare(Triple o1, Triple o2) {
//                return o1.rank - o2.rank;
//            }
//        });
//        double pr = -1, rc = -1;
//        int cap = -1;
//        for (int i = 0; i < data.size(); ++i) {
//            int currentCap = data.get(i).rank;
//            int numF = 0;
//            for (int j = 0; j < data.size(); ++j) {
//                for (int f : data.get(j).rankf) {
//                    if (f <= currentCap) {
//                        ++numF;
//                    }
//                }
//            }
//            double currentPr = ((double) i + 1) / (numF + i + 1);
//            if (currentPr >= PR_LIM) {
//                pr = currentPr;
//                rc = ((double) (i + 1)) / data.size();
//                cap = currentCap;
//            }
//        }
//
//        return String.format("%d\t%s\t%d\t%.3f\t%.3f\t%d", data.get(0).p, relations[data.get(0).p], cap, pr, rc, data
//                .size());

        double mrr = 0;
        for (Triple t : data) {
            mrr += 1.0f / (t.rank + 1);
        }
        mrr /= data.size();
        return String.format("%d\t%s\t%.12f\t%d", data.get(0).p, relations[data.get(0).p], mrr, data
                .size());
    }

    PrecisionRecallScoreLocalLambda() throws Exception {
        System.out.println("Reading data.");
        readData();
        System.out.println("Processing.");

        ArrayList<Thread> threads = new ArrayList<>();
        counter = new AtomicInteger();
        for (int i = 0; i < N_WORKER; ++i) {
            Thread t = new Thread(
                    new LambdaWorker(triples, facts_encoded_set, entitiesEmbedding,
                            relationsEmbedding, i, N_WORKER, counter));
            threads.add(t);
        }

        Thread monitor = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    System.out.println("Sampled: " + counter.get() + "/" + triples.size());
                    try {
                        Thread.sleep(2000);
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
        System.out.println("Outputting.");
        PrintWriter out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(new File(WORKSPACE + "/pr_rc_input_local_lambda")))));
        HashMap<Integer, ArrayList<Triple>> group = new HashMap<>();

        for (Triple f : triples) {
            if (!group.containsKey(f.p)) {
                group.put(f.p, new ArrayList<Triple>());
            }
            group.get(f.p).add(f);
        }
        for (Integer k : group.keySet()) {
            String str = process(group.get(k));
            System.out.println(str);
            out.println(str);
        }
        out.close();
        System.out.println("Done.");
    }

    public static void main(String[] args) throws Exception {
//        args = new String[]{"../data/imdb/"};
        WORKSPACE = args[0];

        META = WORKSPACE + "/meta.txt";
        EMBEDDING = WORKSPACE + "/embedding";
        TRAIN = WORKSPACE + "/train.txt";
        TEST = WORKSPACE + "/test.data.txt";

        new PrecisionRecallScoreLocalLambda();
    }
}

