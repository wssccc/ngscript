/*
 *  wssccc all rights reserved
 */
package parseroid.table;

import parseroid.grammar.Symbol;
import parseroid.grammar.Production;
import parseroid.grammar.Grammar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class ItemSet {

    int id;
    ArrayList<Item> items = new ArrayList<Item>();
    HashMap<Symbol, ItemSet> go = new HashMap<Symbol, ItemSet>();

    public ItemSet() {
        this.id = -1;
    }

    public ItemSet(int id) {
        this.id = id;
    }

    ArrayList<Symbol> getInputs() {
        ArrayList<Symbol> result = new ArrayList<Symbol>();
        for (Item item : items) {
            if (item.production.produces.length != item.pos) {
                if (!result.contains(item.production.produces[item.pos])) {
                    result.add(item.production.produces[item.pos]);
                }
            }
        }
        return result;
    }

    ItemSet getGoto(Symbol x) {
        ItemSet itemSet = new ItemSet();
        for (Item item : items) {
            if (item.production.produces.length != item.pos) {
                if (item.production.produces[item.pos].identifier.equals(x.identifier)) {
                    itemSet.getItem(item.production, item.pos + 1).addLookahead(item.lookahead);
                }
            }
        }
        return itemSet;
    }

    void closure(Grammar g) {
        Set<Item> expanded = new HashSet<Item>();
        boolean changed = true;
        while (changed) {
            changed = false;
            Item[] itemArray = new Item[items.size()];
            items.toArray(itemArray);
            for (int i = 0; i < itemArray.length; ++i) {
                Item item = itemArray[i];
                if (!expanded.contains(item)) {
                    expanded.add(item);
                    changed = true;
                    if (item.production.produces.length != item.pos) {
                        Symbol nextExpand = item.production.produces[item.pos];
                        if (nextExpand.isTerminal == false) {
                            String keys[] = new String[item.lookahead.keySet().size()];
                            item.lookahead.keySet().toArray(keys);
                            for (int k = 0; k < keys.length;) {
                                Symbol lh = item.lookahead.get(keys[k]);

                                Symbol[] suc = new Symbol[item.production.produces.length - item.pos];
                                for (int j = item.pos + 1; j < item.production.produces.length; j++) {
                                    suc[j - item.pos - 1] = item.production.produces[j];
                                }
                                suc[suc.length - 1] = lh;
                                HashMap<String, Symbol> firstset = g.getFirstSet(suc);
                                //
                                ArrayList<Production> pros = g.getSymProductions(nextExpand);
                                boolean lhchanged = false;
                                for (Production p : pros) {
                                    lhchanged |= (getItem(p, 0).addLookahead(firstset));
                                }
                                if (lhchanged) {
                                    k = 0;
                                } else {
                                    k++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public Item getItem(Production production, int pos) {
        for (Item i : items) {
            if (i.production.eq(production) && i.pos == pos) {
                return i;
            }
        }
        Item item = new Item(production, pos);
        items.add(item);
        return item;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Item item : items) {
            sb.append("  ");
            sb.append(item.toString());
            sb.append("\r\n");
        }
        return sb.toString();
    }

    boolean contains(Item item) {
        for (Item i : items) {
            if (i.eq(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean eq(ItemSet other) {
        if (this.items.size() != other.items.size()) {
            return false;
        }
        for (Item item : this.items) {
            if (!other.contains(item)) {
                return false;
            }
        }
        return true;
    }

    Item getHeart(Item item) {
        for (Item i : items) {
            if (i.eqHeart(item)) {
                return i;
            }
        }
        return null;
    }

    boolean eqHeart(ItemSet other) {
        if (this.items.size() != other.items.size()) {
            return false;
        }
        for (Item item : this.items) {
            if (other.getHeart(item) == null) {
                return false;
            }
        }
        return true;
    }

    boolean merge(ItemSet itemSet) {
        boolean changed = false;
        for (Item item : itemSet.items) {
            changed |= getHeart(item).addLookahead(item.lookahead);
        }
        return changed;
    }
}
