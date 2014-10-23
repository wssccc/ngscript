/*
 *  wssccc all rights reserved
 */
package org.ngscript.parseroid.table;

import java.io.Serializable;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class ParserAction implements Serializable {

    public static final char REDUCE = 'r';
    public static final char ACCEPT = 'a';
    public static final char SHIFT = 's';
    public static final char GOTO = 't';

    public char action;
    public int param;

    private ParserAction(char action, int param) {
        this.action = action;
        this.param = param;
    }

    public static ParserAction reduce(int param) {
        return new ParserAction(REDUCE, param);
    }

    public static ParserAction shift(int param) {
        return new ParserAction(SHIFT, param);
    }

    public static ParserAction accept() {
        return new ParserAction(ACCEPT, 0);
    }

    public static ParserAction go(int param) {
        return new ParserAction(GOTO, param);
    }

    public boolean eq(ParserAction other) {
        return other.action == this.action && other.param == this.param;
    }

    @Override
    public String toString() {
        return "" + action + "" + param;
    }

}
