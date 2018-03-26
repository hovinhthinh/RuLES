package de.mpii.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by hovinhthinh on 3/26/18.
 */
public class WIKI44KTypeExtract {
    public static void main(String[] args) throws Exception {
        String workspace = args[0];

        BufferedReader metaIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(workspace +
                "/meta.txt"))));
        String[] spl = metaIn.readLine().split("\\s++");
        int nEntities = Integer.parseInt(spl[0]);

        HashMap<String, String> entities = new HashMap();

        for (int i = 0; i < nEntities; ++i) {
            String line = metaIn.readLine();
            String id = line.substring(line.lastIndexOf("_") + 1, line.length() - 1);
            entities.put(id, metaIn.readLine());
        }

        metaIn.close();

        for (String line : DataSamplingPredicates.readlines("/scratch/GW/pool0/hvthinh/type.wiki")) {
            String[] arr = line.split(" ");
            String s = arr[0].substring(arr[0].lastIndexOf("/") + 1, arr[0].length() - 1);
            String o = arr[2].substring(arr[2].lastIndexOf("/") + 1, arr[2].length() - 1);
            if (entities.containsKey(s)) {
                System.out.println(entities.get(s) + "\t<type>\t" + o);
            }
        }
    }
}
