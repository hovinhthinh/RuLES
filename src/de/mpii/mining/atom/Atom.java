package de.mpii.mining.atom;

/**
 * Created by hovinhthinh on 11/13/17.
 */
public abstract class Atom {
    public boolean dangling, negated;
    public int sid, pid;

    public Atom(boolean dangling, boolean negated) {
        this.dangling = dangling;
        this.negated = negated;
    }
}
