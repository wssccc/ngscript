/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.parseroid.table;

import org.ngscript.parseroid.grammar.Production;
import org.ngscript.parseroid.grammar.Symbol;

import java.util.HashMap;

/**
 * @author wssccc
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
