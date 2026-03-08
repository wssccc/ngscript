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

package org.ngscript.parser;

/**
 * A lexical token produced by {@link org.ngscript.lexer.Lexer} and consumed by the parser.
 *
 * @author wssccc
 */
public class Token {

    public String type;
    public String value;
    public int line;

    /**
     * The raw (pre-escape-decoding) text for template chunks, used by tagged
     * templates (String.raw); null for other tokens.
     */
    public String raw;

    /** Whether a string literal contained an escape sequence (a directive
     * with escapes is not "use strict" per ES). */
    public boolean hasEscape;

    public boolean isValidPos() {
        return line != -1;
    }

    public Token(String type) {
        this.type = type;
        this.line = -1;
    }

    public Token(String type, int line) {
        this.type = type;
        this.line = line;
    }

    public Token(String type, int line, String value) {
        this.type = type;
        this.line = line;
        this.value = value;
    }

    public Token(String type, int line, String value, String raw) {
        this.type = type;
        this.line = line;
        this.value = value;
        this.raw = raw;
    }

    @Override
    public String toString() {
        return "[" + type + (value == null ? "" : "," + value) + "]" + (line >= 0 ? (" line:" + line) : (""));
    }
}
