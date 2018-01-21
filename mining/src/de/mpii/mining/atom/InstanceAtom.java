package de.mpii.mining.atom;

/**
 * Created by hovinhthinh on 1/20/18.
 */
public class InstanceAtom extends Atom {
    public int value;
    public boolean reversed;

    public InstanceAtom(boolean dangling, boolean negated, boolean reversed, int sid, int pid, int value) {
        super(dangling, negated);
        this.reversed = reversed;
        this.sid = sid;
        this.pid = pid;
        this.value = value;
    }
}
