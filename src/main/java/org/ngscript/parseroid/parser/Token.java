/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ngscript.parseroid.parser;

/**
 *
 * @author wssccc
 */
public class Token {

    public String type;
    public String value;
    public int line;

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

    @Override
    public String toString() {
        return "[" + type + (value == null ? "" : "," + value) + "]" + (line >= 0 ? (" line:" + line) : (""));
    }

}
