/*
 *  wssccc all rights reserved
 */
package parseroid.grammar;

import java.io.Serializable;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Production implements Serializable {

    public Symbol sym;
    public Symbol[] produces;
    public int id;

    public Production(Symbol sym, Symbol[] produces, int id) {
        this.sym = sym;
        this.produces = produces;
        this.id = id;
    }

    public boolean eq(Production other) {
        if (this.sym != other.sym) {
            return false;
        }
        if (other.produces.length != this.produces.length) {
            return false;
        }
        for (int i = 0; i < produces.length; i++) {
            if (other.produces[i] != this.produces[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sym.identifier);
        sb.append("->");
        for (Symbol produce : produces) {
            sb.append(produce.identifier);
        }
        return sb.toString();
    }

}
