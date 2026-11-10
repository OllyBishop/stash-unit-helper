package io.github.ollybishop.stashunithelper.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StringUtilsTest
{
    @Test
    public void formatsPrimitiveAndNestedArrays()
    {
        assertEquals( "[1, 2]", StringUtils.toReadableString( new int[] { 1, 2 } ) );
        assertEquals( "[[1, 2], [text, null]]", StringUtils.toReadableString(
                new Object[] { new int[] { 1, 2 }, new Object[] { "text", null } } ) );
        assertEquals( "[]", StringUtils.toReadableString( new int[] {} ) );
    }

    @Test
    public void preservesScalarAndNullValues()
    {
        assertEquals( "null", StringUtils.toReadableString( null ) );
        assertEquals( "42", StringUtils.toReadableString( 42 ) );
        assertEquals( "[text]", StringUtils.toReadableString( "[text]" ) );
    }

    @Test
    public void escapesQuotedAndMultilineValues()
    {
        assertEquals( "'You can\\'t use C:\\\\stash\\nnow\\t'", StringUtils.toSingleQuotedString(
                "You can't use C:\\stash\nnow\t" ) );
        assertEquals( "'null'", StringUtils.toSingleQuotedString( null ) );
        assertEquals( "'[a\\'b, c\\\\d]'", StringUtils.toSingleQuotedString(
                new String[] { "a'b", "c\\d" } ) );
    }
}
