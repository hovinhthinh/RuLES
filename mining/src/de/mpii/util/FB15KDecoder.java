package de.mpii.util;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Created by hovinhthinh on 12/23/17.
 */
public class FB15KDecoder {
    private static class Entity {
        String code;
        String entity;
        String link;
        String description;

        @Override
        public String toString() {
            return code + "\t" + entity + "\t" + link + "\t" + description;
        }
    }

    private static class Worker implements Runnable {
        public Entity[] entities;
        public int start, jump;
        public Map<String, String> id2wiki;

        public Worker(Entity[] entities, int start, int jump, Map<String, String> id2wiki) {
            this.entities = entities;
            this.start = start;
            this.jump = jump;
            this.id2wiki = id2wiki;
        }

        @Override
        public void run() {
            for (int i = start; i < entities.length; i += jump) {
                if (entities[i].entity != null && !entities[i].entity.equals("null")) {
                    continue;
                }
                entities[i].link = id2wiki.get(entities[i].code);
                if (entities[i].link != null) {
                    entities[i].link = entities[i].link.replace("http", "https");

                    String html = Crawler.getContentFromUrl(entities[i].link);
                    if (html == null) {
                        System.out.println("err: " + entities[i].link);
                        continue;
                    }
                    entities[i].entity = TParser.getContent(html, "<span class=\"wikibase-title-label\">", "</span>");

                    entities[i].description = TParser.getContent(html, "<div " +
                            "class=\"wikibase-entitytermsview-heading-description \">", "</div>");
                    if (entities[i].description != null) {
                        entities[i].description = entities[i].description.replaceAll("\\s++", " ");
                    }
                }
                System.out.println(entities[i]);
            }
        }
    }

    // args: <Freebase/wikidata mapping file> <meta file>
    // write output to id2info
    public static void main(String[] args) throws Exception {
        args = new String[]{"../data/fb15k/original/fb2w.nt.gz", "../data/fb15k/meta.txt"};

        Map<String, String> id2wiki = new HashMap<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File
                (args[0])))));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] arr = line.split("\t");
            String id = arr[0].substring(arr[0].lastIndexOf("/"), arr[0].length() - 1).replace('.', '/');
            String wiki = arr[2].substring(1, arr[2].lastIndexOf(">"));
            id2wiki.put(id, wiki);

        }
        in.close();

        int nEntities = 0;
        in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[1]))));
        line = in.readLine();
        nEntities = Integer.parseInt(line.split("\t")[0]);
        Entity[] entities = new Entity[nEntities];
        for (int i = 0; i < nEntities; ++i) {
            entities[i] = new Entity();
            entities[i].code = in.readLine();
        }
        in.close();
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(new File("id2info"))));
            for (int i = 0; i < nEntities; ++i) {
                line = in.readLine();
                String[] arr = line.split("\t");
                entities[i].code = arr[0];
                entities[i].entity = arr[1];
                entities[i].link = arr[2];
                entities[i].description = arr[3];

            }
            in.close();
        } catch (Exception e) {
        }
        int nWorkers = 16;
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < nWorkers; ++i) {
            Thread t = new Thread(new Worker(entities, i, nWorkers, id2wiki));
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            t.join();
        }

        PrintWriter out = new PrintWriter(new File("id2info"));
        for (Entity e : entities) {
            out.println(e);
        }
        out.close();
    }
}
