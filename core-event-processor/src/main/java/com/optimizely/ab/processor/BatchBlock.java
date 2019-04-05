/**
 *    Copyright 2019, Optimizely Inc. and contributors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.optimizely.ab.processor;

import com.optimizely.ab.common.internal.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Emits buffers in the form of a {@link Collection}, each which contains a maximum number of elements or when teh
 * buffer reaches a maximum age.
 *
 * Buffers are maintained and flushed downstream on a separate, dedicated thread.
 *
 * Supports throttling the number of batches in-flight.
 *
 * @param <T> the type of input elements and output element batches
 * @see BatchTask
 */
public class BatchBlock<T> extends SourceBlock.Base<T> implements ActorBlock<T, T> {
    public static final Logger logger = LoggerFactory.getLogger(BatchBlock.class);

    private final BatchOptions config;
    private final Executor executor;
    private final Semaphore inFlightPermits;
    private final Supplier<BatchTask<T, ?>> batchTaskFactory;

    /**
     * Mutex to serialize modifications to buffer
     */
    private final Object mutex = new Object();

    /**
     * Task that holds & flushes the buffer for latest batch
     */
    private BatchTask<T, ?> currentTask;

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    BatchBlock(BatchOptions config) {
        this.config = Assert.notNull(config, "config");
        this.executor = Assert.notNull(config.getExecutor(), "executor");
        this.inFlightPermits = createInFlightSemaphore(config);
        this.batchTaskFactory = createBatchTaskFactory(config);
    }

    /**
     * @param element the element to push
     */
    @Override
    public void post(@Nonnull T element) {
        synchronized (mutex) {
            collect(element);
        }
    }

    /**
     * @param elements the elements to put
     */
    @Override
    public void postBatch(@Nonnull Collection<? extends T> elements) {
        synchronized (mutex) {
            for (T element : elements) {
                collect(element);
            }
        }
    }

    @Override
    public boolean onStop(long timeout, TimeUnit unit) {
        if (config.shouldFlushOnShutdown()) {
            try {
                flush();
            } catch (InterruptedException e) {
                logger.warn("Interrupted while flushing before shutdown");
            }
        }
        return true;
    }

    /**
     * Forces in-flight work to be flushed synchronously.
     */
    public void flush() throws InterruptedException {
        synchronized (mutex) {
            // prevent new batches from being started
            if (currentTask != null) {
                currentTask.close();
            }

            logger.debug("Flush started");
            Integer n = config.getMaxBatchInFlight();
            if (inFlightPermits != null && n != null) {
                inFlightPermits.acquire(n);
                inFlightPermits.release(n);
            }
            logger.debug("Flush complete");
        }
    }

    public BatchOptions getConfig() {
        return config;
    }

    protected Supplier<BatchTask<T, ?>> createBatchTaskFactory(BatchOptions config) {
        int maxSize = normalizeMaxBatchSize(config);
        Duration maxAge = normalizeMaxBatchAge(config);

        final Supplier<Collection<T>> bufferSupplier = (maxSize > 0) ?
            () -> new ArrayList<>(maxSize) :
            ArrayList::new;

        return () -> new BatchTask<>(bufferSupplier, this::onBufferClose, maxSize, maxAge);
    }

    /**
     * @return a positive integer if max size is configured, otherwise returns zero.
     */
    protected static int normalizeMaxBatchSize(BatchOptions config) {
        Integer maxBatchSize = config.getMaxBatchSize();
        if (maxBatchSize == null || maxBatchSize <= 0) {
            return 0;
        }
        return maxBatchSize;
    }

    /**
     * @return a {@link Duration} if max age is configured, otherwise {@code null}.
     */
    protected static Duration normalizeMaxBatchAge(BatchOptions config) {
        Duration maxBatchAge = config.getMaxBatchAge();
        if (maxBatchAge == null || maxBatchAge.isNegative()) {
            return Duration.ZERO;
        }
        return maxBatchAge;
    }


    protected Semaphore createInFlightSemaphore(BatchOptions config) {
        Integer n = config.getMaxBatchInFlight();
        return (n != null) ? new Semaphore(n) : null;
    }

    /**
     * Callback that consumes elements from a closed {@link BatchTask}
     */
    protected void onBufferClose(Collection<? extends T> batch) {
        if (batch == null || batch.isEmpty()) {
            logger.debug("Batch completed without any elements"); // i.e. forced
            return;
        }

        logger.debug("Batch completed with {} elements: {}", batch.size(), batch);

        target().postBatch(batch);
    }

