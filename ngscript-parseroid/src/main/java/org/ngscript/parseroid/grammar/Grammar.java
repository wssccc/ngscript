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

package org.ngscript.parseroid.grammar;

import lombok.Data;

import java.io.Serializable;
import java.util.*;

/**
 * @author wssccc
 */
@Data
public final class Grammar implements Serializable {

    private int id = 0;
    private String rootSymbol;

    private final Set<String> arrayNotations = new HashSet<>();
    private final Set<String> filterNotations = new HashSet<>();
    private final Map<String, String> classNotations = new HashMap<>();
    private final Map<Integer, Production> productions = new HashMap<>();
    private final Map<Integer, Symbol[]> productionAlias = new HashMap<>();

    private final Map<String, Symbol> symbols = new HashMap<>();

    // calculated production map
    private final Map<Symbol, List<Production>> symbolProductions = new HashMap<>();
    private final Map<Symbol, Map<String, Symbol>> firstSet = new HashMap<>();

    public Grammar() {
        //system defined symbols
        symbols.put(Symbol.NULL.identifier, Symbol.NULL);
        symbols.put(Symbol.EOF.identifier, Symbol.EOF);
        symbols.put(Symbol.ERROR.identifier, Symbol.ERROR);
    }

    public void checkProduction() {
        for (Symbol sym : symbols.values()) {
            if (!sym.isTerminal) {
                if (getSymProductions(sym).isEmpty()) {
                    throw new RuntimeException(sym + " has no production");
                }
            }
        }
    }

    public int createProduction(String symName, Symbol[] produces) {
        Production production = new Production(createSymbol(symName, false), produces, id);
        productions.put(id, production);
        return id++;
    }

    public void createProductionAlias(int id, Symbol[] alias) {
        assert !productionAlias.containsKey(id);
        productionAlias.put(id, alias);
    }

    public Symbol createSymbol(String name, boolean terminal) {
        if (symbols.containsKey(name)) {
            if (symbols.get(name).isTerminal == terminal) {
                return symbols.get(name);
            } else {
                throw new RuntimeException("Symbol " + name + " defined as terminal=" + symbols.get(name).isTerminal);
            }
        } else {
            Symbol s = Symbol.create(name, terminal);
            symbols.put(name, s);
            return s;
        }
    }

    public Symbol getSymbol(String type) {
        return symbols.get(type);
    }

    public Map<String, Symbol> getFirstSet(Symbol sym) {
        if (firstSet.isEmpty()) {
            genFirstSet();
        }
        return firstSet.get(sym);
    }

    public Production getProduction(int id) {
        Integer intObj = id;
        if (productions.containsKey(intObj)) {
            return productions.get(intObj);
        } else {
            throw new RuntimeException("Production id=" + id + " is not defined");
        }
    }

    public Symbol[] getProductionAlias(int id) {
        return productionAlias.get(id);
    }

    public List<Production> getSymProductions(Symbol sym) {
        assert !sym.isTerminal;
        if (symbolProductions.containsKey(sym)) {
            return symbolProductions.get(sym);
        } else {
            List<Production> result = new ArrayList<>();
            for (Production pro : productions.values()) {
                if (pro.sym.identifier.equals(sym.identifier)) {
                    result.add(pro);
                }
            }
            symbolProductions.put(sym, result);
            return result;
        }
    }

    public Production getRootProduction() {
        if (rootSymbol == null) {
            throw new RuntimeException("root symbol not set");
        }
        Symbol rootSym = createSymbol(rootSymbol, false);
        Production p = null;
        for (Production pro : productions.values()) {
            if (pro.sym == rootSym) {
                if (p == null) {
                    p = pro;
                } else {
                    throw new RuntimeException("duplicated root production");
                }
            }
        }
        if (p == null) {
            throw new RuntimeException("root symbol produces nothing");
        }
        return p;
    }

    public Map<String, Symbol> getFirstSet(Symbol[] symbols) {
        Map<String, Symbol> result = new HashMap<>();
        for (Symbol symbol : symbols) {
            Map<String, Symbol> oneFirstSet = getFirstSet(symbol);
            result.putAll(oneFirstSet);
            if (oneFirstSet.containsKey(Symbol.NULL.identifier)) {
                result.remove(Symbol.NULL.identifier);
                //continue
            } else {
                return result;
            }
        }
        throw new RuntimeException("NULL producer at the end of the production");
    }


    private void genFirstSet() {
        //add terminal symbols
        for (Production pro : productions.values()) {
            for (Symbol produce : pro.produces) {
                if (produce.isTerminal) {
                    addToFirstSet(produce, produce);
                }
            }
        }
        addToFirstSet(Symbol.EOF, Symbol.EOF);
        addToFirstSet(Symbol.NULL, Symbol.NULL);
        //
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Production pro : productions.values()) {
                for (Symbol produce : pro.produces) {
                    Map<String, Symbol> oneFirstSet = getFirstSet(produce);
                    if (oneFirstSet == null) {
                        oneFirstSet = new HashMap<>();
                        firstSet.put(produce, oneFirstSet);
                    }
                    for (Symbol sym : oneFirstSet.values()) {
                        changed |= addToFirstSet(pro.sym, sym);
                    }
                    if (!oneFirstSet.containsKey(Symbol.NULL.identifier)) {
                        break;
                    }
                }
            }
        }
    }

    boolean addToFirstSet(Symbol vn, Symbol vt) {
        boolean changed = false;
        Map<String, Symbol> oneFirstSet;

        if (!firstSet.containsKey(vn)) {
            changed = true;
            oneFirstSet = new HashMap<>();
            firstSet.put(vn, oneFirstSet);
        } else {
            oneFirstSet = firstSet.get(vn);
        }

        if (!oneFirstSet.containsKey(vt.identifier)) {
            changed = true;
            oneFirstSet.put(vt.identifier, vt);
        }
        return changed;
    }

}
