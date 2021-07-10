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

package org.ngscript.fastlexer;

import org.ngscript.parseroid.parser.Token;

/**
 * @author wssccc
 */
public class SourceStream {

    public static final char EOF = '\0';
    int line_no = 1;
    char ch[];
    int pos = 0;

    public SourceStream(String str) {
        this.ch = str.toCharArray();
    }

    public boolean eof() {
        return pos >= ch.length;
    }

    public void forward() {
        if (ch[pos] == '\n') {
            ++line_no;
        }
        ++pos;
    }

    public char read() {
        char c = peek();
        if (c != EOF) {
            forward();
        }
        return c;
    }

    public char peek() {
        return pos < ch.length ? ch[pos] : EOF;
    }

    public boolean tryRead(char c) {
        if (peek() == c) {
            forward();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "at line " + line_no + " char=" + (eof() ? "EOF" : "'" + (char) peek() + "'(" + peek() + ")") + "";
    }

    Token token(String type) {
        return new Token(type, line_no);
    }

    Token token(String type, String value) {
        return new Token(type, line_no, value);
    }
}