    /**
     * Inserts the specified element into a batch, initializing a new batch if current
     * batch does not exist or does not accept it (max age/size), and blocking if
     * necessary to acquire in-flight batch permit.
     *
     * Assumes mutex for {@link #currentTask} is being held by caller.
     */
    protected void collect(T element) {
        // try to add to current buffer
        if (currentTask == null || !currentTask.offer(element)) {
            // otherwise, start new one
            BatchTask<T, ?> successorTask = batchTaskFactory.get();

            if (!successorTask.offer(element)) {
                // This can only happen if element itself is flawed and cannot be added
                // to batch (even a new one), so we must drop it.
                logger.warn("Discarding {} rejected by {}", element, successorTask);
                // could potentially return here; currently utilizes successorTask
            }

            currentTask = successorTask;

            // TODO consider executing (acquiring in-flight permit) after releasing task mutex
            executeBatch(successorTask);
        }
    }

    /**
     * Submits task to executor when permitted by the configured number of in-flight batches.
     */
    protected void executeBatch(final BatchTask<T, ?> task) {
        Runnable runnable = task;

        if (inFlightPermits != null) {
            try {
                inFlightPermits.acquire();

                logger.debug("Acquired in-flight permit (available: {}, queued: {})",
                    inFlightPermits.availablePermits(),
                    inFlightPermits.getQueueLength());
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for in-flight batch permit");
                Thread.interrupted();
                Thread.currentThread().interrupt();
                throw new BlockException(e.getMessage(), e);
            }

            // release permit when run complete
            runnable = () -> {
                try {
                    task.run();
                } finally {
                    inFlightPermits.release();
                }
            };
        }

        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException e) {
            // release permit if never ran
            if (inFlightPermits != null) {
                inFlightPermits.release();
            }
            throw new BlockException("Unable to start batching task", e);
        }
    }

    public static class Builder<T> implements BatchOptions {
        static int MAX_BATCH_SIZE_DEFAULT = 10;
        static Duration MAX_BATCH_OPEN_MS_DEFAULT = Duration.ofMillis(250);

        private Integer maxBatchSize = MAX_BATCH_SIZE_DEFAULT;
        private Duration maxBatchAge = MAX_BATCH_OPEN_MS_DEFAULT;
        private Integer maxBatchInFlight = null;
        private boolean flushOnShutdown = true;
        private Executor executor;

        private Builder() {
        }

        public Builder<T> from(BatchOptions config) {
            Assert.notNull(config, "config");
            this.maxBatchSize = config.getMaxBatchSize();
            this.maxBatchAge = config.getMaxBatchAge();
            this.maxBatchInFlight = config.getMaxBatchInFlight();
            this.flushOnShutdown = config.shouldFlushOnShutdown();
            this.executor = config.getExecutor();
            return this;
        }

        public Builder<T> maxBatchSize(@Nullable Integer maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder<T> maxBatchAge(@Nullable Duration maxBatchAge) {
            this.maxBatchAge = maxBatchAge;
            return this;
        }

        public Builder<T> maxBatchInFlight(@Nullable Integer maxBatchInFlight) {
            this.maxBatchInFlight = maxBatchInFlight;
            return this;
        }

        public Builder<T> flushOnShutdown(boolean flushOnShutdown) {
            this.flushOnShutdown = flushOnShutdown;
            return this;
        }

        public Builder<T> executor(@Nonnull Executor executor) {
            this.executor = Assert.notNull(executor, "executor");
            return this;
        }

        @Override
        public Integer getMaxBatchSize() {
            return maxBatchSize;
        }

        @Override
        public Duration getMaxBatchAge() {
            return maxBatchAge;
        }

        @Override
        public Integer getMaxBatchInFlight() {
            return maxBatchInFlight;
        }

        @Override
        public boolean shouldFlushOnShutdown() {
            return flushOnShutdown;
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("Builder{");
            sb.append(", maxBatchSize=").append(maxBatchSize);
            sb.append(", maxBatchAge=").append(maxBatchAge);
            sb.append(", maxBatchInFlight=").append(maxBatchInFlight);
            sb.append(", flushOnShutdown=").append(flushOnShutdown);
            sb.append(", executor=").append(executor);
            sb.append('}');
            return sb.toString();
        }

        public BatchBlock<T> build() {
            return new BatchBlock<>(this);
        }
    }
}