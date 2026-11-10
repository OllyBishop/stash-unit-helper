package io.github.ollybishop.stashunithelper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaptureWindowTest
{
    @Test
    public void captureIsOptInAndExpiresAtBoundary()
    {
        CaptureWindow capture = new CaptureWindow();
        assertFalse( capture.tryCountRecord( 10 ) );
        capture.start( 10 );
        assertTrue( capture.tryCountRecord( 10 + CaptureWindow.DURATION_MILLIS - 1 ) );
        assertFalse( capture.tryCountRecord( 10 + CaptureWindow.DURATION_MILLIS ) );
    }

    @Test
    public void budgetCannotBeExceededAndCanRestart()
    {
        CaptureWindow capture = new CaptureWindow();
        capture.start( 100 );
        for ( int i = 0; i < CaptureWindow.MAX_RECORD_COUNT; i++ )
        {
            assertTrue( capture.tryCountRecord( 100 ) );
        }
        assertFalse( capture.tryCountRecord( 100 ) );
        capture.start( 200 );
        assertTrue( capture.tryCountRecord( 200 ) );
        capture.stop();
        assertFalse( capture.tryCountRecord( 200 ) );
    }
}
