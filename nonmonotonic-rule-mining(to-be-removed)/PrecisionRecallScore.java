import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;


public class PrecisionRecallScore {
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

    public static String META, EMBEDDING, TRAIN, TEST, VALID;


    public static final int N_COMP = 50;
    public static final int N_WORKER = 50;

    static int nEntities, nRelations;

    static Embedding[] entitiesEmbedding, relationsEmbedding;

    static HashSet<Long> facts_encoded_set = new HashSet<>();


    ArrayList<Triple> triples = new ArrayList<>();
    AtomicInteger counter;

    public void readData() throws Exception {
        BufferedReader in_m = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(META))));
        String[] split = in_m.readLine().trim().split("\\s++");

        nEntities = Integer.parseInt(split[0]);
        nRelations = Integer.parseInt(split[1]);

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
        for (String file : new String[]{TEST, VALID}) {
            in_t = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(file))));
            while ((line = in_t.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                split = line.split("\t");
                if (split.length != 3) {
                    continue;
                }
                Triple t = new Triple();
                t.s = Integer.parseInt(split[0]);
                t.p = Integer.parseInt(split[1]);
                t.o = Integer.parseInt(split[2]);

                facts_encoded_set.add(Fact.encode(t.s, t.p, t.o));
                triples.add(t);
            }
            in_t.close();
        }

    }

    PrecisionRecallScore() throws Exception {
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
                    System.out.println("Sampled: " + counter.get());
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
                new OutputStreamWriter(new FileOutputStream(new File(WORKSPACE + "/pr_rc_input")))));
        for (Triple f : triples) {
            out.println(1 + "\t" + f.rank);
            for (int k : f.rankf) {
                out.println(0 + "\t" + k);
            }
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
        TEST = WORKSPACE + "/test.txt";
        VALID = WORKSPACE + "/valid.txt";

        new PrecisionRecallScore();
    }
}

