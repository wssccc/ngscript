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
import org.ngscript.parseroid.grammar.Symbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wssccc
 */
public class CanonicalCollection {

    public ArrayList<ItemSet> cc = new ArrayList<ItemSet>();
    private Grammar grammar;

    public CanonicalCollection(Grammar grammar) {
        this.grammar = grammar;
    }

    /**
     * build CanonicalCollection for LALR(1)
     */
    public void buildCC() {
        //store expanded items
        Set<ItemSet> expanded = new HashSet<ItemSet>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < cc.size(); ++i) {
                //expand each itemset
                ItemSet itemSet = cc.get(i);
                if (!expanded.contains(itemSet)) {
                    expanded.add(itemSet);
                    changed = true;
                    //get valid inputs of the itemSet
                    ArrayList<Symbol> inputs = itemSet.getInputs();
                    //use each input to generate GOTO itemset
                    for (Symbol sym : inputs) {
                        ItemSet newItemSet = itemSet.getGoto(sym);
                        //create closure(expand inner)
                        newItemSet.closure(grammar);
                        //this is used to reduce the number of itemsets
                        ItemSet sameh = getSameHeart(newItemSet);
                        if (sameh.merge(newItemSet)) {
                            expanded.remove(sameh);
                        }
                        //link with go edge
                        itemSet.go.put(sym, sameh);
                    }
                }
            }
        }
    }

    /**
     * build action table
     *
     * @return
     */
    public LALRTable buildTable() {
        LALRTable table = new LALRTable(grammar);
        for (ItemSet itemSet : cc) {
            for (Item item : itemSet.items) {
                if (item.pos < item.production.produces.length) {
                    if (item.production.produces[item.pos].isTerminal) {
                        int j = itemSet.go.get(item.production.produces[item.pos]).id;
                        table.add(item.production.produces[item.pos].identifier, itemSet.id, ParserAction.shift(j));
                    }
                } else {
                    for (Symbol lhsym : item.lookahead.values()) {
                        if (lhsym.isTerminal) {
                            if (lhsym == Symbol.EOF && item.production.almostEquals(grammar.getRootProduction())) {
                                table.add(lhsym.identifier, itemSet.id, ParserAction.accept());
                            } else {
                                table.add(lhsym.identifier, itemSet.id, ParserAction.reduce(item.production.id));
                            }
                        }
                    }
                }
            }
            for (Symbol sym : itemSet.go.keySet()) {
                if (!sym.isTerminal) {
                    ItemSet goset = itemSet.go.get(sym);
                    table.add(sym.identifier, itemSet.id, ParserAction.go(goset.id));
                }
            }
        }

        return table;
    }

    ItemSet getSameHeart(ItemSet itemset) {
        for (ItemSet ccset : cc) {
            if (ccset.eqHeart(itemset)) {
                return ccset;
            }
        }
        itemset.id = cc.size();
        cc.add(itemset);
        return itemset;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cc.size(); i++) {
            ItemSet itemSet = cc.get(i);
            sb.append("I[");
            sb.append(itemSet.id);
            sb.append(']');
            sb.append("\r\n");
            sb.append(cc.get(i).toString());

        }
        return sb.toString();
    }

}
