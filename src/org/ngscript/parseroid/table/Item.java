/*
 *  wssccc all rights reserved
 */
package org.ngscript.parseroid.table;

import org.ngscript.parseroid.grammar.Symbol;
import org.ngscript.parseroid.grammar.Production;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Item {

    Production production;
    int pos;
    HashMap<String, Symbol> lookahead = new HashMap<String,Symbol>();

    public Item(Production production, int pos) {
        this.production = production;
        this.pos = pos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(production.sym.identifier);
        sb.append("->");
        for (int i = 0; i < production.produces.length; i++) {
            if (i == pos) {
                sb.append('*');
            }
            sb.append(production.produces[i].identifier);
            sb.append(' ');
        }
        if (pos == production.produces.length) {
            sb.append('*');
        }
        sb.append(',');
        for (String key : lookahead.keySet()) {
            sb.append(lookahead.get(key).identifier);
            sb.append('/');
        }
        return sb.toString();
    }

    public boolean addLookahead(Symbol sym) {
        if (!lookahead.containsKey(sym.identifier)) {
            lookahead.put(sym.identifier, sym);
            return true;
        }
        return false;
    }

    public boolean addLookahead(HashMap<String, Symbol> syms) {
        int n = lookahead.size();
        lookahead.putAll(syms);

        return n != lookahead.size();
    }

    public boolean eq(Item other) {
        if (this.pos != other.pos) {
            return false;
        }
        if (this.lookahead.size() != other.lookahead.size()) {
            return false;
        }

        for (String symid : other.lookahead.keySet()) {
            if (!lookahead.containsKey(symid)) {
                return false;
            }
        }
        return this.production.eq(other.production);
    }

    public boolean eqHeart(Item other) {
        if (this.pos != other.pos) {
            return false;
        }
        return this.production.eq(other.production);
    }
}
