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
    public int line_no;

    public boolean isValidPos() {
        return line_no != -1;
    }

    public Token(String type) {
        this.type = type;
        this.line_no = -1;
    }

    public Token(String type, int line) {
        this.type = type;
        this.line_no = line;
    }

    public Token(String type, int line, String value) {
        this.type = type;
        this.line_no = line;
        this.value = value;
    }

    @Override
    public String toString() {
        return "[" + type + (value == null ? "" : "," + value) + "]" + (line_no >= 0 ? (" line:" + line_no) : (""));
    }

}
