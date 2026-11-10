package io.github.ollybishop.stashunithelper.util;

import java.util.Arrays;

/**
 * Utility methods for converting and formatting text.
 */
public final class StringUtils
{
    private StringUtils()
    {
    }

    /**
     * Converts an object to readable text, expanding the contents of primitive and nested arrays.
     * <p>
     * Examples (input followed by the returned string):
     * <pre>{@code
     * 42                                          -> "42"
     * "Search"                                    -> "Search"
     * new int[] { 1, 2 }                          -> "[1, 2]"
     * new Object[] { new int[] { 1, 2 }, "text" } -> "[[1, 2], text]"
     * new int[] {}                                -> "[]"
     * null                                        -> "null"
     * }</pre>
     *
     * @param value value to convert; may be an array or null
     * @return the value's text, with array contents expanded
     */
    public static String toReadableString( Object value )
    {
        String wrappedValue = Arrays.deepToString( new Object[] { value } );
        return wrappedValue.substring( 1, wrappedValue.length() - 1 );
    }

    /**
     * Converts a value to readable text and wraps it in single quotes.
     * Array contents are expanded; backslashes, single quotes, carriage returns, line feeds and tabs are escaped.
     *
     * @param value value to convert; may be an array or null
     * @return escaped text surrounded by single quotes
     */
    public static String toSingleQuotedString( Object value )
    {
        String escapedValue = toReadableString( value )
                .replace( "\\", "\\\\" )
                .replace( "'", "\\'" )
                .replace( "\r", "\\r" )
                .replace( "\n", "\\n" )
                .replace( "\t", "\\t" );

        return "'" + escapedValue + "'";
    }
}
