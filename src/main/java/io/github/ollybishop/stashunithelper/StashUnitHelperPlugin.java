package io.github.ollybishop.stashunithelper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static io.github.ollybishop.stashunithelper.util.StringUtils.toSingleQuotedString;

@Slf4j
@PluginDescriptor(
        name = "STASH Unit Helper",
        description = "Observes STASH interactions; no reminders or game actions",
        tags = { "stash", "clue", "diagnostic" },
        enabledByDefault = false
)
public class StashUnitHelperPlugin extends Plugin
{
    private final CaptureWindow captureWindow = new CaptureWindow();

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private StashUnitHelperConfig config;

    // Lifecycle callbacks run outside the client thread; identity rejects stale queued work.
    private volatile Object activeSessionToken;

    private int eventNumber;
    private int variableRecordCount;
    private boolean isCaptureOpen;

    private WorldPoint lastLocation;
    private Item[] inventorySnapshot;
    private Item[] equipmentSnapshot;

    @Provides
    StashUnitHelperConfig provideConfig( ConfigManager manager )
    {
        return manager.getConfig( StashUnitHelperConfig.class );
    }

    @Override
    protected void startUp()
    {
        log.debug(
                "STASH_DIAG enabled; capture is bounded to {} seconds / {} records.",
                CaptureWindow.DURATION_SECONDS, CaptureWindow.MAX_RECORD_COUNT );

        Object sessionToken = new Object();
        activeSessionToken = sessionToken;

        clientThread.invokeLater( () ->
        {
            if ( activeSessionToken == sessionToken && config.isCaptureEnabled() )
                beginCapture( "Capture enabled at startup" );
        } );
    }

    @Override
    protected void shutDown()
    {
        activeSessionToken = null;

        clientThread.invokeLater( () ->
        {
            endCapture( "Plugin disabled" );
            clearSnapshots();
        } );
    }

    @Subscribe
    public void onConfigChanged( ConfigChanged event )
    {
        if ( StashUnitHelperConfig.GROUP.equals( event.getGroup() ) && "capture".equals( event.getKey() ) )
        {
            Object sessionToken = activeSessionToken;
            boolean isCaptureEnabled = config.isCaptureEnabled();

            clientThread.invokeLater( () ->
            {
                if ( sessionToken == null || activeSessionToken != sessionToken )
                    return;

                if ( isCaptureEnabled )
                    beginCapture( "Capture enabled manually" );
                else
                    endCapture( "Capture disabled manually" );
            } );
        }
    }

    @Subscribe
    public void onMenuOptionClicked( MenuOptionClicked event )
    {
        endCaptureIfExpired();

        boolean isSupportedStashUnit = isSupportedStashUnit( event );

        if ( !isCaptureActive() )
        {
            if ( activeSessionToken == null || !isSupportedStashUnit || !"Search".equals( event.getMenuOption() ) )
                return;

            beginCapture( "'Search' selected on supported STASH unit" );
        }

        // No menu target text: it could contain another player's name.
        logDiagnosticEvent(
                "menu",
                new String[] { "menuAction", "menuOption", "targetId", "itemId", "param0", "param1", "isConsumed", "isSupportedStashUnit" },
                new Object[] { event.getMenuAction(), Text.removeTags( event.getMenuOption() ), event.getId(), event.getItemId(),
                               event.getParam0(), event.getParam1(), event.isConsumed(), isSupportedStashUnit } );

        if ( isSupportedStashUnit )
        {
            ObjectComposition objectDef = client.getObjectDefinition( event.getId() );

            if ( objectDef != null )
            {
                logDiagnosticEvent(
                        "object-definition",
                        new String[] { "objectId", "varbitId", "varpId", "impostorIds" },
                        new Object[] { objectDef.getId(), objectDef.getVarbitId(), objectDef.getVarPlayerId(), objectDef.getImpostorIds() } );
            }
        }
    }

