package com.gammonscope.parser;

public interface GameFileParser {

    /** Whether this parser can handle the given file name / content. */
    boolean supports(String fileName, String content);

    /** The short format tag stored on the match (e.g. "MAT", "SGF"). */
    String formatTag();

    ParsedMatch parse(String content, String fileName);
}
