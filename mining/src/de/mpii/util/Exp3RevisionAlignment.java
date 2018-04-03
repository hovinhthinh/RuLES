package de.mpii.util;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hovinhthinh on 4/1/18.
 */
public class Exp3RevisionAlignment {
    // <output from ours> <output from rumis> <out>
    public static void main(String[] args) throws Exception {
        args = "../exp3/wiki44k.embed.10.ec02.xyz ../exp3/wiki44k.rumis.10.ec2.same.fixed ../exp3/wiki44k.revision".split
                ("\\s++");
        Map<String, String> horn2ex = new HashMap<>();
        for (String line : IO.readlines(args[1])) {
            int pos = line.indexOf(", not");
            String horn = line.substring(0, pos);
            horn2ex.put(horn, line);
        }
        PrintWriter embed = IO.openForWrite(args[2] + ".embed"), rumis = IO.openForWrite(args[2] + ".rumis");
        for (String line : IO.readlines(args[0])) {
            line = line.replaceAll("V0", "X").replaceAll("V1", "Z").replaceAll("V2", "Y");
            int pos = line.indexOf(", not");
            String horn = line.substring(0, pos);
            if (horn2ex.containsKey(horn)) {
                embed.println(line);
                rumis.println(horn2ex.get(horn));
            }
        }
        embed.close();
        rumis.close();
    }
}
