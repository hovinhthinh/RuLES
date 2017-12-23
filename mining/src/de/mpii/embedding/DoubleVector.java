package de.mpii.embedding;

/**
 * Created by hovinhthinh on 11/4/17.
 */
public class DoubleVector {
    public double[] value;

    public DoubleVector(int length) {
        value = new double[length];
    }

    public int getLength() {
        return value.length;
    }
}