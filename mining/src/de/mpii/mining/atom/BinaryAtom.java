package de.mpii.mining.atom;

/**
 * Created by hovinhthinh on 11/13/17.
 */
public class BinaryAtom extends Atom {
    public int oid;

    public BinaryAtom(boolean dangling, boolean negated, int sid, int pid, int oid) {
        super(dangling, negated);
        this.sid = sid;
        this.pid = pid;
        this.oid = oid;
    }
}
