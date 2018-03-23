package de.mpii.util;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hovinhthinh on 3/23/18.
 */
public class DataSamplingEntities {
    // args: <file> <threshold> <out>
    public static void main(String[] args) throws Exception {
        ArrayList<String> lines = DataSamplingPredicates.readlines(args[0]);
        HashMap<String, Integer> entity2deg = new HashMap<>();
        for (String line : lines) {
            String arr[] = line.split("\t");
            entity2deg.put(arr[0], entity2deg.getOrDefault(arr[0], 0) + 1);
            entity2deg.put(arr[2], entity2deg.getOrDefault(arr[2], 0) + 1);
        }

        int threshold = Integer.parseInt(args[1]);

        int nE = 0, nF = 0;
        for (Map.Entry<String, Integer> e : entity2deg.entrySet()) {
            if (e.getValue() >= threshold) {
                ++nE;
            }
        }

        PrintWriter out = DataSamplingPredicates.openForWrite(args[2]);
        for (String line : lines) {
            String arr[] = line.split("\t");
            if (entity2deg.get(arr[0]) >= threshold && entity2deg.get(arr[2]) >= threshold) {
                out.println(line);
                ++nF;
            }
        }

        out.close();

        System.out.println("nEntities: " + nE);
        System.out.println("nFacts: " + nF);
    }
}
