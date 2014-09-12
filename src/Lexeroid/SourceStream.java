/*
 *  wssccc all rights reserved
 */
package Lexeroid;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class SourceStream {

    public int line = 0;
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
            ++line;
        }
        ++pos;
    }

    public int peek() {
        return pos < ch.length ? ch[pos] : -1;
    }

    @Override
    public String toString() {
        return "at line " + line + " char=" + (eof() ? "EOF" : "'" + (char) peek() + "'(" + peek() + ")") + "";
    }

}
