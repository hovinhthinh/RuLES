package de.mpii.mining.rule;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/13/17.
 */
class RuleComparator implements Comparator<Rule> {
    @Override
    public int compare(Rule o1, Rule o2) {
        return o1.atoms.size() - o2.atoms.size();
    }
}

public class RuleQueue {
    public static final Logger LOGGER = Logger.getLogger(RuleQueue.class.getName());

    private static final int OPERATION_LOG_INTERVAL = 100000;

    // Synchronized set.
    private Set<Long> enqueuedRuleCode;

    // Synchronized queue.
    private PriorityBlockingQueue<Rule> rulesQueue;

    private int enqueueLimit;
    private int enqueueCount;
    private int operationCount;

    public RuleQueue(int enqueueLimit) {
        enqueuedRuleCode = Collections.synchronizedSet(new HashSet<>());
        rulesQueue = new PriorityBlockingQueue<>(11, new RuleComparator());

        this.enqueueLimit = enqueueLimit;
        enqueueCount = 0;
        operationCount = 0;
    }

    public int size() {
        return rulesQueue.size();
    }

    public boolean isEmpty() {
        return rulesQueue.size() == 0;
    }

    public boolean enqueue(Rule r) {
        if (enqueueCount >= enqueueLimit) {
            return false;
        }
        long code = r.encode();
        if (enqueuedRuleCode.contains(code)) {
            return false;
        }
        enqueuedRuleCode.add(code);
        rulesQueue.add(r);
        ++enqueueCount;
        ++operationCount;
        if (operationCount % OPERATION_LOG_INTERVAL == 0) {
            LOGGER.info("RuleBodyQueueSize: " + rulesQueue.size());
        }
        return true;
    }

    public Rule dequeue() {
        try {
            ++operationCount;
            if (operationCount % OPERATION_LOG_INTERVAL == 0) {
                LOGGER.info("RuleBodyQueueSize: " + rulesQueue.size());
            }
            // Wait for 15 min before returning.
            return rulesQueue.poll(900, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
