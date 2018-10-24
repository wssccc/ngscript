/*
 *  wssccc all rights reserved
 */
package org.ngscript.parseroid.grammar;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
@ToString
@EqualsAndHashCode
public class Symbol implements Serializable{

    public static final Symbol NULL = new Symbol("NULL", true);
    public static final Symbol EOF = new Symbol("EOF", true);
    public static final Symbol ERROR = new Symbol("ERROR", true);

    public String identifier;
    public boolean isTerminal;

    private Symbol(String identifier, boolean isTerminal) {
        this.identifier = identifier;
        this.isTerminal = isTerminal;
    }

    public static Symbol create(String identifier, boolean isTerminal) {
        return new Symbol(identifier, isTerminal);
    }
}
