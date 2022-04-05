package com.axinom.drm.sample.license.internal.utils;

import com.axinom.drm.sample.BuildConfig;

import java.util.Locale;

/**
 * Class holds most commonly used text utils
 */

@SuppressWarnings("WeakerAccess")
public class LicenseManagerUtils {

    /**
     * Returns whether the given character is a carriage return ('\r') or a line feed ('\n').
     *
     * @param c The character.
     * @return Whether the given character is a linebreak.
     */
    public static boolean isLinebreak(int c) {
        return c == '\n' || c == '\r';
    }

    /**
     * Returns the integer equal to the big-endian concatenation of the characters in {@code string}
     * as bytes. The string must be no more than four characters long.
     *
     * @param string A string no more than four characters long.
     * @return  integer code for string
     */
    public static int getIntegerCodeForString(String string) {
        int length = string.length();
        if (BuildConfig.DEBUG && !(length <= 4)) {
            throw new IllegalArgumentException();
        }
        int result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 8;
            result |= string.charAt(i);
        }
        return result;
    }

    /**
     * Tests two objects for {@link Object#equals(Object)} equality, handling the case where one or
     * both may be null.
     *
     * @param o1 The first object.
     * @param o2 The second object.
     * @return {@code o1 == null ? o2 == null : o1.equals(o2)}.
     */
    public static boolean areEqual(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    /**
     * Converts text to lower case using {@link Locale#US}.
     *
     * @param text The text to convert.
     * @return The lower case text, or null if {@code text} is null.
     */
    public static String toLowerInvariant(String text) {
        return text == null ? null : text.toLowerCase(Locale.US);
    }

}
