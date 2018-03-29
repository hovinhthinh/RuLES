package de.mpii.util;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by hovinhthinh on 3/30/18.
 */
public class IO {
    public static ArrayList<String> readlines(String file) {
        try {
            ArrayList<String> l = new ArrayList<>();
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while ((line = in.readLine()) != null) {
                l.add(line);
            }
            in.close();
            return l;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PrintWriter openForWrite(String file) throws IOException {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file))));
    }
}
