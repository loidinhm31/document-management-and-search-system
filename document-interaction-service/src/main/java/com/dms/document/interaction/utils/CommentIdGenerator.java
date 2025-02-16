package com.dms.document.interaction.utils;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class CommentIdGenerator implements IdentifierGenerator {
    // Thread-safe counter for sequence part (0-9999)
    private static final AtomicInteger sequence = new AtomicInteger(0);
    private static final int MAX_SEQUENCE = 9999;

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object)
            throws HibernateException {
        // Get current timestamp in microseconds (first 6 digits)
        long timestampPart = (Instant.now().toEpochMilli() * 1000) % 1000000;

        // Get and increment sequence (0-9999, will rollover)
        int sequencePart = sequence.updateAndGet(current ->
                current >= MAX_SEQUENCE ? 0 : current + 1);

        // Combine timestamp and sequence similar to the SQL function
        // timestamp_part * 10000 shifts timestamp left 4 digits
        // then add sequence_part to fill last 4 digits
        return (timestampPart * 10000) + sequencePart;
    }
}