package de.mpii.embedding;

/**
 * Created by hovinhthinh on 11/13/17.
 */

// Embeddding Client should support concurrent queries.
public interface EmbeddingClient {
    double getScore(int subject, int predicate, int object);

    double getInvertedRank(int subject, int predicate, int object);
}
