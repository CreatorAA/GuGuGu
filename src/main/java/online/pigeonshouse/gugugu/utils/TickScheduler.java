package online.pigeonshouse.gugugu.utils;

import lombok.Getter;
import online.pigeonshouse.gugugu.event.MinecraftServerEvents;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TickScheduler {
    private static final Queue<ScheduledTask> TASKS = new ConcurrentLinkedQueue<>();

    public static void onServerTick(MinecraftServerEvents.ServerTickEvent event) {
        Iterator<ScheduledTask> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();

            if (task.isCancelled()) {
                iterator.remove();
                continue;
            }

            if (--task.remainingTicks <= 0) {
                try {
                    task.runnable.run();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (!task.isRepeating()) {
                        task.markAsDone();
                        iterator.remove();
                    } else {
                        task.remainingTicks = task.intervalTicks;
                    }
                }
            }
        }
    }

    /**
     * 一次性延迟任务
     */
    public static ScheduledTask schedule(int delayTicks, Runnable runnable) {
        ScheduledTask task = new ScheduledTask(delayTicks, runnable, false, 0);
        TASKS.add(task);
        return task;
    }

    /**
     * 循环任务
     */
    public static ScheduledTask scheduleAtFixedRate(int initialDelayTicks, int intervalTicks, Runnable runnable) {
        ScheduledTask task = new ScheduledTask(initialDelayTicks, runnable, true, intervalTicks);
        TASKS.add(task);
        return task;
    }

    public static void cancelAll() {
        for (ScheduledTask task : TASKS) {
            task.cancel();
        }
        TASKS.clear();
    }

    public static int getPendingTaskCount() {
        return TASKS.size();
    }

    public static class ScheduledTask {
        private final Runnable runnable;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        @Getter
        private final boolean repeating;
        @Getter
        private final int intervalTicks;

        @Getter
        int remainingTicks;

        ScheduledTask(int delay, Runnable runnable, boolean repeating, int intervalTicks) {
            this.remainingTicks = delay;
            this.runnable = runnable;
            this.repeating = repeating;
            this.intervalTicks = intervalTicks;
        }

        public boolean cancel() {
            if (isDone() || cancelled.get()) {
                return false;
            }
            cancelled.set(true);
            if (!repeating) {
                markAsDone();
            }
            return true;
        }

        public void join() throws InterruptedException {
            if (repeating) {
                throw new UnsupportedOperationException("Cannot join repeating tasks");
            }
            latch.await();
        }

        public boolean join(long timeoutTicks) throws InterruptedException {
            if (repeating) {
                throw new UnsupportedOperationException("Cannot join repeating tasks");
            }
            long timeoutMs = timeoutTicks * 50;
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public boolean isDone() {
            if (repeating) {
                return false;
            }
            return latch.getCount() == 0;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        private void markAsDone() {
            latch.countDown();
        }
    }
}