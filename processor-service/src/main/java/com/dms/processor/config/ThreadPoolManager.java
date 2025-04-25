package com.dms.processor.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unified thread pool manager that provides dynamic resource allocation
 * based on system capabilities. Uses a shared thread pool to maximize resource
 * utilization across different types of document processing tasks.
 */
@Component
@Slf4j
public class ThreadPoolManager {

    // Configuration properties
    @Value("${app.threads.core-pool-size:0}")
    private int configuredCorePoolSize;

    @Value("${app.threads.max-pool-size:0}")
    private int configuredMaxPoolSize;

    @Value("${app.threads.queue-capacity:100}")
    private int queueCapacity;

    @Value("${app.threads.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${app.threads.adaptive:true}")
    private boolean adaptive;

    // Unified thread pool
    private ThreadPoolExecutor executorService;
    private ScheduledExecutorService scheduledTasksPool;

    // Task tracking and metrics
    private final AtomicInteger activeDocumentTasks = new AtomicInteger(0);
    private final AtomicInteger activeOcrTasks = new AtomicInteger(0);
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksRejected = new AtomicLong(0);

    // Resource monitoring
    private double systemLoadThreshold = 0.8; // 80% load threshold
    private long lastResourceCheck = 0;
    private Runtime runtime = Runtime.getRuntime();

