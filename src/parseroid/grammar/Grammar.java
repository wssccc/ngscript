/*
 *  wssccc all rights reserved
 */
package parseroid.grammar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public final class Grammar implements Serializable {

    private int id = 0;
    public String rootSymbol;
    public String oriRootSymbol;
    public final HashSet<String> arrayNotations;
    public final HashSet<String> filterNotations;
    public final HashMap<String, String> classNotations;
    private final HashMap<Integer, Production> productions;
    private final HashMap<Integer, Symbol[]> production_alias;

    private final HashMap<String, Symbol> symbols;
    //cached
    private final HashMap<Symbol, ArrayList<Production>> symProductions;
    //generate later
    private HashMap<Symbol, HashMap<String, Symbol>> firstSet;

    public Grammar() {
        filterNotations = new HashSet<String>();
        arrayNotations = new HashSet<String>();
        classNotations = new HashMap<String, String>();
        productions = new HashMap<Integer, Production>();
        production_alias = new HashMap<Integer, Symbol[]>();
        symProductions = new HashMap<Symbol, ArrayList<Production>>();
        //symbols
        symbols = new HashMap<String, Symbol>();
        //system defined symbols
        symbols.put(Symbol.NULL.identifier, Symbol.NULL);
        symbols.put(Symbol.EOF.identifier, Symbol.EOF);
        symbols.put(Symbol.ERROR.identifier, Symbol.ERROR);

    }

    public void checkProduction() {
        for (Symbol sym : symbols.values()) {
            if (sym.isTerminal == false) {
                if (getSymProductions(sym).isEmpty()) {
                    throw new RuntimeException(sym + " has no produtions");
                }
            }
        }
    }

    public int createProduction(String symName, Symbol[] produces) {
        Production production = new Production(createSymbol(symName, false), produces, id);
        productions.put(id, production);
        int thisid = id;
        ++id;
        return thisid;
    }

    public void createProductionAlias(int id, Symbol[] syms) {
        assert production_alias.containsKey(id) == false;
        production_alias.put(id, syms);
    }

    public Symbol createSymbol(String name, boolean ternimal) {
        if (symbols.containsKey(name)) {
            if (symbols.get(name).isTerminal == ternimal) {
                return symbols.get(name);
            } else {
                throw new RuntimeException("symbol " + name + " defined as terminal=" + symbols.get(name).isTerminal);
            }
        } else {
            Symbol s = Symbol.create(name, ternimal);
            symbols.put(name, s);
            return s;
        }
    }

    public Symbol getSymbol(String type) {
        return symbols.get(type);
    }

    public HashMap<String, Symbol> getFirstSet(Symbol sym) {
        if (firstSet == null) {
            genFirstSet();
        }
        return firstSet.get(sym);
    }

    public Production getProduction(int id) {
        Integer intObj = id;
        if (productions.containsKey(intObj)) {
            return productions.get(intObj);
        } else {
            throw new RuntimeException("production id=" + id + " is not defined");
        }
    }

    public Symbol[] getProductionAlias(int id) {
        return production_alias.get(id);
    }

    public ArrayList<Production> getSymProductions(Symbol sym) {
        assert sym.isTerminal == false;
        if (symProductions.containsKey(sym)) {
            return symProductions.get(sym);
        } else {
            ArrayList<Production> result = new ArrayList<Production>();
            for (Production pro : productions.values()) {
                if (pro.sym.identifier.equals(sym.identifier)) {
                    result.add(pro);
                }
            }
            symProductions.put(sym, result);
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
                    throw new RuntimeException("dunplicated root production");
                }
            }
        }
        if (p == null) {
            throw new RuntimeException("root symbol produces nothing");
        }
        return p;
    }

    public HashMap<String, Symbol> getFirstSet(Symbol[] seq) {
        HashMap<String, Symbol> result = new HashMap<String, Symbol>();
        for (int i = 0; i < seq.length; ++i) {
            HashMap<String, Symbol> onefirstset = getFirstSet(seq[i]);
            result.putAll(onefirstset);
            if (onefirstset.containsKey(Symbol.NULL.identifier)) {
                result.remove(Symbol.NULL.identifier);
                //continue
            } else {
                return result;
            }
        }
        throw new RuntimeException("NULL producer at the end of the production");
    }

    //inner
    void genFirstSet() {
        firstSet = new HashMap<Symbol, HashMap<String, Symbol>>();
        //add terminal symbols
        for (Production pro : productions.values()) {
            for (Symbol produce : pro.produces) {
                if (produce.isTerminal == true) {
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
                    HashMap<String, Symbol> onefirstset = getFirstSet(produce);
                    if (onefirstset == null) {
                        onefirstset = new HashMap<String, Symbol>();
                        firstSet.put(produce, onefirstset);
                    }
                    for (Symbol sym : onefirstset.values()) {
                        changed |= addToFirstSet(pro.sym, sym);
                    }
                    if (!onefirstset.containsKey(Symbol.NULL.identifier)) {
                        break;
                    }
                }
            }
        }
    }

    boolean addToFirstSet(Symbol vn, Symbol vt) {
        boolean changed = false;
        HashMap<String, Symbol> onefirstset;

        if (!firstSet.containsKey(vn)) {
            changed = true;
            onefirstset = new HashMap<String, Symbol>();
            firstSet.put(vn, onefirstset);
        } else {
            onefirstset = firstSet.get(vn);
        }

        if (!onefirstset.containsKey(vt.identifier)) {
            changed = true;
            onefirstset.put(vt.identifier, vt);
        }
        return changed;
    }

}
