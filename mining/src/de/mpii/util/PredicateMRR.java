package de.mpii.util;

import de.mpii.embedding.EmbeddingClient;
import de.mpii.embedding.TransEClient;
import de.mpii.mining.graph.KnowledgeGraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by hovinhthinh on 3/7/18.
 */
public class PredicateMRR {
    // args: <workspace>
    public static void main(String[] args) throws Exception {
        args = "../data/imdb".split("\\s++");

        String workspace = args[0];
        KnowledgeGraph knowledgeGraph = new KnowledgeGraph(workspace);
        EmbeddingClient embeddingClient = new TransEClient(workspace, "L1");

        ArrayList<Triple>[] total = new ArrayList[knowledgeGraph.nRelations];
        for (int i = 0; i < knowledgeGraph.nRelations; ++i) {
            total[i] = new ArrayList<>();
        }
        BufferedReader idealIn = new BufferedReader(new InputStreamReader(new FileInputStream(new File(workspace +
                "/ideal.data.txt"))));
        String line;
        while ((line = idealIn.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            String[] arr = line.split("\t");
            if (arr[1].equals("<type>") || arr[1].equals("<subClassOf>")) {
                continue;
            }
            if (knowledgeGraph.entitiesStringMap.containsKey(arr[0]) && knowledgeGraph.entitiesStringMap.containsKey(arr[2]) &&
                    knowledgeGraph.relationsStringMap.containsKey(arr[1])) {
                int s = knowledgeGraph.entitiesStringMap.get(arr[0]);
                int p = knowledgeGraph.relationsStringMap.get(arr[1]);
                int o = knowledgeGraph.entitiesStringMap.get(arr[2]);
                if (knowledgeGraph.trueFacts.containFact(s, p, o)) {
                    continue;
                }
                total[p].add(new Triple(s, p, o));
            }
        }
        idealIn.close();

        for (int i = 0; i < knowledgeGraph.nRelations; ++i) {
            Collections.shuffle(total[i]);
            int size = Math.min(total[i].size(), 100);
            if (size == 0) {
                continue;
            }
            double mrr = 0;
            for (int j = 0; j < size; ++j) {
                mrr += embeddingClient.getInvertedRank(total[i].get(j).s, i, total[i].get(j).o) / size;
            }
            System.out.printf("%s\t%d\t%.3f\n", knowledgeGraph.relationsString[i], size, mrr);
        }

    }

    static class Triple {
        int s, p, o;

        public Triple(int s, int p, int o) {
            this.s = s;
            this.p = p;
            this.o = o;
        }
    }
}
