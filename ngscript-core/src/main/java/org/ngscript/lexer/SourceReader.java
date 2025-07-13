/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.lexer;

/**
 * @author wssccc
 */
public class SourceReader {

    public static final char EOF = '\0';

    int lineNumber = 1;
    int position = 0;

    char[] chars;

    public SourceReader(String str) {
        this(str, 1);
    }

    /**
     * Creates a reader whose first character is considered to be at
     * {@code startLine} (used by interactive mode so that automatic
     * semicolon insertion can detect line breaks across input batches).
     */
    public SourceReader(String str, int startLine) {
        this.chars = str.toCharArray();
        this.lineNumber = startLine;
    }

    public boolean eof() {
        return position >= chars.length;
    }

    public void forward() {
        char c = chars[position];
        if (c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
            ++lineNumber;
        }
        ++position;
    }

    public char read() {
        char c = peek();
        if (c != EOF) {
            forward();
        }
        return c;
    }

    public void skipBlanks() {
        //skip blanks (ECMAScript WhiteSpace + LineTerminator)
        while (true) {
            char c = peek();
            if ((c == ' ' || c == '\t' || c == '\u000B' || c == '\u000C'
                    || c == '\u00A0' || c == '\r' || c == '\n' || c == '\u2028'
                    || c == '\u2029' || c == '\uFEFF') && !eof()) {
                forward();
            } else {
                break;
            }
        }
    }

    public char peek() {
        return position < chars.length ? chars[position] : EOF;
    }

    /**
     * Peeks the character at {@code offset} positions ahead of the current
     * position without consuming anything. Returns {@link #EOF} past the end.
     */
    public char peek(int offset) {
        int p = position + offset;
        return p < chars.length ? chars[p] : EOF;
    }

    public boolean readNext(char c) {
        if (peek() == c) {
            forward();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "at line " + lineNumber + " char=" + (eof() ? "EOF" : "'" + (char) peek() + "'(" + peek() + ")") + "";
    }
}
