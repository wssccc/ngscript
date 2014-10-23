/*
 *  wssccc all rights reserved
 */
package org.ngscript.parseroid.table;

import org.ngscript.parseroid.grammar.Grammar;
import org.ngscript.parseroid.grammar.Symbol;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class TableGenerator {

    public CanonicalCollection lalrCc;
    public LALRTable lalrTable;

    Grammar grammar;

    public TableGenerator(Grammar g) {
        this.grammar = g;
    }

    public LALRTable generate(boolean debug) {
        ItemSet initItemSet = new ItemSet();

        Symbol[] pros = new Symbol[]{grammar.createSymbol(grammar.rootSymbol, false)};
        if (grammar.rootSymbol == null) {
            throw new RuntimeException("root symbol not set");
        }
        grammar.oriRootSymbol = grammar.rootSymbol;
        grammar.rootSymbol = grammar.rootSymbol + "'";
        grammar.createProduction(grammar.rootSymbol, pros);
        Item initItem = new Item(grammar.getRootProduction(), 0);
        initItem.addLookahead(Symbol.EOF);
        initItemSet.items.add(initItem);

        lalrCc = new CanonicalCollection(grammar);
        lalrCc.getSameHeart(initItemSet);
        initItemSet.closure(grammar);
        lalrCc.buildCC();
        if (debug) {
            System.out.println(lalrCc);
        }
        lalrTable = lalrCc.buildTable();
        if (debug) {
            for (String sym : lalrTable.data.keySet()) {
                System.out.print("\t" + sym.substring(0, 2));
            }
            System.out.println("");
            for (int i = 0; i < lalrCc.cc.size(); ++i) {
                System.out.print(i + "\t");
                for (String sym : lalrTable.data.keySet()) {
                    ParserAction action = lalrTable.get(sym, i);
                    if (action != null) {
                        System.out.print(action);
                    } else {
                        System.out.print("");
                    }
                    System.out.print("\t");
                }
                System.out.println("");
            }
        }
        return lalrTable;
    }
}
