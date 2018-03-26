package de.mpii.util;

import java.io.*;
import java.util.HashSet;

/**
 * Created by hovinhthinh on 3/6/18.
 */
public class ProcessDesc {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream
                ("../data/wiki44k/entities_description.txt")));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream
                ("../data/wiki44k/tmp")));
        String line;
        HashSet<String> goodId = new HashSet<>();
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String arr[] = line.split("\t");
            String e = arr[0];
            StringBuilder d = new StringBuilder(arr[1].toLowerCase());
            for (int i = 0; i < d.length(); ++i) {
                if (Character.isLetterOrDigit(d.charAt(i)) || d.charAt(i) == '.' || d.charAt(i) == '-' || d.charAt(i)
                        == '\'') {
                    continue;
                }
                d.setCharAt(i, ' ');
            }
            String newD = d.toString().replaceAll("\\s++", " ").trim();
            if (newD.split(" ").length < 1) {
                continue;
            }
            out.println(e + "\t" + newD);
            goodId.add(e);
        }
        in.close();
        out.close();

        in = new BufferedReader(new InputStreamReader(new FileInputStream("../data/wiki44k/ideal.data.txt")));
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream
                ("../data/wiki44k/ideal.data.new.txt")));
        HashSet<String> has = new HashSet<>();
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String arr[] = line.split("\t");
            if (goodId.contains(arr[0]) && goodId.contains(arr[2])) {
                out.println(line);
                has.add(arr[0]);
                has.add(arr[2]);
            }
        }
        in.close();
        out.close();

        in = new BufferedReader(new InputStreamReader(new FileInputStream("../data/wiki44k/tmp")));
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream
                ("../data/wiki44k/entities_description.new.txt")));
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String arr[] = line.split("\t");
            if (has.contains(arr[0])) {
                out.println(line);
            }
        }
        in.close();
        out.close();
    }
}