    @Subscribe
    public void onChatMessage( ChatMessage event )
    {
        endCaptureIfExpired();

        if ( !isCaptureActive() )
            return;

        if ( event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM )
            return;

        String message = Text.removeTags( event.getMessage() );

        if ( message.toLowerCase( Locale.ROOT ).contains( "stash" ) )
        {
            logDiagnosticEvent(
                    "stash-chat",
                    new String[] { "chatMessageType", "messageText" },
                    new Object[] { event.getType(), message } );
        }
    }

    @Subscribe
    public void onGameTick( GameTick event )
    {
        endCaptureIfExpired();

        if ( !isCaptureActive() )
            return;

        if ( client.getGameState() != GameState.LOGGED_IN )
            return;

        WorldPoint currentLocation = getPlayerLocation();

        if ( currentLocation != null && !currentLocation.equals( lastLocation ) )
        {
            logDiagnosticEvent( "location", "previousLocation", lastLocation );
            lastLocation = currentLocation;
        }

        logItemContainerChanges( InventoryID.INV );
        logItemContainerChanges( InventoryID.WORN );
    }

    @Subscribe
    public void onItemContainerChanged( ItemContainerChanged event )
    {
        endCaptureIfExpired();

        if ( !isCaptureActive() )
            return;

        int containerId = event.getContainerId();

        if ( containerId == InventoryID.INV || containerId == InventoryID.WORN )
        {
            logDiagnosticEvent( "container-event", "containerId", containerId );
            logItemContainerChanges( containerId );
        }
    }

    @Subscribe
    public void onVarbitChanged( VarbitChanged event )
    {
        endCaptureIfExpired();

        if ( !isCaptureActive() )
            return;

        if ( !config.isVariableCaptureEnabled() || variableRecordCount >= 80 )
            return;

        logDiagnosticEvent(
                "variable-candidate",
                new String[] { "varbitId", "varpId", "newValue" },
                new Object[] { event.getVarbitId(), event.getVarpId(), event.getValue() } );

        if ( ++variableRecordCount == 80 )
            logDiagnosticEvent( "variable-limit", "message", "Further variable events omitted in this capture." );
    }

    @Subscribe
    public void onWidgetLoaded( WidgetLoaded event )
    {
        endCaptureIfExpired();

        if ( isCaptureActive() )
            logDiagnosticEvent( "widget-loaded", "widgetGroupId", event.getGroupId() );
    }

    @Subscribe
    public void onGameStateChanged( GameStateChanged event )
    {
        endCaptureIfExpired();

        if ( isCaptureActive() )
            logDiagnosticEvent( "game-state", "newGameState", event.getGameState() );

        clearSnapshots();

        if ( event.getGameState() == GameState.LOGIN_SCREEN )
            endCapture( "Login screen reached; start a fresh capture for the next session" );
    }

    private void beginCapture( String reason )
    {
        endCapture( "Capture restarted" );

        captureWindow.start( monotonicTimeMillis() );
        isCaptureOpen = true;

        eventNumber = 0;
        variableRecordCount = 0;

        clearSnapshots();

        log.debug(
                "STASH_DIAG BEGIN reason={} durationSeconds={} maxRecordCount={}",
                toSingleQuotedString( reason ),
                toSingleQuotedString( CaptureWindow.DURATION_SECONDS ),
                toSingleQuotedString( CaptureWindow.MAX_RECORD_COUNT ) );
    }

    private void endCapture( String reason )
    {
        if ( isCaptureOpen )
            log.debug( "STASH_DIAG END reason={} recordCount={}", toSingleQuotedString( reason ), toSingleQuotedString( eventNumber ) );

        captureWindow.stop();
        isCaptureOpen = false;
    }

    /**
     * Closes an open capture once its duration or record budget has been exhausted.
     */
    private void endCaptureIfExpired()
    {
        if ( activeSessionToken != null && isCaptureOpen && !captureWindow.isActive( monotonicTimeMillis() ) )
            endCapture( "Time or record limit reached" );
    }

