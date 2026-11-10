package io.github.ollybishop.stashunithelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class StashUnitHelperLauncher
{
    // RuneLite's loadBuiltin uses generic varargs; passing this known plugin class is safe.
    @SuppressWarnings( "unchecked" )
    public static void main( String[] args ) throws Exception
    {
        ExternalPluginManager.loadBuiltin( StashUnitHelperPlugin.class );
        RuneLite.main( args );
    }
}
