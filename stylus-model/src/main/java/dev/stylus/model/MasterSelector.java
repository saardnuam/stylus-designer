package dev.stylus.model;

/**
 * One fo:conditional-page-master-reference row (F-2.27): which master applies when. Null
 * conditions mean "any"; XSL-FO picks the first row whose conditions all match, so the last
 * row should normally be unconditional.
 */
public record MasterSelector(
        String masterName,
        String pagePosition,   // null|first|last|rest|only
        String oddOrEven,      // null|odd|even
        String blankOrNot) {   // null|blank|not-blank

    public static MasterSelector any(String masterName) {
        return new MasterSelector(masterName, null, null, null);
    }
}
