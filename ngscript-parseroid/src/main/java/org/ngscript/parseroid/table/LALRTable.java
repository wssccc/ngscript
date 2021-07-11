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

import org.ngscript.parseroid.grammar.Grammar;
import org.ngscript.parseroid.grammar.Production;
import org.ngscript.parseroid.grammar.Symbol;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author wssccc
 */
public class LALRTable implements Serializable {

    public Grammar g;
    HashMap<String, HashMap<Integer, ParserAction>> data = new HashMap<String, HashMap<Integer, ParserAction>>();

    public LALRTable(Grammar g) {
        this.g = g;
    }

    public boolean isArray(String sym) {
        return g.getArrayNotations().contains(sym);
    }

    public Production getProduction(int id) {
        return g.getProduction(id);
    }

    public Symbol[] getProductionAlias(int id) {
        return g.getProductionAlias(id);
    }

    public void add(String sym, int status, ParserAction action) {
        if (!data.containsKey(sym)) {
            data.put(sym, new HashMap<Integer, ParserAction>());
        }
        HashMap<Integer, ParserAction> entry = data.get(sym);
        Integer i = status;
        if (entry.containsKey(i)) {
            if (!entry.get(i).eq(action)) {
                //try priori
                if (entry.get(i).action == ParserAction.REDUCE && action.action == ParserAction.SHIFT) {
                    //reduce-shift collision
                    throw new RuntimeException("reduce-shift collision symbol=" + sym + " status=" + status);
                }
                if (entry.get(i).action == ParserAction.REDUCE && action.action == ParserAction.REDUCE) {
                    //reduce-reduce collision
                    throw new RuntimeException("reduce-shift collision symbol=" + sym + " status=" + status);
                }

                throw new RuntimeException("sym=" + sym + " status=" + i + " already exsits");
            }
        } else {
            entry.put(i, action);
        }
    }

    public ParserAction get(String sym, int status) {
        if (!data.containsKey(sym)) {
            return null;
        }
        HashMap<Integer, ParserAction> entry = data.get(sym);
        Integer i = status;
        if (!entry.containsKey(i)) {
            return null;
        }
        return entry.get(i);
    }

    public ArrayList<String> getExpectation(int status) {
        ArrayList<String> expects = new ArrayList<String>();
        for (String sym : data.keySet()) {
            if (data.get(sym).containsKey(status)) {
                expects.add(sym);
            }
        }
        return expects;
    }
}
