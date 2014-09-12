/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Lexeroid;

/**
 *
 * @author wssccc
 */
public class LexToken {

    public String type;
    public int line;
    public String value;

    public LexToken(String type, int line) {
        this.type = type;
        this.line = line;
    }

    public LexToken(String type, int line, String value) {
        this.type = type;
        this.line = line;
        this.value = value;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 79 * hash + this.line;
        hash = 79 * hash + (this.value != null ? this.value.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LexToken other = (LexToken) obj;
        if ((this.type == null) ? (other.type != null) : !this.type.equals(other.type)) {
            return false;
        }
        if (this.line != other.line) {
            return false;
        }
        return !((this.value == null) ? (other.value != null) : !this.value.equals(other.value));
    }

    @Override
    public String toString() {
        return "\r\nToken{" + "type=" + type + ", line=" + line + ", value=" + value + '}';
    }

}
