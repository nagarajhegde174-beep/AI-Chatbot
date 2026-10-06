package com.nexaai.ai.usage;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.model.ModelDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Where usage goes.
 *
 * <p>An interface because the destination changes. This service owns no database (CI rule R5), so
 * today usage lands in a bounded in-memory buffer and is exposed through an endpoint; a later
 * phase replaces this bean with one that persists. Everything above this interface is unchanged
 * either way, which is the point of having it.
 */
public interface UsageSink {

    void record(UsageRecord record);

    /** The most recent records, newest last. */
    List<UsageRecord> recent();

    UsageRecord.Totals totals();

    /** An in-memory sink with a bounded buffer. */
    @Component
    class InMemory implements UsageSink {

        private static final Logger log = LoggerFactory.getLogger(InMemory.class);

        private final java.util.List<UsageRecord> records = new java.util.ArrayList<>();
        private final int capacity;
        private final boolean enabled;

        public InMemory(AiProperties properties) {
            this.capacity = Math.max(1, properties.getUsage().getRetainRecords());
            this.enabled = properties.getUsage().isEnabled();
        }

        @Override
        public synchronized void record(UsageRecord record) {
            if (!enabled) {
                return;
            }
            records.add(record);
            // Bounded. An unbounded buffer in a stateless service is a memory leak with extra
            // steps, and this process is scaled by adding replicas.
            while (records.size() > capacity) {
                records.remove(0);
            }
        }

        @Override
        public synchronized List<UsageRecord> recent() {
            return new ArrayList<>(records);
        }

        @Override
        public synchronized UsageRecord.Totals totals() {
            return UsageRecord.totals(records);
        }

        /** Model names seen, for the usage-by-model breakdown. */
        public synchronized java.util.Map<String, Long> countsByModel() {
            java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
            for (UsageRecord record : records) {
                counts.merge(record.model(), 1L, Long::sum);
            }
            return counts;
        }
    }
}
