package io.github.ollybishop.stashunithelper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ObjectComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StashUnitHelperPluginTest
{
    // Capture the plugin's log output for assertions rather than logging from this test class.
    @SuppressWarnings( "LoggerInitializedWithForeignClass" )
    private final Logger logger = (Logger) LoggerFactory.getLogger( StashUnitHelperPlugin.class );

    private final Client client = mock( Client.class );
    private final ClientThread clientThread = mock( ClientThread.class );
    private final StashUnitHelperConfig config = mock( StashUnitHelperConfig.class );
    private final Queue<Runnable> callbacks = new ArrayDeque<>();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private StashUnitHelperPlugin plugin;

    @Before
    public void setUp()
    {
        doAnswer( invocation ->
        {
            callbacks.add( invocation.getArgument( 0 ) );
            return null;
        } ).when( clientThread ).invokeLater( any( Runnable.class ) );

        when( client.getGameState() ).thenReturn( GameState.LOGGED_IN );
        when( client.getObjectDefinition( ObjectID.HH_EASY001 ) ).thenReturn( mock( ObjectComposition.class ) );

        appender.start();
        logger.addAppender( appender );

        plugin = createPlugin();
        plugin.startUp();
        drainCallbacks();

        appender.list.clear();
    }

    @After
    public void tearDown()
    {
        logger.detachAppender( appender );
        appender.stop();
    }

    @Test
    public void memberInjectedPluginStartsConfiguredCapture()
    {
        // Given
        StashUnitHelperPlugin loadedPlugin = createPlugin();
        when( config.isCaptureEnabled() ).thenReturn( true );

        // When
        loadedPlugin.startUp();
        appender.list.clear();
        drainCallbacks();

        // Then
        List<String> messages = getMessages();

        assertEquals( 1, messages.size() );
        assertTrue( messages.get( 0 ).contains( "BEGIN" ) );
    }

    @Test
    public void memberInjectedPluginRecordsEventsAfterStartup()
    {
        // Given
        StashUnitHelperPlugin loadedPlugin = createPlugin();
        when( config.isCaptureEnabled() ).thenReturn( true );
        loadedPlugin.startUp();
        drainCallbacks();
        appender.list.clear();

        // When
        loadedPlugin.onMenuOptionClicked( searchEvent() );

        // Then
        List<String> messages = getMessages();

        assertEquals( 2, messages.size() );
        assertTrue( messages.get( 0 ).contains( "eventNumber='1'" ) );
    }

    @Test
    public void automaticCaptureIncludesTriggerAndObjectDefinition()
    {
        // Given
        MenuOptionClicked event = searchEvent();

        // When
        plugin.onMenuOptionClicked( event );

        // Then
        List<String> messages = getMessages();

        assertEquals( 3, messages.size() );
        assertTrue( messages.get( 0 ).contains( "BEGIN reason='\\'Search\\' selected" ) );
        assertTrue( messages.get( 1 ).contains( "eventNumber='1'" ) );
        assertTrue( messages.get( 1 ).contains( "eventType='menu'" ) );
        assertTrue( messages.get( 1 ).contains( "targetId='" + ObjectID.HH_EASY001 + "'" ) );
        assertTrue( messages.get( 1 ).contains( "isConsumed='false'" ) );
        assertTrue( messages.get( 2 ).contains( "eventType='object-definition'" ) );
    }

    @Test
    public void captureRecordsUpToItsBudget()
    {
        // Given
        MenuOptionClicked event = searchEvent();

        // When
        for ( int i = 0; i < CaptureWindow.MAX_RECORD_COUNT / 2; i++ )
            plugin.onMenuOptionClicked( event );

        // Then
        List<String> messages = getMessages();

        assertEquals( CaptureWindow.MAX_RECORD_COUNT + 1, messages.size() );
        assertTrue( messages.get( CaptureWindow.MAX_RECORD_COUNT ).contains( "eventNumber='600'" ) );
    }

    @Test
    public void exhaustedCaptureEndsOnceOnNextEvent()
    {
        // Given
        exhaustRecordBudget();
        appender.list.clear();

        // When
        plugin.onWidgetLoaded( new WidgetLoaded() );
        plugin.onWidgetLoaded( new WidgetLoaded() );

        // Then
        List<String> messages = getMessages();

        assertEquals( 1, messages.size() );
        assertTrue( messages.get( 0 ).contains( "END reason='Time or record limit reached' recordCount='600'" ) );
    }

    @Test
    public void searchRestartsExhaustedCaptureWithFreshEventNumbering()
    {
        // Given
        exhaustRecordBudget();
        plugin.onWidgetLoaded( new WidgetLoaded() );
        appender.list.clear();

        // When
        plugin.onMenuOptionClicked( searchEvent() );

        // Then
        List<String> messages = getMessages();

        assertEquals( 3, messages.size() );
        assertTrue( messages.get( 0 ).contains( "BEGIN" ) );
        assertTrue( messages.get( 1 ).contains( "eventNumber='1'" ) );
    }

    @Test
    public void unsupportedSearchDoesNotStartCapture()
    {
        // Given
        MenuOptionClicked event = searchEvent();
        when( event.getMenuEntry().getType() ).thenReturn( MenuAction.WALK );

        // When
        plugin.onMenuOptionClicked( event );

        // Then
        assertTrue( getMessages().isEmpty() );
    }

    @Test
    public void manualRestartWaitsForClientThread()
    {
        // Given
        startAutomaticCapture();

        // When
        setCapture( true );

        // Then
        assertTrue( getMessages().isEmpty() );
    }

    @Test
    public void manualRestartClosesPreviousCaptureBeforeStartingNext()
    {
        // Given
        startAutomaticCapture();
        setCapture( true );

        // When
        drainCallbacks();

        // Then
        List<String> messages = getMessages();

        assertEquals( 2, messages.size() );
        assertTrue( messages.get( 0 ).contains( "END reason='Capture restarted' recordCount='2'" ) );
        assertTrue( messages.get( 1 ).contains( "BEGIN" ) );
    }

    @Test
    public void manualRestartResetsEventNumbering()
    {
        // Given
        startAutomaticCapture();
        setCapture( true );
        drainCallbacks();
        appender.list.clear();

        // When
        plugin.onMenuOptionClicked( searchEvent() );

        // Then
        assertTrue( getMessages().get( 0 ).contains( "eventNumber='1'" ) );
    }

    @Test
    public void shutdownRejectsPendingConfigurationAndOldSessionCallbacks()
    {
        // Given
        setCapture( true );

        // When
        plugin.shutDown();
        when( config.isCaptureEnabled() ).thenReturn( false );
        plugin.startUp();
        drainCallbacks();

        // Then
        assertFalse( getMessages().stream().anyMatch( message -> message.contains( "BEGIN" ) ) );
    }

    @Test
    public void reenabledPluginCanStartFreshAutomaticCapture()
    {
        // Given
        setCapture( true );
        plugin.shutDown();
        when( config.isCaptureEnabled() ).thenReturn( false );
        plugin.startUp();
        drainCallbacks();
        appender.list.clear();

        // When
        plugin.onMenuOptionClicked( searchEvent() );

        // Then
        List<String> messages = getMessages();

        assertEquals( 1, messages.stream().filter( message -> message.contains( "BEGIN" ) ).count() );
        assertTrue( messages.get( 1 ).contains( "eventNumber='1'" ) );
    }

    @Test
    public void shutdownDefersCaptureCleanupToClientThread()
    {
        // Given
        startAutomaticCapture();

        // When
        plugin.shutDown();

        // Then
        assertTrue( getMessages().isEmpty() );
    }

    @Test
    public void shutdownImmediatelyPreventsFurtherEvents()
    {
        // Given
        startAutomaticCapture();
        plugin.shutDown();
        appender.list.clear();

        // When
        plugin.onMenuOptionClicked( searchEvent() );

        // Then
        assertTrue( getMessages().isEmpty() );
    }

    @Test
    public void shutdownEndsCaptureOnClientThread()
    {
        // Given
        startAutomaticCapture();
        plugin.shutDown();

        // When
        drainCallbacks();

        // Then
        List<String> messages = getMessages();

        assertEquals( 1, messages.size() );
        assertTrue( messages.get( 0 ).contains( "END reason='Plugin disabled'" ) );
    }

    @Test
    public void rapidConfigurationChangesAreDeferred()
    {
        // Given
        // Capture is initially disabled by setUp.

        // When
        setCapture( true );
        setCapture( false );

        // Then
        assertTrue( getMessages().isEmpty() );
    }

    @Test
    public void rapidConfigurationChangesKeepTheirOriginalOrder()
    {
        // Given
        setCapture( true );
        setCapture( false );

        // When
        drainCallbacks();

        // Then
        List<String> messages = getMessages();

        assertEquals( 2, messages.size() );
        assertTrue( messages.get( 0 ).contains( "BEGIN" ) );
        assertTrue( messages.get( 1 ).contains( "END reason='Capture disabled manually'" ) );
    }

    @Test
    public void startupCaptureIsDeferred()
    {
        // Given
        prepareConfiguredStartup();

        // When
        plugin.startUp();

        // Then
        assertFalse( getMessages().stream().anyMatch( message -> message.contains( "BEGIN" ) ) );
        assertEquals( 1, callbacks.size() );
    }

    @Test
    public void shutdownCancelsPendingStartupCapture()
    {
        // Given
        prepareConfiguredStartup();
        plugin.startUp();
        appender.list.clear();

        // When
        plugin.shutDown();
        drainCallbacks();

        // Then
        assertTrue( getMessages().isEmpty() );
    }

    @Test
    public void eventLogsEscapePassedValuesWithoutTrailingSpaces()
    {
        // Given
        startAutomaticCapture();
        MenuOptionClicked event = searchEvent();
        when( event.getMenuEntry().getOption() ).thenReturn( "Can't use C:\\stash" );

        // When
        plugin.onMenuOptionClicked( event );

        // Then
        List<String> messages = getMessages();

        assertTrue( messages.get( 0 ).contains( "menuOption='Can\\'t use C:\\\\stash'" ) );
        assertTrue( messages.stream().noneMatch( message -> message.endsWith( " " ) ) );
    }

    @Test
    public void menuLogsPreserveFullTextAndSpacingWithoutTags()
    {
        // Given
        startAutomaticCapture();
        String suffix = "x".repeat( 300 );
        String text = "<col=ff0000>STASH</col>  \t\r\n" + suffix;
        String expectedValue = "'STASH  \\t\\r\\n" + suffix + "'";

        MenuOptionClicked event = searchEvent();
        when( event.getMenuEntry().getOption() ).thenReturn( text );

        // When
        plugin.onMenuOptionClicked( event );

        // Then
        List<String> messages = getMessages();

        assertTrue( messages.get( 0 ).contains( "menuOption=" + expectedValue ) );
        assertFalse( messages.get( 0 ).contains( "\n" ) );
        assertFalse( messages.get( 0 ).contains( "\r" ) );
    }

    @Test
    public void chatLogsPreserveFullTextAndSpacingWithoutTags()
    {
        // Given
        startAutomaticCapture();
        String suffix = "x".repeat( 300 );
        String text = "<col=ff0000>STASH</col>  \t\r\n" + suffix;
        String expectedValue = "'STASH  \\t\\r\\n" + suffix + "'";

        ChatMessage event = mock( ChatMessage.class );
        when( event.getType() ).thenReturn( ChatMessageType.GAMEMESSAGE );
        when( event.getMessage() ).thenReturn( text );

        // When
        plugin.onChatMessage( event );

        // Then
        List<String> messages = getMessages();

        assertEquals( 1, messages.size() );
        assertTrue( messages.get( 0 ).contains( "messageText=" + expectedValue ) );
        assertFalse( messages.get( 0 ).contains( "\n" ) );
        assertFalse( messages.get( 0 ).contains( "\r" ) );
    }

    private void startAutomaticCapture()
    {
        plugin.onMenuOptionClicked( searchEvent() );
        appender.list.clear();
    }

    private void exhaustRecordBudget()
    {
        for ( int i = 0; i < CaptureWindow.MAX_RECORD_COUNT / 2; i++ )
            plugin.onMenuOptionClicked( searchEvent() );
    }

    private void prepareConfiguredStartup()
    {
        plugin.shutDown();
        drainCallbacks();

        when( config.isCaptureEnabled() ).thenReturn( true );
    }

    private StashUnitHelperPlugin createPlugin()
    {
        StashUnitHelperPlugin loadedPlugin = new StashUnitHelperPlugin();

        Guice.createInjector( new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( Client.class ).toInstance( client );
                bind( ClientThread.class ).toInstance( clientThread );
                bind( StashUnitHelperConfig.class ).toInstance( config );
            }
        } ).injectMembers( loadedPlugin );

        return loadedPlugin;
    }

    private MenuOptionClicked searchEvent()
    {
        MenuEntry entry = mock( MenuEntry.class );

        when( entry.getType() ).thenReturn( MenuAction.GAME_OBJECT_FIRST_OPTION );
        when( entry.getIdentifier() ).thenReturn( ObjectID.HH_EASY001 );
        when( entry.getOption() ).thenReturn( "Search" );

        return new MenuOptionClicked( entry );
    }

    private void setCapture( boolean isEnabled )
    {
        when( config.isCaptureEnabled() ).thenReturn( isEnabled );

        ConfigChanged event = new ConfigChanged();
        event.setGroup( StashUnitHelperConfig.GROUP );
        event.setKey( "capture" );

        plugin.onConfigChanged( event );
    }

    private void drainCallbacks()
    {
        while ( !callbacks.isEmpty() )
            callbacks.remove().run();
    }

    private List<String> getMessages()
    {
        List<String> messages = new ArrayList<>();

        for ( ILoggingEvent event : appender.list )
            messages.add( event.getFormattedMessage() );

        return messages;
    }
}
