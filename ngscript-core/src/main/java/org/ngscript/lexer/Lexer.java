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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ngscript.parser.Token;

/**
 * @author wssccc
 */
public class Lexer {

    private static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "var", "true", "false", "null", "undefined",
            "import", "function", "new", "if", "return",
            "break", "continue", "while", "switch", "case",
            "default", "typeof", "try", "catch", "finally",
            "go", "throw", "for", "else",
            "do", "in", "instanceof", "delete", "void", "with",
            // ES5 future reserved words, always reserved
            "class", "const", "enum", "export", "extends", "super",
            "debugger")));

    /**
     * Token types after which a {@code /} starts a regular expression literal
     * rather than a division (ECMAScript lexical goal rule). The stream start
     * (no previous token) also allows a regex.
     */
    private static final Set<String> REGEX_ALLOWED_AFTER = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "lparen", "lsqr", "lcurly", "comma", "semicolon", "colon",
            "question_mark", "nullish",
            "assign", "asgn_add", "asgn_sub", "asgn_mul", "asgn_div", "asgn_mod", "asgn_exp",
            "asgn_shl", "asgn_shr", "asgn_ushr", "asgn_bit_and", "asgn_bit_or", "asgn_bit_xor",
            "asgn_and", "asgn_or", "asgn_nullish",
            "eq", "veq", "neq", "vneq",
            "not", "bit_and", "bit_or", "bit_xor", "and", "or",
            "add", "sub", "mul", "div", "mod", "exp", "shl", "shr", "ushr", "bit_not",
            "lt", "gt", "le", "ge",
            "return", "typeof", "new", "throw", "case")));

    private final SourceReader reader;
    private final StringBuilder stringBuilder;

    /**
     * Type of the most recently produced non-comment token; used to decide
     * whether {@code /} starts a regular expression literal.
     */
    private String lastTokenType;

    public static List<Token> scan(String string) throws LexerException {
        return scan(string, 1);
    }

    /**
     * Scans a source string whose first line is {@code startLine}.
     */
    public static List<Token> scan(String string, int startLine) throws LexerException {
        List<Token> tokens = new ArrayList<>();
        Token token;
        Lexer lexer = new Lexer(string, startLine);
        while ((token = lexer.nextToken()) != null) {
            if (!token.type.equals("comment")) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public Lexer(String string) {
        this(string, 1);
    }

    public Lexer(String string, int startLine) {
        this.reader = new SourceReader(string, startLine);
        this.stringBuilder = new StringBuilder();
    }

    /**
     * Produces the next token. Returns {@code null} at end of input.
     * Comment tokens are returned too; callers usually filter them.
     */
    public Token nextToken() throws LexerException {
        return getToken();
    }

    Token getToken() throws LexerException {
        int marker;
        //skip blanks
        reader.skipBlanks();
        // skip a hashbang comment at the very start of the file (ES HashbangComment)
        if (reader.position == 0 && reader.peek() == '#' && reader.peek(1) == '!') {
            while (true) {
                char c = reader.read();
                if (c == '\0' || c == '\n' || c == '\r') {
                    break;
                }
            }
            return getToken();
        }
        //read token
        char chr;
        switch (chr = reader.read()) {
            case '/':
                if (isRegexAllowed(lastTokenType)) {
                    // comment markers win over regex literals even here
                    if (reader.peek() == '/') {
                        return scanLineComment();
                    }
                    if (reader.peek() == '*') {
                        return scanBlockComment();
                    }
                    return scanRegex();
                }
                if (reader.peek() == '/') {
                    return scanLineComment();
                } else if (reader.peek() == '*') {
                    return scanBlockComment();
                } else if (reader.readNext('=')) {
                    return token("asgn_div");
                } else {
                    return token("div");
                }
            case '.':
                chr = reader.peek();
                if (isNumeric(chr)) {
                    marker = reader.position - 1;
                    while (true) {
                        chr = reader.peek();
                        if (chr == 'e' || chr == 'E' || isNumeric(chr)) {
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
            case ')': {
                // the token's line must be recorded before skipping blanks
                // (the arrow-function lookahead below skips them)
                int rparenLine = reader.lineNumber;
                reader.skipBlanks();
                if (reader.peek() == '=' && reader.peek(1) == '>') {
                    reader.forward();
                    reader.forward();
                    return token("rparen_arrow_fn");
                }
                return token("rparen", rparenLine);
            }
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
                if (reader.readNext('<')) {
                    if (reader.readNext('=')) {
                        return token("asgn_shl");
                    }
                    return token("shl");
                }
                return determine('=', "le", "lt");
            case '>':
                if (reader.readNext('>')) {
                    if (reader.readNext('>')) {
                        if (reader.readNext('=')) {
                            return token("asgn_ushr");
                        }
                        return token("ushr");
                    }
                    if (reader.readNext('=')) {
                        return token("asgn_shr");
                    }
                    return token("shr");
                }
                return determine('=', "ge", "gt");
            case ',':
                return token("comma");
            case '|':
                if (reader.readNext('|')) {
                    if (reader.readNext('=')) {
                        return token("asgn_or");
                    }
                    return token("or");
                }
                if (reader.readNext('=')) {
                    return token("asgn_bit_or");
                }
                return token("bit_or");
            case '^':
                if (reader.readNext('=')) {
                    return token("asgn_bit_xor");
                }
                return token("bit_xor");
            case '&':
                if (reader.readNext('&')) {
                    if (reader.readNext('=')) {
                        return token("asgn_and");
                    }
                    return token("and");
                }
                if (reader.readNext('=')) {
                    return token("asgn_bit_and");
                }
                return token("bit_and");
            case ':':
                return token("colon");
            case '?':
                if (reader.readNext('?')) {
                    if (reader.readNext('=')) {
                        return token("asgn_nullish");
                    }
                    return token("nullish");
                }
                if (reader.peek() == '.' && !isNumeric(reader.peek(1))) {
                    reader.forward();
                    return token("optional_chain");
                }
                return token("question_mark");
            case '+':
                if (reader.readNext('+')) {
                    return token("inc");
                }
                if (reader.readNext('=')) {
                    return token("asgn_add");
                }
                return token("add");
            case '-':
                if (reader.readNext('-')) {
                    return token("dec");
                }
                if (reader.readNext('=')) {
                    return token("asgn_sub");
                }
                return token("sub");
            case '*':
                if (reader.readNext('*')) {
                    if (reader.readNext('=')) {
                        return token("asgn_exp");
                    }
                    return token("exp");
                }
                if (reader.readNext('=')) {
                    return token("asgn_mul");
                }
                return token("mul");
            case '%':
                if (reader.readNext('=')) {
                    return token("asgn_mod");
                }
                return token("mod");
            case '~':
                return token("bit_not");
            case '"':
            case '\'':
                return scanString(chr);
            case SourceReader.EOF:
                return null;
            default:
                if (isIdentStart(chr) || (chr == '\\' && reader.peek() == 'u')) {
                    // note: chr was consumed by read(), so the 'u' of an
                    // escape is at the current position
                    stringBuilder.setLength(0);
                    if (chr == '\\') {
                        reader.forward();
                        reader.forward();
                        char decoded = (char) readHexDigits(4, "unicode escape");
                        if (!isIdentStart(decoded)) {
                            throw new LexerException("invalid unicode escape at start of identifier");
                        }
                        stringBuilder.append(decoded);
                    } else {
                        stringBuilder.append(chr);
                    }
                    while (true) {
                        char c = reader.peek();
                        if (isIdentPart(c)) {
                            stringBuilder.append(c);
                            reader.forward();
                        } else if (c == '\\' && reader.peek(1) == 'u') {
                            // unicode escape inside an identifier: backslash-uXXXX
                            reader.forward();
                            reader.forward();
                            char decoded = (char) readHexDigits(4, "unicode escape");
                            if (!isIdentPart(decoded)) {
                                throw new LexerException("invalid unicode escape in identifier");
                            }
                            stringBuilder.append(decoded);
                        } else {
                            return translateIdent(stringBuilder.toString());
                        }
                    }
                }
                if (isNumeric(chr)) {
                    String type = "integer";
                    marker = reader.position - 1;
                    if (chr == '0') {
                        char p = reader.peek();
                        if (p == 'x' || p == 'X' || p == 'o' || p == 'O' || p == 'b' || p == 'B') {
                            return scanRadix();
                        }
                        if (p >= '0' && p <= '7') {
                            return scanLegacyOctal();
                        }
                    }
                    boolean hasExponent = false;
                    boolean hasDecimalPoint = false;
                    while (true) {
                        chr = reader.peek();
                        if (chr == '.') {
                            if (hasDecimalPoint) {
                                // 1..toString() -> the number ends at the first dot
                                String text = substr(marker, reader.position);
                                if (text.indexOf('_') >= 0) {
                                    text = text.replace("_", "");
                                }
                                return token(type, text);
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
                        } else if (chr == '_') {
                            // numeric separators (loosely accepted between any digits)
                            reader.forward();
                        } else if (isNumeric(chr)) {
                            reader.forward();
                        } else {
                            String text = substr(marker, reader.position);
                            if (text.indexOf('_') >= 0) {
                                text = text.replace("_", "");
                            }
                            return token(type, text);
                        }
                    }
                }
        }
        throw new LexerException("unexpected char '" + chr + "'(" + (int) chr + ")" + reader.toString());
    }

    /**
     * Scans a single or double quoted string literal, decoding escape sequences into
     * the actual characters. The returned token value contains no quotes.
     */
    private Token scanString(char quote) throws LexerException {
        stringBuilder.setLength(0);
        while (true) {
            char c = reader.read();
            if (c == '\\') {
                char next = reader.read();
                switch (next) {
                    case 'n': stringBuilder.append('\n'); break;
                    case 'r': stringBuilder.append('\r'); break;
                    case 't': stringBuilder.append('\t'); break;
                    case 'b': stringBuilder.append('\b'); break;
                    case 'f': stringBuilder.append('\f'); break;
                    case 'v': stringBuilder.append('\u000b'); break;
                    case '0': stringBuilder.append('\0'); break;
                    case '\\': stringBuilder.append('\\'); break;
                    case '"': stringBuilder.append('"'); break;
                    case '\'': stringBuilder.append('\''); break;
                    case 'x':
                        stringBuilder.append((char) readHexDigits(2, "hex escape"));
                        break;
                    case 'u':
                        if (reader.peek() == '{') {
                            // code point escape: backslash-u{1F600}
                            reader.forward();
                            int value = 0;
                            int digits = 0;
                            while (true) {
                                int d = Character.digit(reader.peek(), 16);
                                if (d < 0) {
                                    break;
                                }
                                value = value * 16 + d;
                                reader.forward();
                                ++digits;
                            }
                            if (digits == 0 || reader.peek() != '}' || value > 0x10FFFF) {
                                throw new LexerException("invalid unicode code point escape");
                            }
                            reader.forward();
                            if (value > 0xFFFF) {
                                // encode as a surrogate pair
                                value -= 0x10000;
                                stringBuilder.append((char) (0xD800 + (value >> 10)));
                                stringBuilder.append((char) (0xDC00 + (value & 0x3FF)));
                            } else {
                                stringBuilder.append((char) value);
                            }
                        } else {
                            stringBuilder.append((char) readHexDigits(4, "unicode escape"));
                        }
                        break;
                    case SourceReader.EOF:
                        throw new LexerException("unclosed string literal");
                    default:
                        if (next == '\n' || next == '\r' || next == '\u2028' || next == '\u2029') {
                            // LineContinuation: the backslash and the line terminator vanish
                            break;
                        }
                        // a backslash before any other character (NonEscapeCharacter)
                        // keeps the character, e.g. \A == "A"
                        stringBuilder.append(next);
                        break;
                }
            } else if (c == quote) {
                return token("string", stringBuilder.toString());
            } else if (c == SourceReader.EOF || c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
                throw new LexerException("unclosed string literal");
            } else {
                stringBuilder.append(c);
            }
        }
    }

    private int readHexDigits(int count, String what) throws LexerException {
        int value = 0;
        for (int i = 0; i < count; i++) {
            int d = Character.digit(reader.read(), 16);
            if (d < 0) {
                throw new LexerException("invalid " + what);
            }
            value = value * 16 + d;
        }
        return value;
    }

    /**
     * Scans a 0x/0o/0b literal and returns a decimal integer token when the value fits
     * in a Java int, otherwise a double token (ES numbers are IEEE-754 doubles).
     */
    private Token scanRadix() throws LexerException {
        char prefix = reader.read();
        int radix = (prefix == 'x' || prefix == 'X') ? 16 : ((prefix == 'o' || prefix == 'O') ? 8 : 2);
        stringBuilder.setLength(0);
        boolean any = false;
        while (true) {
            char c = reader.peek();
            if (c == '_') {
                reader.forward();
                continue;
            }
            int d = Character.digit(c, radix);
            if (d < 0) {
                break;
            }
            stringBuilder.append(c);
            reader.forward();
            any = true;
        }
        if (!any) {
            throw new LexerException("invalid number format: missing digits after 0" + prefix);
        }
        BigInteger big = new BigInteger(stringBuilder.toString(), radix);
        String dec = big.toString(10);
        if (big.bitLength() <= 31) {
            return token("integer", dec);
        }
        double dbl = big.doubleValue();
        if (Double.isInfinite(dbl)) {
            return token("double", "Infinity");
        }
        return token("double", dec);
    }

    /**
     * Scans a legacy octal literal like {@code 010} (== 8). When a digit 8 or
     * 9 appears the literal is treated as a decimal number (ES2015+
     * NonOctalDecimalIntegerLiteral semantics for 08/09).
     */
    private Token scanLegacyOctal() throws LexerException {
        stringBuilder.setLength(0);
        boolean nonOctal = false;
        while (true) {
            char c = reader.peek();
            if (c >= '0' && c <= '7') {
                stringBuilder.append(c);
                reader.forward();
            } else if (c == '8' || c == '9') {
                nonOctal = true;
                stringBuilder.append(c);
                reader.forward();
            } else if (c == '_') {
                reader.forward();
            } else {
                break;
            }
        }
        String text = stringBuilder.toString().replace("_", "");
        if (nonOctal) {
            return token("integer", text);
        }
        BigInteger big = new BigInteger(text, 8);
        String dec = big.toString(10);
        if (big.bitLength() <= 31) {
            return token("integer", dec);
        }
        double dbl = big.doubleValue();
        if (Double.isInfinite(dbl)) {
            return token("double", "Infinity");
        }
        return token("double", dec);
    }

    /**
     * Whether {@code /} at the current position starts a regular expression
     * literal according to the ECMAScript lexical goal rule.
     */
    static boolean isRegexAllowed(String previousTokenType) {
        return previousTokenType == null || REGEX_ALLOWED_AFTER.contains(previousTokenType);
    }

    /**
     * Scans a regular expression literal; the opening slash has been consumed.
     * The returned token value is the full literal text including slashes and flags.
     */
    private Token scanRegex() throws LexerException {
        stringBuilder.setLength(0);
        stringBuilder.append('/');
        boolean inClass = false;
        while (true) {
            char c = reader.read();
            if (c == '\\') {
                char next = reader.read();
                if (next == SourceReader.EOF || next == '\n' || next == '\r') {
                    throw new LexerException("unclosed regular expression literal");
                }
                stringBuilder.append('\\').append(next);
            } else if (c == '[') {
                inClass = true;
                stringBuilder.append(c);
            } else if (c == ']') {
                inClass = false;
                stringBuilder.append(c);
            } else if (c == '/' && !inClass) {
                stringBuilder.append('/');
                while (true) {
                    char f = reader.peek();
                    if (isIdentPart(f)) {
                        stringBuilder.append(f);
                        reader.forward();
                    } else {
                        break;
                    }
                }
                return token("regex", stringBuilder.toString());
            } else if (c == SourceReader.EOF || c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
                throw new LexerException("unclosed regular expression literal");
            } else {
                stringBuilder.append(c);
            }
        }
    }

    private Token scanLineComment() throws LexerException {
        int marker = reader.position + 1;
        while (true) {
            char c = reader.read();
            if (c == '\0' || c == '\r' || c == '\n' || c == '\u2028' || c == '\u2029') {
                return tokenComment(substr(marker, reader.position - 1));
            }
        }
    }

    private Token scanBlockComment() throws LexerException {
        int marker = reader.position + 1;
        reader.forward(); // Skip the '*'
        int commentStartLine = reader.lineNumber;
        while (true) {
            char c = reader.read();
            if (c == '\0') {
                throw new LexerException("unclosed multiline comment starting at line " + commentStartLine);
            }
            if (c == '*' && reader.peek() == '/') {
                reader.forward(); // Skip the '/'
                return tokenComment(substr(marker, reader.position - 2));
            }
        }
    }

    Token translateIdent(String str) {
        if (KEYWORDS.contains(str)) {
            return token(str);
        } else {
            return token("ident", str);
        }
    }

    static boolean isIdentStart(char c) {
        // exclude the EOF sentinel: Java 8 reports isUnicodeIdentifierPart('\0') == true
        return c != '\0' && (Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_');
    }

    static boolean isIdentPart(char c) {
        return c != '\0' && (Character.isUnicodeIdentifierPart(c) || c == '$');
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

    private Token token(String type, int line) {
        lastTokenType = type;
        return new Token(type, line);
    }

    private Token token(String type) {
        lastTokenType = type;
        return new Token(type, reader.lineNumber);
    }

    private Token token(String type, String value) {
        lastTokenType = type;
        return new Token(type, reader.lineNumber, value);
    }

    /**
     * Comments never change the previous-token state used for regex detection.
     */
    private Token tokenComment(String value) {
        return new Token("comment", reader.lineNumber, value);
    }
}
