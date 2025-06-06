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
            "go", "throw", "for", "else", "val")));

    private final SourceReader reader;
    private final StringBuilder stringBuilder;

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
        this.reader = new SourceReader(string);
        this.stringBuilder = new StringBuilder();
    }

    Token getToken() throws LexerException {
        int marker;
        //skip blanks
        reader.skipBlanks();
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
                } else if (reader.peek() == '*') {
                    // Handle multiline comment
                    marker = reader.position + 1;
                    reader.forward(); // Skip the '*'
                    int commentStartLine = reader.lineNumber;
                    while (true) {
                        char c = reader.read();
                        if (c == '\0') {
                            throw new LexerException("unclosed multiline comment starting at line " + commentStartLine);
                        }
                        if (c == '*' && reader.peek() == '/') {
                            reader.forward(); // Skip the '/'
                            return token("comment", substr(marker, reader.position - 2));
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
                reader.skipBlanks();
                if (reader.readNext('=')) {
                    if (reader.readNext('>')) {
                        return token("rparen_arrow_fn");
                    }
                }
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
                    char c = reader.read();
                    switch (c) {
                        case '\\':
                            // Handle escape sequences
                            char next = reader.read();
                            switch (next) {
                                case 'n': case 'r': case 't': case '\\': case '"': case '\'':
                                    break;
                                default:
                                    throw new LexerException("invalid escape sequence: \\" + next);
                            }
                            break;
                        case '\"':
                            return token("string", substr(marker, reader.position - 1));
                        case SourceReader.EOF:
                            throw new LexerException("unclosed string literal");
                        default:
                            // Normal character
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
                    boolean hasExponent = false;
                    boolean hasDecimalPoint = false;
                    while (true) {
                        chr = reader.peek();
                        if (chr == '.') {
                            if (hasDecimalPoint) {
                                throw new LexerException("invalid number format: multiple decimal points");
                            }
                            reader.forward();
                            type = "double";
                            hasDecimalPoint = true;
                        } else if (chr == 'e' || chr == 'E') {
                            if (hasExponent) {
                                throw new LexerException("invalid number format: multiple exponents");
                            }
                            reader.forward();
                            hasExponent = true;
                            type = "double";
                            // Handle optional sign after exponent
                            if (reader.peek() == '+' || reader.peek() == '-') {
                                reader.forward();
                            }
                            // Must have at least one digit after exponent
                            if (!isNumeric(reader.peek())) {
                                throw new LexerException("invalid number format: missing digits after exponent");
                            }
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
        stringBuilder.setLength(0);
        stringBuilder.append(reader.chars, begin, end - begin);
        return stringBuilder.toString();
    }

    private Token token(String type) {
        return new Token(type, reader.lineNumber);
    }

    private Token token(String type, String value) {
        return new Token(type, reader.lineNumber, value);
    }
}
