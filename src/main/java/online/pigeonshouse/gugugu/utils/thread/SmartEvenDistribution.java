package online.pigeonshouse.gugugu.utils.thread;

import lombok.Data;

import java.util.*;
import java.util.concurrent.BlockingQueue;

public class SmartEvenDistribution<E> implements DistributionStrategy<E> {

    private final LoadBalanceConfig config;

    private final Map<Integer, QueueLoadHistory> loadHistory = new HashMap<>();
    
    public SmartEvenDistribution() {
        this(new LoadBalanceConfig());
    }
    
    public SmartEvenDistribution(LoadBalanceConfig config) {
        this.config = config;
    }

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

        updateLoadHistory(privateQs, publicQ);

        if (shouldUseLoadBalancedMode(privateQs, publicQ, taskCount)) {
            return loadBalancedDistribute(tasks, privateQs, publicQ, totalQueues);
        } else {
            return enhancedEvenDistribute(tasks, privateQs, publicQ, totalQueues);
        }
    }

    private boolean shouldUseLoadBalancedMode(List<BlockingQueue<E>> privateQs, 
                                            BlockingQueue<E> publicQ, int taskCount) {
        if (taskCount < config.getMinTaskCountForLoadBalance()) {
            return false;
        }

        int maxLoad = getMaxQueueLoad(privateQs, publicQ);
        int minLoad = getMinQueueLoad(privateQs, publicQ);
        
        return (maxLoad - minLoad) >= config.getLoadImbalanceThreshold();
    }

    private Map<Integer, List<E>> loadBalancedDistribute(
            Collection<E> tasks, List<BlockingQueue<E>> privateQs,
            BlockingQueue<E> publicQ, int totalQueues) {
        
        Map<Integer, List<E>> distribution = new HashMap<>();
        for (int i = 0; i < totalQueues; i++) {
            distribution.put(i, new ArrayList<>());
        }

        int[] currentLoads = getCurrentLoads(privateQs, publicQ);
        int[] capacities = calculateQueueCapacities(currentLoads, tasks.size());
        
        if (tasks instanceof List) {
            distributeByCapacity((List<E>) tasks, capacities, distribution);
        } else {
            distributeCollectionByCapacity(tasks, capacities, distribution);
        }
        
        return distribution;
    }

    private Map<Integer, List<E>> enhancedEvenDistribute(
            Collection<E> tasks, List<BlockingQueue<E>> privateQs,
            BlockingQueue<E> publicQ, int totalQueues) {
        
        Map<Integer, List<E>> distribution = new HashMap<>();
        for (int i = 0; i < totalQueues; i++) {
            distribution.put(i, new ArrayList<>());
        }

        int[] adjustments = calculateLoadAdjustments(privateQs, publicQ);
        
        if (tasks instanceof List) {
            distributeWithAdjustments((List<E>) tasks, totalQueues, adjustments, distribution);
        } else {
            distributeCollectionWithAdjustments(tasks, totalQueues, adjustments, distribution);
        }
        
        return distribution;
    }

    private void distributeByCapacity(List<E> tasks, int[] capacities, 
                                    Map<Integer, List<E>> distribution) {
        int taskCount = tasks.size();
        int totalCapacity = Arrays.stream(capacities).sum();
        
        if (totalCapacity == 0) {
            distributeListBatch(tasks, capacities.length, distribution);
            return;
        }

        int startIndex = 0;
        for (int queueIndex = 0; queueIndex < capacities.length; queueIndex++) {
            int capacity = capacities[queueIndex];
            if (capacity > 0) {
                int taskCountForQueue = (int) Math.round((double) capacity / totalCapacity * taskCount);
                taskCountForQueue = Math.min(taskCountForQueue, capacity);
                taskCountForQueue = Math.min(taskCountForQueue, taskCount - startIndex);
                
                if (taskCountForQueue > 0) {
                    List<E> queueTasks = distribution.get(queueIndex);
                    int endIndex = startIndex + taskCountForQueue;
                    
                    for (int i = startIndex; i < endIndex; i++) {
                        queueTasks.add(tasks.get(i));
                    }
                    
                    startIndex = endIndex;
                }
            }
            
            if (startIndex >= taskCount) break;
        }
        
        if (startIndex < taskCount) {
            distributeRemainingTasks(tasks, startIndex, distribution);
        }
    }

    /**
     * 将集合任务按容量分发到各个队列
     * 
     * @param tasks 任务集合
     * @param capacities 各队列容量数组
     * @param distribution 分发结果映射
     */
    private void distributeCollectionByCapacity(Collection<E> tasks, int[] capacities, 
                                              Map<Integer, List<E>> distribution) {
        int taskCount = tasks.size();
        int totalCapacity = Arrays.stream(capacities).sum();
        
        if (totalCapacity == 0) {
            distributeCollectionBatch(tasks, capacities.length, distribution);
            return;
        }

        Iterator<E> taskIterator = tasks.iterator();
        int remainingTasks = taskCount;
        
        for (int queueIndex = 0; queueIndex < capacities.length && remainingTasks > 0; queueIndex++) {
            int capacity = capacities[queueIndex];
            if (capacity > 0) {
                int taskCountForQueue = (int) Math.round((double) capacity / totalCapacity * taskCount);
                taskCountForQueue = Math.min(taskCountForQueue, capacity);
                taskCountForQueue = Math.min(taskCountForQueue, remainingTasks);
                
                if (taskCountForQueue > 0) {
                    List<E> queueTasks = distribution.get(queueIndex);
                    
                    if (queueTasks instanceof ArrayList) {
                        ((ArrayList<E>) queueTasks).ensureCapacity(taskCountForQueue);
                    }
                    
                    for (int i = 0; i < taskCountForQueue && taskIterator.hasNext(); i++) {
                        queueTasks.add(taskIterator.next());
                        remainingTasks--;
                    }
                }
            }
        }
        
        if (remainingTasks > 0 && taskIterator.hasNext()) {
            distributeRemainingCollectionTasks(taskIterator, remainingTasks, distribution);
        }
    }

    private void distributeWithAdjustments(List<E> tasks, int totalQueues, 
                                         int[] adjustments, Map<Integer, List<E>> distribution) {
        int taskCount = tasks.size();
        int baseCount = taskCount / totalQueues;
        int remainder = taskCount % totalQueues;
        
        int[] adjustedCounts = applyLoadAdjustments(baseCount, remainder, adjustments, totalQueues);
        
        int startIndex = 0;
        for (int queueIndex = 0; queueIndex < totalQueues; queueIndex++) {
            int countForThisQueue = adjustedCounts[queueIndex];
            
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

    private void distributeCollectionWithAdjustments(Collection<E> tasks, int totalQueues, 
                                                   int[] adjustments, Map<Integer, List<E>> distribution) {
        List<E> taskList = new ArrayList<>(tasks);
        distributeWithAdjustments(taskList, totalQueues, adjustments, distribution);
    }

    private int[] calculateQueueCapacities(int[] currentLoads, int taskCount) {
        int maxLoad = Arrays.stream(currentLoads).max().orElse(0);
        int[] capacities = new int[currentLoads.length];
        
        for (int i = 0; i < currentLoads.length; i++) {
            capacities[i] = Math.max(0, maxLoad - currentLoads[i] + config.getBaseCapacity());
            
            QueueLoadHistory history = loadHistory.get(i);
            if (history != null && history.isIncreasingTrend()) {
                capacities[i] = Math.max(0, capacities[i] - config.getTrendAdjustment());
            }
        }
        
        return capacities;
    }

    private int[] calculateLoadAdjustments(List<BlockingQueue<E>> privateQs, BlockingQueue<E> publicQ) {
        int totalQueues = privateQs.size() + 1;
        int[] adjustments = new int[totalQueues];
        int avgLoad = getAverageQueueLoad(privateQs, publicQ);
        
        for (int i = 0; i < privateQs.size(); i++) {
            int load = privateQs.get(i).size();
            adjustments[i] = avgLoad - load;
        }
        
        int publicLoad = publicQ.size();
        adjustments[privateQs.size()] = avgLoad - publicLoad;
        
        return adjustments;
    }

    private int[] applyLoadAdjustments(int baseCount, int remainder, int[] adjustments, int totalQueues) {
        int[] counts = new int[totalQueues];
        int totalAdjustment = Arrays.stream(adjustments).sum();
        
        if (totalAdjustment == 0) {
            for (int i = 0; i < totalQueues; i++) {
                counts[i] = baseCount + (i < remainder ? 1 : 0);
            }
            return counts;
        }
        
        double adjustmentFactor = (double) remainder / totalAdjustment;
        for (int i = 0; i < totalQueues; i++) {
            int adjustment = (int) Math.round(adjustments[i] * adjustmentFactor);
            counts[i] = baseCount + adjustment;
            counts[i] = Math.max(0, counts[i]);
        }
        
        return counts;
    }

    private void updateLoadHistory(List<BlockingQueue<E>> privateQs, BlockingQueue<E> publicQ) {
        for (int i = 0; i < privateQs.size(); i++) {
            int load = privateQs.get(i).size();
            loadHistory.computeIfAbsent(i, k -> new QueueLoadHistory(config.getHistorySize()))
                      .recordLoad(load);
        }
        loadHistory.computeIfAbsent(privateQs.size(), k -> new QueueLoadHistory(config.getHistorySize()))
                  .recordLoad(publicQ.size());
    }
    
    private int[] getCurrentLoads(List<BlockingQueue<E>> privateQs, BlockingQueue<E> publicQ) {
        int[] loads = new int[privateQs.size() + 1];
        for (int i = 0; i < privateQs.size(); i++) {
            loads[i] = privateQs.get(i).size();
        }
        loads[privateQs.size()] = publicQ.size();
        return loads;
    }
    
    private int getMaxQueueLoad(List<BlockingQueue<E>> privateQs, BlockingQueue<E> publicQ) {
        return Arrays.stream(getCurrentLoads(privateQs, publicQ)).max().orElse(0);
    }
    
    private int getMinQueueLoad(List<BlockingQueue<E>> privateQs, BlockingQueue<E> publicQ) {
        return Arrays.stream(getCurrentLoads(privateQs, publicQ)).min().orElse(0);
    }
    
    private int getAverageQueueLoad(List<BlockingQueue<E>> privateQs, BlockingQueue<E> publicQ) {
        int[] loads = getCurrentLoads(privateQs, publicQ);
        return (int) Arrays.stream(loads).average().orElse(0);
    }
    
    private void distributeListBatch(List<E> tasks, int totalQueues, Map<Integer, List<E>> distribution) {
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
    
    private void distributeRemainingTasks(List<E> tasks, int startIndex, 
                                        Map<Integer, List<E>> distribution) {
        int remaining = tasks.size() - startIndex;
        int[] queueSizes = distribution.values().stream().mapToInt(List::size).toArray();
        
        for (int i = 0; i < remaining; i++) {
            int targetQueue = findMinLoadQueue(queueSizes);
            distribution.get(targetQueue).add(tasks.get(startIndex + i));
            queueSizes[targetQueue]++;
        }
    }
    
    private void distributeRemainingCollectionTasks(Iterator<E> taskIterator, int remainingTasks,
                                                  Map<Integer, List<E>> distribution) {
        int[] queueSizes = distribution.values().stream().mapToInt(List::size).toArray();
        
        for (int i = 0; i < remainingTasks && taskIterator.hasNext(); i++) {
            int targetQueue = findMinLoadQueue(queueSizes);
            distribution.get(targetQueue).add(taskIterator.next());
            queueSizes[targetQueue]++;
        }
    }
    
    private int findMinLoadQueue(int[] queueSizes) {
        int minIndex = 0;
        for (int i = 1; i < queueSizes.length; i++) {
            if (queueSizes[i] < queueSizes[minIndex]) {
                minIndex = i;
            }
        }
        return minIndex;
    }

    /**
     * 负载均衡配置类
     * 用于配置智能分发策略的各种参数，优化系统性能
     */
    @Data
    public static class LoadBalanceConfig {
        /** 
         * 启用负载均衡模式的最小任务数量
         * 当任务数少于该值时，使用简单的均匀分发策略
         */
        private int minTaskCountForLoadBalance = 10;
        
        /** 
         * 负载不均衡阈值
         * 当最大和最小队列负载差异达到该值时，启用负载均衡模式
         */
        private int loadImbalanceThreshold = 5;
        
        /** 
         * 队列基础容量
         * 用于计算队列可用容量的基础值，防止容量计算为0
         */
        private int baseCapacity = 2;
        
        /** 
         * 趋势调整值
         * 当检测到队列负载呈上升趋势时，减少分配的容量值
         */
        private int trendAdjustment = 1;
        
        /** 
         * 历史记录大小
         * 保存的负载历史记录数量，用于趋势分析
         */
        private int historySize = 5;
    }

    private static class QueueLoadHistory {
        private final int maxSize;
        private final LinkedList<Integer> loads = new LinkedList<>();
        
        public QueueLoadHistory(int maxSize) {
            this.maxSize = maxSize;
        }
        
        public void recordLoad(int load) {
            loads.addLast(load);
            if (loads.size() > maxSize) {
                loads.removeFirst();
            }
        }
        
        public boolean isIncreasingTrend() {
            if (loads.size() < 2) return false;
            
            int increases = 0;
            Iterator<Integer> it = loads.iterator();
            int prev = it.next();
            while (it.hasNext()) {
                int current = it.next();
                if (current > prev) increases++;
                prev = current;
            }
            
            return increases >= loads.size() * 0.6;
        }
    }
}