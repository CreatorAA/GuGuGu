package online.pigeonshouse.gugugu.utils.thread;

import java.util.*;
import java.util.concurrent.BlockingQueue;

public class EvenDistribution<E> implements DistributionStrategy<E> {

    @Override
    public Map<Integer, List<E>> distribute(
            Collection<E> tasks,
            List<BlockingQueue<E>> privateQs,
            BlockingQueue<E> publicQ) {

        int totalQueues = privateQs.size() + 1;
        int taskCount = tasks.size();

        if (taskCount == 0) {
            return Collections.emptyMap();
        }

        if (taskCount <= totalQueues || totalQueues == 1) {
            return distributeExtremeCases(tasks, totalQueues);
        }

        Map<Integer, List<E>> distribution = new HashMap<>();
        for (int i = 0; i < totalQueues; i++) {
            distribution.put(i, new ArrayList<>(taskCount / totalQueues + 1));
        }

        if (taskCount > 100 && tasks instanceof List) {
            distributeListBatch((List<E>) tasks, totalQueues, distribution);
        } else if (taskCount > 50) {
            distributeCollectionBatch(tasks, totalQueues, distribution);
        } else {
            distributeByRoundRobin(tasks, totalQueues, distribution);
        }

        return distribution;
    }

    private Map<Integer, List<E>> distributeExtremeCases(Collection<E> tasks, int totalQueues) {
        Map<Integer, List<E>> distribution = new HashMap<>();
        for (int i = 0; i < totalQueues; i++) {
            distribution.put(i, new ArrayList<>());
        }

        if (totalQueues == 1) {
            distribution.get(0).addAll(tasks);
        } else {
            Iterator<E> iterator = tasks.iterator();
            for (int i = 0; i < totalQueues && iterator.hasNext(); i++) {
                distribution.get(i).add(iterator.next());
            }
        }

        return distribution;
    }

    private void distributeByRoundRobin(Collection<E> tasks, int totalQueues,
                                        Map<Integer, List<E>> distribution) {
        Iterator<E> taskIterator = tasks.iterator();
        int currentQueue = 0;

        while (taskIterator.hasNext()) {
            E task = taskIterator.next();
            distribution.get(currentQueue).add(task);

            currentQueue = (currentQueue + 1) % totalQueues;
        }
    }

    private void distributeListBatch(List<E> tasks, int totalQueues,
                                     Map<Integer, List<E>> distribution) {
        int taskCount = tasks.size();

        int baseCount = taskCount / totalQueues;
        int remainder = taskCount % totalQueues;

        int startIndex = 0;
        for (int queueIndex = 0; queueIndex < totalQueues; queueIndex++) {
            int countForThisQueue = baseCount + (queueIndex < remainder ? 1 : 0);

            if (countForThisQueue > 0) {
                List<E> queueTasks = distribution.get(queueIndex);
                int endIndex = startIndex + countForThisQueue;

                for (int i = startIndex; i < endIndex; i++) {
                    queueTasks.add(tasks.get(i));
                }

                startIndex = endIndex;
            }
        }
    }

    private void distributeCollectionBatch(Collection<E> tasks, int totalQueues,
                                           Map<Integer, List<E>> distribution) {
        int taskCount = tasks.size();
        int baseCount = taskCount / totalQueues;
        int remainder = taskCount % totalQueues;

        Iterator<E> iterator = tasks.iterator();

        for (int queueIndex = 0; queueIndex < totalQueues; queueIndex++) {
            int countForThisQueue = baseCount + (queueIndex < remainder ? 1 : 0);
            List<E> queueTasks = distribution.get(queueIndex);

            if (queueTasks instanceof ArrayList) {
                ((ArrayList<E>) queueTasks).ensureCapacity(countForThisQueue);
            }

            for (int i = 0; i < countForThisQueue && iterator.hasNext(); i++) {
                queueTasks.add(iterator.next());
            }
        }
    }
}