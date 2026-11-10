# Formatting Conventions

This document records the intent behind Bishop Java Style for maintainers and coding agents. The XML profile defines the formatter settings; these notes explain the choices. Keep both consistent when changing the style.

## Indentation and Line Length

Use four spaces for block indentation and eight spaces for continuation indentation. Avoid tabs. The preferred line width is 120 characters; this is a wrapping guide rather than a requirement to split every longer line.

Wrapped method arguments and declaration parameters use fixed continuation indentation. Method arguments deliberately do not align beneath the first argument: that setting also pushes lambda blocks across the page. Fixed indentation keeps lambda blocks readable and avoids indentation depending on the length of a method name.

```java
Arrays.copyOfRange( values,
        from,
        to );

Arrays.copyOfRange(
        values,
        from,
        to );

new Thread( () ->
{
    System.out.println( "Hello, world!" );
} ).start();
```

Multiline binary expressions align with their first operand. Wrapped array initialiser elements align with their first element. These separate alignment rules remain enabled.

```java
return x >= 0 &&
       x < width &&
       y >= 0 &&
       y < height;

Point[] points = new Point[] { new Point( 0, 0 ),
                               new Point( 1, 1 ) };
```

## Spaces Inside Delimiters

Pad non-empty parentheses with spaces in calls, declarations, conditions and annotations. Keep casts unpadded and empty parentheses as `()`.

Pad expressions inside square brackets. Add a space before an array initialiser's opening brace and spaces inside non-empty initialiser braces. Empty array brackets remain `[]` and empty initialisers remain `{}`.

```java
void move( int x, int y )
if ( x > 0 )
System.out.println( values[ i ] );
new int[] { 1, 2, 3 }
new int[ length ]
(int) number
list.isEmpty()
```

The padding is intentional: preserve it rather than replacing it with an IDE's default Java spacing.

## Braces

Place class, method, block and lambda braces on a new line. Place `else`, `catch` and `finally` on new lines, along with the trailing `while` in a `do` loop. Keep simple classes, methods and blocks expanded across lines.

Do not force optional braces around single-statement conditions or loops. Reformatting should preserve the author's choice of whether to use those braces.

## Imports

Prefer explicit imports to wildcard imports. The profile raises the thresholds for wildcard conversion and removes the package exceptions that would otherwise introduce wildcard imports. Use **Optimize Imports** to remove unused imports and organise the remaining ones.

## Javadoc

Align parameter descriptions and indent wrapped tag descriptions. For `@return`, continuation text should begin beneath the description's first word:

```java
 * @return true if this list contains no elements;
 *         false otherwise
```

IntelliJ uses the general continuation indent for wrapped `@return` descriptions. The eight-space continuation indent therefore also supports this layout; changing it affects both code and Javadoc.

## Maintaining the Profile

Keep formatting changes separate from behaviour changes. When adjusting the profile, check examples involving ordinary method calls as well as lambdas: settings can affect both. Update this document if the intended layout changes and re-import the XML into IntelliJ to apply the new settings.
