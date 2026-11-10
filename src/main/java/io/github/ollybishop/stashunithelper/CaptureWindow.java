package io.github.ollybishop.stashunithelper;

import java.time.Duration;

/**
 * Pure capture budget; uses a monotonic clock supplied in milliseconds by the subscriber.
 */
final class CaptureWindow
{
    static final long DURATION_SECONDS = 120;
    static final long DURATION_MILLIS = Duration.ofSeconds( DURATION_SECONDS ).toMillis();
    static final int MAX_RECORD_COUNT = 600;

    private boolean isRunning;
    private long startTimeMillis;
    private int recordCount;

    /**
     * Starts or restarts capture with a fresh duration and record budget.
     *
     * @param nowMillis current monotonic clock reading in milliseconds
     */
    void start( long nowMillis )
    {
        isRunning = true;
        startTimeMillis = nowMillis;
        recordCount = 0;
    }

    /**
     * Stops capture so no further records are allowed until {@link #start(long)} is called.
     */
    void stop()
    {
        isRunning = false;
    }

    /**
     * Checks whether capture is running and has time and record capacity remaining.
     * Does not change the record count.
     *
     * @param nowMillis current reading from the same monotonic clock used to start capture,
     *                  in milliseconds
     * @return true if capture has started, has not been stopped and is below both limits;
     *         false at or beyond either limit
     */
    boolean isActive( long nowMillis )
    {
        return isRunning &&
               nowMillis < startTimeMillis + DURATION_MILLIS &&
               recordCount < MAX_RECORD_COUNT;
    }

    /**
     * Checks whether another diagnostic entry is allowed and counts it if so.
     * The caller writes the entry; this method only updates the capture budget.
     *
     * @param nowMillis current reading from the same monotonic clock used to start capture,
     *                  in milliseconds
     * @return true if one record was counted and the caller may write it;
     *         false if capture is inactive, leaving the count unchanged
     */
    boolean tryCountRecord( long nowMillis )
    {
        if ( !isActive( nowMillis ) )
            return false;

        recordCount++;

        return true;
    }
}
