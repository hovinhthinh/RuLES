package de.mpii.util;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by thinhhv on 23/08/2014.
 */
public class TParser {

    // ======================================================================

    /**
     * Return a list of String between 2 tags
     *
     * @param content
     * @param openTag
     * @param closeTag
     * @return
     */
    public static ArrayList<String> getContentList(String content, String openTag, String closeTag) {
        ArrayList<String> result = new ArrayList<String>();
        String[] split1 = content.split(openTag);
        for (int i = 1; i < split1.length; i++) {
            String[] split2 = split1[i].split(closeTag, 2);
            if (split2.length > 0) {
                result.add(split2[0]);
            }
        }
        return result;
    }

    // ======================================================================

    /**
     * GET String between 2 tags.
     *
     * @param content
     * @param openTag
     * @param closeTag
     * @return String between the tags. If wasn't able to find either the
     * first or the second tag - return null
     */
    public static String getContent(String content, String openTag, String closeTag) {
        String[] split1 = content.split(openTag, 2);
        for (int i = 1; i < split1.length; i++) {
            String[] split2 = split1[i].split(closeTag, 2);
            if (split2.length > 1) {
                return split2[0];
            }
        }
        return null;
    }

    // ======================================================================

    /**
     * Get List String with regex , use group
     *
     * @param content
     * @param regex
     * @return
     */
    public static ArrayList<String> getAllInGroup(String content, String regex) {
        Pattern p = Pattern.compile(regex);
        return getAllInGroup(content, p);
    }

    /**
     * Get all string match with pattern
     *
     * @param content
     * @param p       Pattern
     * @return
     */
    public static ArrayList<String> getAllInGroup(String content, Pattern p) {
        ArrayList<String> result = null;
        Matcher m = p.matcher(content);
        while (m.find()) {
            if (result == null) {
                result = new ArrayList<String>();
            }
            result.add(m.group());
        }
        return result;
    }

    // ==================================================================================\

    /**
     * Get first String with regex use group
     *
     * @param content
     * @param regex
     * @return
     */
    public static String getOneInGroup(String content, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        return getOneInGroup(content, p);
    }

    /**
     * Get String math with pattern
     *
     * @param content
     * @param p       Pattern
     * @return
     */
    public static String getOneInGroup(String content, Pattern p) {
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    // ======================================================================

    /**
     * Check a string match with a regex or not
     *
     * @param content
     * @param regex
     * @return
     */
    public static boolean containSubstringWithFormatAsRegex(String content, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        return containSubstringWithFormatAsRegex(content, p);
    }

    // ======================================================================

    /**
     * Check a string match with a pattern or not
     *
     * @param content
     * @param p
     * @return
     */
    public static boolean containSubstringWithFormatAsRegex(String content, Pattern p) {
        Matcher m = p.matcher(content);
        return m.find();
    }

    public static Pair<Integer, Integer> getRangeOfTagContains(String content, String plainPattern) {
        return getRangeOfTagContains(content, plainPattern, 0, null);
    }

    /**
     * <p>
     * Find tag which has class or style field contains
     * <code>plainPattern</code> (inside a pair of quotes). If multiple tag
     * found, return the last one. </p>
     * <p>
     * Eg. <ul> <li>&lt;span style=&quot;display:none&quot; ... /&gt;</li>
     * <li>&lt;span
     * style=&quot;display:none&quot;&gt;&0000000000000319.000000&lt;a
     * href=&quot;&quot;&gt;XYZ&lt;/a&gt;&lt;img
     * src=&quot;xyz.jpg&quot;/&gt;&lt;/span&gt;</li> <li>&lt;table
     * class=&quot;infobox&quot; cellspacing=&quot;5&quot;
     * style=&quot;border-spacing: 3px; width:22em;&quot;&gt;...&lt;/table>
     * <li>&lt;sup id=&quot;cite_ref-2&quot;&gt;&lt;a
     * href=&quot;#cite_note-2&quot;&gt;[2]&lt;/a>&lt;/sup&gt;</li> </li>
     * </ul>
     * </p>
     *
     * @param content
     * @param plainPattern
     * @param startPosition
     * @param tagName
     * @return range[begin, end) or null if not found.
     */
    public static Pair<Integer, Integer> getRangeOfTagContains(String content, String plainPattern, int startPosition, String tagName) {
        int index = startPosition - 1;
        for (; ; ) {
            index = content.indexOf(plainPattern, index + 1);
            if (index < 0) {
                break;
            }
            int bIndex = -1, eIndex = -1;
            boolean hasQuote = false;
            for (int i = index - 1; i >= 0; i--) {
                if (content.charAt(i) == '<') {
                    bIndex = i;
                    break;
                } else if (content.charAt(i) == '"' || content.charAt(i) == '\'') {
                    hasQuote = true;
                }
            }
            if (bIndex == -1 || !hasQuote) {
                continue;
            }
            if (tagName != null && content.indexOf(tagName, bIndex + 1) != bIndex + 1) {
                continue;
            }
            hasQuote = false;
            for (int i = index + plainPattern.length(); i < content.length(); i++) {
                if (content.charAt(i) == '>') {
                    eIndex = i;
                    break;
                } else if (content.charAt(i) == '"' || content.charAt(i) == '\'') {
                    hasQuote = true;
                }
            }
            if (eIndex == -1 || !hasQuote) {
                continue;
            }
            int lastIndex = findLastIndexOfCompleteTag(content, bIndex);
            if (lastIndex < 0) {
                continue;
            }
            return new Pair<Integer, Integer>(bIndex, lastIndex);
        }
        return null;
    }

    private static int findLastIndexOfCompleteTag(String content, int bIndex) {
        int openCount = 0, closeCount = 0, lastIndex = -1, leng = content.length();
        for (int index = bIndex; index < leng; index++) {
            if (content.charAt(index) == '<') {
                if (index + 1 < leng && content.charAt(index + 1) == '/') {
                    ++closeCount;
                    if (closeCount > openCount) {
                        break;
                    }
                    ++index;
                    if (closeCount == openCount) {
                        for (; index < leng; index++) {
                            if (content.charAt(index) == '>') {
                                lastIndex = index + 1;
                                break;
                            }
                        }
                        break;
                    }
                } else {
                    ++openCount;
                }
            } else if (content.charAt(index) == '>') {
                if (content.charAt(index - 1) == '/') {
                    ++closeCount;
                    if (closeCount > openCount) {
                        break;
                    }
                }
            }
        }
        return lastIndex;
    }

    public static String removeJSScriptInTag(String tag) {
        tag = tag.replaceAll("on((change)|(click)|(load)|(submit)|(blur))\\s*+=\\s*+'[^']+'", " ");
        tag = tag.replaceAll("on((change)|(click)|(load)|(submit)|(blur))\\s*+=\\s*+\"[^\"]+\"", " ");
        return tag;
    }

    public static String simpleRemoveTag(String tag) {
        tag = removeJSScriptInTag(tag);
        return tag.replaceAll("<[^>]++>", " ").replaceAll("\\s++", " ").trim();
    }
}