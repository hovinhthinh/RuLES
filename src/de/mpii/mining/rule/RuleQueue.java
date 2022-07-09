package de.mpii.mining.rule;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/13/17.
 */

class CollaborationPriorityQueue<T> {

    private PriorityQueue<T> queue;
    private int numberOfCollaborators;

    private final Object lock = new Object();
    private int currentWaitCount = 0;
    public boolean isEnded = false;

    public CollaborationPriorityQueue(int nCollaborators, Comparator<T> comparator) {
        if (nCollaborators < 1) {
            throw new RuntimeException("nCollaborators must be positive.");
        }
        numberOfCollaborators = nCollaborators;
        queue = new PriorityQueue<>(comparator);
    }

    public boolean push(T element) {
        synchronized (lock) {
            boolean result = queue.add(element);
            lock.notify();
            return result;
        }
    }

    public T pop() {
        synchronized (lock) {
            try {
                while (true) {
                    if (!queue.isEmpty()) {
                        return queue.poll();
                    }
                    ++currentWaitCount;
                    if (currentWaitCount == numberOfCollaborators) {
                        isEnded = true;
                        lock.notify();
                        return null;
                    }
                    lock.wait();
                    --currentWaitCount;
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(CollaborationPriorityQueue.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }
    }

    public int size() {
        return queue.size();
    }
}

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
    private CollaborationPriorityQueue<Rule> rulesQueue;

    private int enqueueLimit;
    private int enqueueCount;
    private int operationCount;
    private int currentNumAtom;

    public RuleQueue(int enqueueLimit, int nWorkers) {
        enqueuedRuleCode = Collections.synchronizedSet(new HashSet<>());
        rulesQueue = new CollaborationPriorityQueue<>(nWorkers, new RuleComparator());

        this.enqueueLimit = enqueueLimit;
        enqueueCount = 0;
        operationCount = 0;
        currentNumAtom = 2;
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
        rulesQueue.push(r);
        ++enqueueCount;
        ++operationCount;
        if (operationCount % OPERATION_LOG_INTERVAL == 0) {
            LOGGER.info("RuleBodyQueueSize: " + rulesQueue.size());
        }
        return true;
    }

    public Rule dequeue() {
        ++operationCount;
        if (operationCount % OPERATION_LOG_INTERVAL == 0) {
            LOGGER.info("RuleBodyQueueSize: " + rulesQueue.size());
        }
        Rule front = rulesQueue.pop();
        return front;
    }
}
