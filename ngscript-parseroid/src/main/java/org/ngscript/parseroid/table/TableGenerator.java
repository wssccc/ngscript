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

/**
 * @author wssccc
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

        Symbol[] pros = new Symbol[]{grammar.createSymbol(grammar.getRootSymbol(), false)};
        if (grammar.getRootSymbol() == null) {
            throw new RuntimeException("root symbol not set");
        }
        grammar.setRootSymbol(grammar.getRootSymbol() + "'");
        grammar.createProduction(grammar.getRootSymbol(), pros);
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
