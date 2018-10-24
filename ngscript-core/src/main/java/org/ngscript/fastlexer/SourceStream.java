/*
 *  wssccc all rights reserved
 */
package org.ngscript.fastlexer;

import org.ngscript.parseroid.parser.Token;

/**
 *
 * @author wssccc <wssccc@qq.com>
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
