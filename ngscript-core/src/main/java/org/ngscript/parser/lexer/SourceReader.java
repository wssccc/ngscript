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

package org.ngscript.parser.lexer;

/**
 * @author wssccc
 */
public class SourceReader {

    public static final char EOF = '\0';

    int lineNumber = 1;
    int position = 0;

    char[] chars;

    public SourceReader(String str) {
        this.chars = str.toCharArray();
    }

    public boolean eof() {
        return position >= chars.length;
    }

    public void forward() {
        if (chars[position] == '\n') {
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

    public char peek() {
        return position < chars.length ? chars[position] : EOF;
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
