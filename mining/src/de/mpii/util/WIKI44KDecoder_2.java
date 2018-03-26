package de.mpii.util;


import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Created by hovinhthinh on 3/26/18.
 */
public class WIKI44KDecoder_2 {
    private static class Entity {
        String entity;
        String description;

        @Override
        public String toString() {
            return entity + "\t" + description;
        }
    }

    // args: <Freebase/wikidata mapping file> <meta file>
    // write output to id2info
    public static void main(String[] args) throws Exception {
        org.json.simple.parser.JSONParser parser = new JSONParser();
//        args = new String[]{"../data/wiki44k/meta.txt", "dump.gz", "e2des"};

        int nEntities = 0;
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(args[0]))));
        String line = in.readLine();
        nEntities = Integer.parseInt(line.split("\t")[0]);

        HashMap<String, Entity> e2e = new HashMap<>();
        for (int i = 0; i < nEntities; ++i) {
            Entity entity = new Entity();
            entity.entity = in.readLine();
            String e = entity.entity.substring(entity.entity.lastIndexOf("_") + 1, entity.entity.length() - 1);
            e2e.put(e, entity);
        }
        in.close();

        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(args[1])))));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(args[2]))));
        in.readLine();
        while ((line = in.readLine()) != null) {
            if (line.charAt(line.length() - 1) == ',') {
                line = line.substring(0, line.length() - 1);
                JSONObject obj = (JSONObject) parser.parse(line);
                String id = (String) obj.get("id");
                if (e2e.containsKey(id)) {
                    Entity e = e2e.get(id);
                    try {
                        String desc = (String) ((JSONObject) ((JSONObject) obj.get("descriptions")).get("en")).get("value");
                        e.description = desc;
                    } catch (Exception ex) {
                    }
//                    e.description = desc;
                    System.out.println(e.toString());
                }
            }
        }
        for (Map.Entry<String, Entity> e : e2e.entrySet()) {
            System.out.println(e.getValue().toString());
        }
        in.close();
        out.close();
    }

}
