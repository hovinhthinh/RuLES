package de.mpii.mining.atom;

/**
 * Created by hovinhthinh on 11/13/17.
 */
public class UnaryAtom extends Atom {
    public UnaryAtom(boolean dangling, boolean negated, int sid, int pid) {
        super(dangling, negated);
        this.sid = sid;
        this.pid = pid;
    }
}