    /**
     * Checks capture availability without changing state or writing log messages.
     */
    private boolean isCaptureActive()
    {
        return activeSessionToken != null && captureWindow.isActive( monotonicTimeMillis() );
    }

    /**
     * Returns a monotonic clock reading in milliseconds for measuring capture duration.
     * <p>
     * Unlike {@link System#currentTimeMillis()}, this clock is unaffected by system clock adjustments, which could
     * otherwise make capture expire early or late.
     * <p>
     * The reading has an arbitrary origin and should only be used to measure elapsed time, not as a calendar timestamp.
     *
     * @return the current monotonic clock reading in milliseconds
     */
    private static long monotonicTimeMillis()
    {
        return TimeUnit.NANOSECONDS.toMillis( System.nanoTime() );
    }

    private void logDiagnosticEvent( String eventType, String key, Object value )
    {
        logDiagnosticEvent( eventType, new String[] { key }, new Object[] { value } );
    }

    /**
     * Logs one event using corresponding keys and values as space-separated key='value' pairs.
     *
     * @param eventType the event type
     * @param keys      detail names, in output order
     * @param values    corresponding detail values; must have the same length as keys
     */
    private void logDiagnosticEvent( String eventType, String[] keys, Object[] values )
    {
        if ( keys.length != values.length )
            throw new IllegalArgumentException( "Detail keys and values must have the same length" );

        if ( captureWindow.tryCountRecord( monotonicTimeMillis() ) )
        {
            StringJoiner fields = new StringJoiner( " " );

            fields.add( "eventNumber=" + toSingleQuotedString( ++eventNumber ) );
            fields.add( "tickCount=" + toSingleQuotedString( client.getTickCount() ) );
            fields.add( "gameState=" + toSingleQuotedString( client.getGameState() ) );
            fields.add( "eventType=" + toSingleQuotedString( eventType ) );
            fields.add( "playerLocation=" + toSingleQuotedString( getPlayerLocation() ) );

            for ( int i = 0; i < keys.length; i++ )
                fields.add( keys[ i ] + "=" + toSingleQuotedString( values[ i ] ) );

            log.debug( "STASH_DIAG {}", fields );
        }
        else
        {
            endCapture( "Time or record limit reached" );
        }
    }

    private static boolean isSupportedStashUnit( MenuOptionClicked event )
    {
        boolean isObjectAction = event.getMenuAction() == MenuAction.GAME_OBJECT_FIRST_OPTION ||
                                 event.getMenuAction() == MenuAction.GAME_OBJECT_SECOND_OPTION ||
                                 event.getMenuAction() == MenuAction.GAME_OBJECT_THIRD_OPTION ||
                                 event.getMenuAction() == MenuAction.GAME_OBJECT_FOURTH_OPTION ||
                                 event.getMenuAction() == MenuAction.GAME_OBJECT_FIFTH_OPTION;

        boolean isSupportedStashId = event.getId() == ObjectID.HH_EASY001 ||
                                     event.getId() == ObjectID.HH_EASY002;

        return isObjectAction && isSupportedStashId;
    }

    private void logItemContainerChanges( int containerId )
    {
        ItemContainer itemContainer = client.getItemContainer( containerId );

        if ( itemContainer == null )
            return;

        Item[] currentItems = itemContainer.getItems();
        Item[] previousItems = containerId == InventoryID.INV ? inventorySnapshot : equipmentSnapshot;

        if ( Arrays.equals( currentItems, previousItems ) )
            return;

        logDiagnosticEvent(
                "items",
                new String[] { "containerId", "currentItems" },
                new Object[] { containerId, currentItems } );

        if ( containerId == InventoryID.INV )
            inventorySnapshot = currentItems.clone();
        else
            equipmentSnapshot = currentItems.clone();
    }

    private void clearSnapshots()
    {
        lastLocation = null;
        inventorySnapshot = null;
        equipmentSnapshot = null;
    }

    private WorldPoint getPlayerLocation()
    {
        if ( client.getLocalPlayer() == null )
            return null;

        return client.getLocalPlayer().getWorldLocation();
    }
}