    @PostConstruct
    public void initialize() {
        // Determine optimal thread pool size based on available processors
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        // If not explicitly configured, calculate optimal sizes based on available processors
        int corePoolSize = configuredCorePoolSize > 0 ?
                configuredCorePoolSize : Math.max(2, availableProcessors / 2);

        int maxPoolSize = configuredMaxPoolSize > 0 ?
                configuredMaxPoolSize : Math.max(4, availableProcessors);

        log.info("Initializing thread pool with available processors: {}, core size: {}, max size: {}",
                availableProcessors, corePoolSize, maxPoolSize);

        // Create thread factories with meaningful names
        ThreadFactory documentThreadFactory = createThreadFactory("doc-processor");
        ThreadFactory scheduledThreadFactory = createThreadFactory("scheduled-task");

        // Create priority-based queue for document processing tasks
        PriorityBlockingQueue<Runnable> taskQueue = new PriorityBlockingQueue<>(
                queueCapacity,
                (r1, r2) -> {
                    // Compare priorities if both are PriorityTask instances
                    if (r1 instanceof PriorityTask && r2 instanceof PriorityTask) {
                        return ((PriorityTask) r2).getPriority() - ((PriorityTask) r1).getPriority();
                    }
                    return 0;
                }
        );

        // Create the main unified thread pool with monitoring if adaptive is enabled
        if (adaptive) {
            executorService = new MonitoredThreadPoolExecutor(
                    corePoolSize,
                    maxPoolSize,
                    keepAliveSeconds, TimeUnit.SECONDS,
                    taskQueue,
                    documentThreadFactory,
                    (r, executor) -> {
                        // Custom rejection handler with backpressure
                        totalTasksRejected.incrementAndGet();
                        log.warn("Task rejected due to resource constraints, applying backpressure");

                        try {
                            // Try to add to queue with timeout - implements backpressure
                            if (!executor.getQueue().offer(r, 30, TimeUnit.SECONDS)) {
                                log.error("Task could not be queued after timeout - discarding task");
                                throw new RejectedExecutionException("Task discarded after timeout");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RejectedExecutionException("Interrupted while waiting to submit task", e);
                        }
                    }
            );
        } else {
            executorService = new ThreadPoolExecutor(
                    corePoolSize,
                    maxPoolSize,
                    keepAliveSeconds, TimeUnit.SECONDS,
                    taskQueue,
                    documentThreadFactory,
                    (r, executor) -> {
                        // Custom rejection handler with backpressure
                        totalTasksRejected.incrementAndGet();
                        log.warn("Task rejected due to resource constraints, applying backpressure");

                        try {
                            // Try to add to queue with timeout - implements backpressure
                            if (!executor.getQueue().offer(r, 30, TimeUnit.SECONDS)) {
                                log.error("Task could not be queued after timeout - discarding task");
                                throw new RejectedExecutionException("Task discarded after timeout");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RejectedExecutionException("Interrupted while waiting to submit task", e);
                        }
                    }
            );
        }

        // Create small pool for scheduled tasks (monitoring, cleanup, etc.)
        scheduledTasksPool = Executors.newScheduledThreadPool(2, scheduledThreadFactory);

        // Schedule periodic resource monitoring
        if (adaptive) {
            scheduledTasksPool.scheduleAtFixedRate(
                    this::checkAndAdjustThreadPool,
                    30,
                    30,
                    TimeUnit.SECONDS
            );
        }

        log.info("Thread pool manager initialized with core={}, max={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);
    }

    /**
     * Submit a document processing task with normal priority
     */
    public <T> CompletableFuture<T> submitDocumentTask(Callable<T> task) {
        return submitTask(task, TaskPriority.NORMAL);
    }

    /**
     * Submit an OCR processing task with higher resource weight
     */
    public <T> CompletableFuture<T> submitOcrTask(Callable<T> task) {
        // OCR tasks get lower priority but are tracked separately
        return submitTask(task, TaskPriority.LOW, true);
    }

    /**
     * Submit any task with specified priority and resource tracking
     */
    public <T> CompletableFuture<T> submitTask(Callable<T> task, TaskPriority priority) {
        return submitTask(task, priority, false);
    }

    /**
     * Submit any task with specified priority and resource tracking
     *
     * @param task The task to execute
     * @param priority Priority level
     * @param isOcrTask Whether this is an OCR task (for resource weighting)
     * @return CompletableFuture for the task
     */
    public <T> CompletableFuture<T> submitTask(Callable<T> task, TaskPriority priority, boolean isOcrTask) {
        // Resource check and tracking
        if (isOcrTask) {
            activeOcrTasks.incrementAndGet();
        } else {
            activeDocumentTasks.incrementAndGet();
        }

        totalTasksSubmitted.incrementAndGet();
        checkSystemResources();

        // Create CompletableFuture to track result
        CompletableFuture<T> future = new CompletableFuture<>();

        // Submit as PriorityTask to the executor
        executorService.execute(new PriorityTask(priority.getValue(), () -> {
            try {
                T result = task.call();
                future.complete(result);
                totalTasksCompleted.incrementAndGet();
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                if (isOcrTask) {
                    activeOcrTasks.decrementAndGet();
                } else {
                    activeDocumentTasks.decrementAndGet();
                }
            }
        }));

        return future;
    }

    /**
     * Schedule a task to run with fixed delay
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay, TimeUnit unit) {
        return scheduledTasksPool.scheduleWithFixedDelay(task, 0, delay, unit);
    }

    /**
     * Check system resources and adjust thread pool if needed
     */
    private void checkAndAdjustThreadPool() {
        try {
            // Calculate memory usage
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            double memoryUsageRatio = (double) usedMemory / runtime.maxMemory();

            // Get current thread counts
            int activeThreads = executorService.getActiveCount();
            int poolSize = executorService.getPoolSize();
            int queueSize = executorService.getQueue().size();

            // Log current resource usage
            log.info("Resource check - Memory: {}, Threads: {}/{}, Queue: {}, Tasks: {}/{}",
                    memoryUsageRatio, activeThreads, poolSize, queueSize,
                    activeDocumentTasks.get(), activeOcrTasks.get());

            // Check if adjustments are needed
            if (memoryUsageRatio > systemLoadThreshold) {
                // High memory usage - reduce max pool size temporarily
                int currentMax = executorService.getMaximumPoolSize();
                int newMax = Math.max(executorService.getCorePoolSize(), currentMax - 1);

                if (newMax < currentMax) {
                    executorService.setMaximumPoolSize(newMax);
                    log.info("Reduced thread pool max size due to high memory usage: {} -> {}",
                            currentMax, newMax);
                }
            } else if (memoryUsageRatio < 0.5 && queueSize > 10) {
                // Low memory usage and tasks waiting - increase max pool size
                int currentMax = executorService.getMaximumPoolSize();
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                int newMax = Math.min(availableProcessors * 2, currentMax + 1);

                if (newMax > currentMax) {
                    executorService.setMaximumPoolSize(newMax);
                    log.info("Increased thread pool max size to handle queue: {} -> {}",
                            currentMax, newMax);
                }
            }
        } catch (Exception e) {
            log.error("Error during thread pool adjustment", e);
        }
    }

    /**
     * Check if we should apply backpressure based on system resources
     */
    private void checkSystemResources() {
        long now = System.currentTimeMillis();
        if (now - lastResourceCheck < 5000) {
            // Don't check too frequently (at most every 5 seconds)
            return;
        }

        lastResourceCheck = now;
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsageRatio = (double) usedMemory / runtime.maxMemory();

        // If memory usage is critically high, apply brief delay to reduce pressure
        if (memoryUsageRatio > 0.85) {
            log.info("System under memory pressure ({}), applying brief backpressure", memoryUsageRatio);
            try {
                Thread.sleep(500); // Short delay to reduce incoming task rate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Create a thread factory with a specific naming pattern
     */
    private ThreadFactory createThreadFactory(String poolName) {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return r -> {
            Thread thread = new Thread(r);
            thread.setName(poolName + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Get current metrics about thread pool usage
     */
    public ThreadPoolMetrics getMetrics() {
        return new ThreadPoolMetrics(
                executorService.getActiveCount(),
                executorService.getPoolSize(),
                executorService.getMaximumPoolSize(),
                executorService.getQueue().size(),
                activeDocumentTasks.get(),
                activeOcrTasks.get(),
                totalTasksSubmitted.get(),
                totalTasksCompleted.get(),
                totalTasksRejected.get()
        );
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down thread pools...");
        shutdownExecutor(executorService, "Unified Thread Pool");
        shutdownExecutor(scheduledTasksPool, "Scheduled Tasks");
    }

    private void shutdownExecutor(ExecutorService executor, String poolName) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("{} did not terminate in time, forcing shutdown", poolName);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while shutting down {}", poolName, e);
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * Task wrapper that includes priority information for the queue
     */
    private static class PriorityTask implements Runnable {
        @Getter
        private final int priority;
        private final Runnable task;

        public PriorityTask(int priority, Runnable task) {
            this.priority = priority;
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }

    /**
     * Extended ThreadPoolExecutor that adds monitoring and metrics
     */
    private static class MonitoredThreadPoolExecutor extends ThreadPoolExecutor {
        // Track execution times for performance monitoring
        private final ThreadLocal<Long> startTimes = new ThreadLocal<>();
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicLong tasksExecuted = new AtomicLong(0);

        public MonitoredThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                           long keepAliveTime, TimeUnit unit,
                                           BlockingQueue<Runnable> workQueue,
                                           ThreadFactory threadFactory,
                                           RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            startTimes.set(System.nanoTime());
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            try {
                long endTime = System.nanoTime();
                Long startTime = startTimes.get();
                if (startTime != null) {
                    long taskTime = endTime - startTime;
                    totalExecutionTime.addAndGet(taskTime);
                    tasksExecuted.incrementAndGet();

                    // Log if this was an unusually long task
                    if (taskTime > TimeUnit.SECONDS.toNanos(10)) {
                        log.info("Long-running task detected: {} ms", TimeUnit.NANOSECONDS.toMillis(taskTime));
                    }
                }
            } finally {
                startTimes.remove();
                super.afterExecute(r, t);
            }

            // Log exception if task failed
            if (t != null) {
                log.error("Task execution failed", t);
            }
        }

        /**
         * Get average task execution time in milliseconds
         */
        public double getAverageExecutionTime() {
            long tasks = tasksExecuted.get();
            if (tasks == 0) return 0;

            return TimeUnit.NANOSECONDS.toMillis(totalExecutionTime.get()) / (double) tasks;
        }
    }

    /**
     * Priority levels for tasks
     */
    @Getter
    public enum TaskPriority {
        CRITICAL(100),
        HIGH(75),
        NORMAL(50),
        LOW(25),
        BACKGROUND(10);

        private final int value;

        TaskPriority(int value) {
            this.value = value;
        }

    }

    /**
     * Metrics class for monitoring thread pool usage
     */
    public record ThreadPoolMetrics(
            int activeThreads,
            int poolSize,
            int maxPoolSize,
            int queueSize,
            int activeDocumentTasks,
            int activeOcrTasks,
            long totalSubmitted,
            long totalCompleted,
            long totalRejected
    ) {}
}