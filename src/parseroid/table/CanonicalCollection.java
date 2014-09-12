/*
 *  wssccc all rights reserved
 */
package parseroid.table;

import parseroid.grammar.Symbol;
import parseroid.grammar.Grammar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class CanonicalCollection {

    public ArrayList<ItemSet> cc = new ArrayList<ItemSet>();
    Grammar g;

    public CanonicalCollection(Grammar g) {
        this.g = g;
    }

    public void buildCC() {
        Set<ItemSet> expanded = new HashSet<ItemSet>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < cc.size(); ++i) {
                ItemSet itemSet = cc.get(i);
                if (!expanded.contains(itemSet)) {
                    expanded.add(itemSet);
                    changed = true;
                    //
                    ArrayList<Symbol> inputs = itemSet.getInputs();
                    for (Symbol sym : inputs) {
                        ItemSet newItemSet = itemSet.getGoto(sym);
                        newItemSet.closure(g);
                        ItemSet sameh = getSameHeart(newItemSet);
                        if (sameh.merge(newItemSet)) {
                            expanded.remove(sameh);
                        }
                        itemSet.go.put(sym, sameh);
                    }
                }
            }
        }
    }

    public LALRTable buildTable() {
        LALRTable table = new LALRTable(g);
        for (ItemSet itemSet : cc) {
            for (Item item : itemSet.items) {
                if (item.pos < item.production.produces.length) {
                    if (item.production.produces[item.pos].isTerminal == true) {
                        int j = itemSet.go.get(item.production.produces[item.pos]).id;
                        table.add(item.production.produces[item.pos].identifier, itemSet.id, ParserAction.shift(j));
                    }
                } else {
                    for (Symbol lhsym : item.lookahead.values()) {
                        if (lhsym.isTerminal) {
                            if (lhsym == Symbol.EOF && item.production.eq(g.getRootProduction())) {
                                table.add(lhsym.identifier, itemSet.id, ParserAction.accept());
                            } else {
                                table.add(lhsym.identifier, itemSet.id, ParserAction.reduce(item.production.id));
                            }
                        }
                    }
                }
            }
            for (Symbol sym : itemSet.go.keySet()) {
                if (sym.isTerminal == false) {
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
