package de.mpii.util;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by hovinhthinh on 3/26/18.
 */
public class WIKI44KDecoder {
    // args: <Freebase/wikidata mapping file> <meta file>
    // write output to id2info
    public static void main(String[] args) throws Exception {
        args = new String[]{"../data/wiki44k/meta.txt"};

        int nEntities = 0;
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0]))));
        String line = in.readLine();
        nEntities = Integer.parseInt(line.split("\t")[0]);
        WIKI44KDecoder.Entity[] entities = new WIKI44KDecoder.Entity[nEntities];
        for (int i = 0; i < nEntities; ++i) {
            entities[i] = new WIKI44KDecoder.Entity();
            entities[i].entity = in.readLine();
        }
        in.close();
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(new File("e2d_wiki44k"))));
            for (int i = 0; i < nEntities; ++i) {
                line = in.readLine();
                String[] arr = line.split("\t");
                entities[i].entity = arr[0];
                entities[i].description = arr[1];

            }
            in.close();
        } catch (Exception e) {
        }
        int nWorkers = 16;
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < nWorkers; ++i) {
            Thread t = new Thread(new WIKI44KDecoder.Worker(entities, i, nWorkers));
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            t.join();
        }

        PrintWriter out = new PrintWriter(new File("e2d_wiki44k"));
        for (WIKI44KDecoder.Entity e : entities) {
            out.println(e);
        }
        out.close();
    }

    private static class Entity {
        String entity;
        String description;

        @Override
        public String toString() {
            return entity + "\t" + description;
        }
    }

    private static class Worker implements Runnable {
        public WIKI44KDecoder.Entity[] entities;
        public int start, jump;

        public Worker(WIKI44KDecoder.Entity[] entities, int start, int jump) {
            this.entities = entities;
            this.start = start;
            this.jump = jump;
        }

        @Override
        public void run() {
            for (int i = start; i < entities.length; i += jump) {
                if (entities[i].description != null && !entities[i].description.equals("null")) {
                    continue;
                }
                String link = "https://www.wikidata.org/wiki/" + entities[i].entity.substring(entities[i].entity
                        .lastIndexOf("_"), entities[i].entity.length() - 1);
                String html = null;
                for (int k = 0; k < 5; ++k) {
                    html = Crawler.getContentFromUrl(link);
                    if (html != null) {
                        break;
                    }
                }
                String default_str = entities[i].entity.substring(1, entities[i].entity.lastIndexOf("_")).replaceAll
                        ("_", " ");
                if (html == null) {
                    entities[i].description = default_str;
                    System.out.println(entities[i]);
                    continue;
                }
                entities[i].description = TParser.getContent(html, "<div " +
                        "class=\"wikibase-entitytermsview-heading-description \">", "</div>");
                if (entities[i].description != null) {
                    entities[i].description = entities[i].description.replaceAll("\\s++", " ").trim();
                } else {
                    entities[i].description = default_str;
                }
                System.out.println(entities[i]);
            }
        }
    }

}
