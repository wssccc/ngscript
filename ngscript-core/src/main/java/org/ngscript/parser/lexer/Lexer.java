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

import org.ngscript.parseroid.parser.Token;

import java.util.*;

/**
 * @author wssccc
 */
public class Lexer {

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "var", "true", "false", "null", "undefined",
            "import", "function", "new", "if", "return",
            "break", "continue", "while", "switch", "case",
            "default", "typeof", "try", "catch", "finally",
            "throw", "for", "else")));

    SourceReader reader;

    public static List<Token> scan(String string) throws LexerException {
        List<Token> tokens = new ArrayList<>();
        Token token;
        Lexer lexer = new Lexer(string);
        while ((token = lexer.getToken()) != null) {
            if (!token.type.equals("comment")) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Lexer(String string) {
        reader = new SourceReader(string);
    }

    Token getToken() throws LexerException {
        int marker;
        //skip blanks
        while (true) {
            char c = reader.peek();
            if ((c == ' ' || c == '\t' || c == '\r' || c == '\n') && !reader.eof()) {
                reader.forward();
            } else {
                break;
            }
        }
        //read token
        char chr;
        switch (chr = reader.read()) {
            case '/':
                if (reader.peek() == '/') {
                    marker = reader.position + 1;
                    while (true) {
                        char c = reader.read();
                        if (c == '\0' || c == '\r' || c == '\n') {
                            return token("comment", substr(marker, reader.position - 1));
                        }
                    }
                } else {
                    return token("div");
                }
            case '.':
                chr = reader.peek();
                if (isNumeric(chr)) {
                    marker = reader.position - 1;
                    while (true) {
                        chr = reader.peek();
                        if (chr == 'e' || isNumeric(chr)) {
                            reader.forward();
                        } else {
                            return token("double", substr(marker, reader.position));
                        }
                    }
                } else {
                    return token("dot", ".");
                }
            case ';':
                return token("semicolon");
            case '(':
                return token("lparen");
            case ')':
                return token("rparen");
            case '{':
                return token("lcurly");
            case '}':
                return token("rcurly");
            case '[':
                return token("lsqr");
            case ']':
                return token("rsqr");
            case '=':
                if (reader.readNext('=')) {
                    if (reader.readNext('=')) {
                        return token("veq");
                    } else {
                        return token("eq");
                    }
                } else {
                    return token("assign");
                }
            case '!':
                if (reader.readNext('=')) {
                    if (reader.readNext('=')) {
                        return token("vneq");
                    } else {
                        return token("neq");
                    }
                } else {
                    return token("not");
                }
            case '<':
                return determine('=', "le", "lt");
            case '>':
                return determine('=', "ge", "gt");
            case ',':
                return token("comma");
            case '|':
                return determine('|', "or", "bit_or");
            case '^':
                return token("xor");
            case '&':
                return determine('&', "and", "bit_and");
            case ':':
                return token("colon");
            case '?':
                return token("question_mark");
            case '+':
                return determine('+', "inc", "add");
            case '-':
                return determine('-', "dec", "sub");
            case '*':
                return token("mul");
            case '%':
                return token("mod");
            case '"':
                marker = reader.position;
                while (true) {
                    switch (reader.read()) {
                        case '\\':
                            reader.forward();
                            break;
                        case '\"':
                            return token("string", substr(marker, reader.position - 1));
                        case SourceReader.EOF:
                            throw new LexerException("invalid string token. \r\n" + substr(marker, reader.position - 1));
                        default:
                            //
                    }
                }
            case SourceReader.EOF:
                return null;
            default:
                if (isAlphabet(chr) || chr == '_') {
                    marker = reader.position - 1;
                    while (true) {
                        chr = reader.peek();
                        if (isAlphabet(chr) || chr == '_' || isNumeric(chr)) {
                            //go on
                            reader.forward();
                        } else {
                            return translateIdent(substr(marker, reader.position));
                        }
                    }
                }
                if (isNumeric(chr)) {
                    String type = "integer";
                    marker = reader.position - 1;
                    while (true) {
                        chr = reader.peek();
                        if (chr == '.') {
                            reader.forward();
                            type = "double";
                        } else if (chr == 'e') {
                            reader.forward();
                            reader.forward(); //skip a digit
                            type = "double";
                        } else if (isNumeric(chr)) {
                            reader.forward();
                        } else {
                            return token(type, substr(marker, reader.position));
                        }
                    }
                }
        }
        throw new LexerException("unexpected char '" + chr + "'(" + (int) chr + ")" + reader.toString());
    }

    Token translateIdent(String str) {
        if (KEYWORDS.contains(str)) {
            return token(str);
        } else {
            return token("ident", str);
        }
    }

    boolean isAlphabet(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    boolean isNumeric(char c) {
        return (c >= '0' && c <= '9');
    }

    Token determine(char next, String ifTrue, String ifFalse) {
        if (reader.readNext(next)) {
            return token(ifTrue);
        } else {
            return token(ifFalse);
        }
    }

    String substr(int begin, int end) {
        return new String(reader.chars, begin, end - begin);
    }

    private Token token(String type) {
        return new Token(type, reader.lineNumber);
    }

    private Token token(String type, String value) {
        return new Token(type, reader.lineNumber, value);
    }
}
