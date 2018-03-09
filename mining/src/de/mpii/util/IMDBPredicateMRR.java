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
public class IMDBPredicateMRR {
    static class Triple {
        int s, p, o;

        public Triple(int s, int p, int o) {
            this.s = s;
            this.p = p;
            this.o = o;
        }
    }

    public static void main(String[] args) throws Exception {
        String workspace = "../data/imdb/";
        KnowledgeGraph knowledgeGraph = new KnowledgeGraph(workspace);
        EmbeddingClient embeddingClient = new TransEClient(workspace, "L1");

        for (int i = 0; i < knowledgeGraph.nRelations; ++i) {
            ArrayList<Triple> total = new ArrayList<>();
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
                    if (p != i || knowledgeGraph.trueFacts.containFact(s, p, o)) {
                        continue;
                    }
                    total.add(new Triple(s, p, o));
                }
            }
            idealIn.close();
            Collections.shuffle(total);
            int size = Math.min(total.size(), 200);
            if (size == 0) {
                continue;
            }
            double mrr = 0;
            for (int j = 0; j < size; ++j) {
                mrr += embeddingClient.getInvertedRank(total.get(j).s, i, total.get(j).o) / size;
            }
            System.out.printf("%s\t%.3f\n", knowledgeGraph.relationsString[i], mrr);
        }

    }
}
