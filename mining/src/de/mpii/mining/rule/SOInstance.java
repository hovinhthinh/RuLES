package de.mpii.mining.rule;

/**
 * Created by hovinhthinh on 11/13/17.
 */
public class SOInstance {
    private static final int HASHCODE_BASE = 999983;

    public int subject, object;

    public SOInstance(int subject, int object) {
        this.subject = subject;
        this.object = object;
    }

    @Override
    public int hashCode() {
        return subject * HASHCODE_BASE + object;
    }

    @Override
    public boolean equals(Object obj) {
        SOInstance i = (SOInstance) obj;
        return subject == i.subject && object == i.object;
    }
}
