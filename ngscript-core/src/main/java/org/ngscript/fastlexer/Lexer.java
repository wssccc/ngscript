/*
 *  wssccc all rights reserved
 */
package org.ngscript.fastlexer;

import org.ngscript.parseroid.parser.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Lexer {
    private static Set<String> keywords = new HashSet<>(Arrays.asList("var", "true", "false", "null", "undefined",
            "import", "function", "new", "if", "return",
            "break", "continue", "while", "switch", "case",
            "default", "typeof", "try", "catch", "finally",
            "throw", "for", "else"));

    SourceStream ss;

    public static ArrayList<Token> scan(String string) throws LexerException {
        ArrayList<Token> tokens = new ArrayList<Token>();
        Token token;
        Lexer lexer = new Lexer(string);
        while ((token = lexer.getToken()) != null) {
            if (!token.type.equals("comment")) {
                tokens.add(token);
            }
        }
        //tokens.add(lexer.ss.token("EOF"));
        return tokens;
    }

    private Lexer(String string) {
        ss = new SourceStream(string);
    }

    Token getToken() throws LexerException {
        int marker;
        //skip blanks
        while (true) {
            char c = ss.peek();
            if ((c == ' ' || c == '\t' || c == '\r' || c == '\n') && !ss.eof()) {
                ss.forward();
            } else {
                break;
            }
        }
        //read token
        char chr;
        switch (chr = ss.read()) {
            case '/':
                if (ss.peek() == '/') {
                    marker = ss.pos + 1;
                    while (true) {
                        char c = ss.read();
                        if (c == '\0' || c == '\r' || c == '\n') {
                            return ss.token("comment", substr(marker, ss.pos - 1));
                        }
                    }
                } else {
                    return ss.token("div");
                }
            case '.':
                chr = ss.peek();
                if (isNumeric(chr)) {
                    marker = ss.pos - 1;
                    while (true) {
                        chr = ss.peek();
                        if (chr == 'e' || isNumeric(chr)) {
                            ss.forward();
                        } else {
                            return ss.token("double", substr(marker, ss.pos));
                        }
                    }
                } else {
                    return ss.token("dot", ".");
                }
            case ';':
                return ss.token("semicolon");
            case '(':
                return ss.token("lparen");
            case ')':
                return ss.token("rparen");
            case '{':
                return ss.token("lcurly");
            case '}':
                return ss.token("rcurly");
            case '[':
                return ss.token("lsqr");
            case ']':
                return ss.token("rsqr");
            case '=':
                if (ss.tryRead('=')) {
                    if (ss.tryRead('=')) {
                        return ss.token("veq");
                    } else {
                        return ss.token("eq");
                    }
                } else {
                    return ss.token("assign");
                }
            case '!':
                if (ss.tryRead('=')) {
                    if (ss.tryRead('=')) {
                        return ss.token("vneq");
                    } else {
                        return ss.token("neq");
                    }
                } else {
                    return ss.token("not");
                }
            case '<':
                return determine('=', "le", "lt");
            case '>':
                return determine('=', "ge", "gt");
            case ',':
                return ss.token("comma");
            case '|':
                return determine('|', "or", "bit_or");
            case '^':
                return ss.token("xor");
            case '&':
                return determine('&', "and", "bit_and");
            case ':':
                return ss.token("colon");
            case '?':
                return ss.token("question_mark");
            case '+':
                return determine('+', "inc", "add");
            case '-':
                return determine('-', "dec", "sub");
            case '*':
                return ss.token("mul");
            case '%':
                return ss.token("mod");
            case '"':
                marker = ss.pos;
                while (true) {
                    switch (ss.read()) {
                        case '\\':
                            ss.forward();
                            break;
                        case '\"':
                            return ss.token("string", substr(marker, ss.pos - 1));
                        case SourceStream.EOF:
                            throw new LexerException("invalid string token. \r\n" + substr(marker, ss.pos - 1));
                        default:
                            //
                    }
                }
            case SourceStream.EOF:
                return null;
            default:
                if (isAlphabet(chr) || chr == '_') {
                    marker = ss.pos - 1;
                    while (true) {
                        chr = ss.peek();
                        if (isAlphabet(chr) || chr == '_' || isNumeric(chr)) {
                            //go on
                            ss.forward();
                        } else {
                            return translateIdent(substr(marker, ss.pos));
                        }
                    }
                }
                if (isNumeric(chr)) {
                    String type = "integer";
                    marker = ss.pos - 1;
                    while (true) {
                        chr = ss.peek();
                        if (chr == '.') {
                            ss.forward();
                            type = "double";
                        } else if (chr == 'e') {
                            ss.forward();
                            ss.forward(); //skip a digit
                            type = "double";
                        } else if (isNumeric(chr)) {
                            ss.forward();
                        } else {
                            return ss.token(type, substr(marker, ss.pos));
                        }
                    }
                }
        }
        throw new LexerException("unexpected char '" + chr + "'(" + (int) chr + ")" + ss.toString());
    }

    Token translateIdent(String str) {
        if (keywords.contains(str)) {
            return ss.token(str);
        } else {
            return ss.token("ident", str);
        }
    }

    boolean isAlphabet(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    boolean isNumeric(char c) {
        return (c >= '0' && c <= '9');
    }

    Token determine(char nextchr, String ifTrue, String ifFalse) {
        if (ss.tryRead(nextchr)) {
            return ss.token(ifTrue);
        } else {
            return ss.token(ifFalse);
        }
    }

    String substr(int begin, int end) {
        return new String(ss.ch, begin, end - begin);
    }
}
