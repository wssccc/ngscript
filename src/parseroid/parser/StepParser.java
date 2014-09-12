package parseroid.parser;

/*
 *  wssccc all rights reserved
 */
import parseroid.grammar.Grammar;
import parseroid.grammar.Symbol;
import parseroid.grammar.Production;
import java.util.List;
import parseroid.table.ParserAction;
import parseroid.table.LALRTable;
import java.util.Stack;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class StepParser {

    LALRTable table;
    public boolean compilable = false;
    AstNode compilableAst = null;
    Stack<Integer> stateStack = new Stack<Integer>();
    Stack<String> symbolStack = new Stack<String>();
    Stack<AstNode> astStack = new Stack<AstNode>();

    public StepParser(LALRTable table) {
        this.table = table;
        resetParser();
    }

    public AstNode getAst() {
        if (compilable) {
            this.reduce(compilableAst);
            LALRParser.removeNULL(compilableAst);
            return compilableAst;
        } else {
            return null;
        }
    }

    public final void resetParser() {
        stateStack.clear();
        symbolStack.clear();
        astStack.clear();
        stateStack.add(0);
        symbolStack.add(Symbol.EOF.identifier);
        compilable = false;

    }

    public void feed(Token token) throws ParserException {
        Token oriToken = token;
        if (compilable) {
            this.resetParser();
            throw new RuntimeException("compilable ast is not empty, you may forget to reset the parser");
        }
        int onHold = 0;

        Integer state = stateStack.peek();
        ParserAction action = table.get(token.type, state);

        if (action == null) {
            //try NULL sym
            action = table.get(Symbol.NULL.identifier, state);
            if (action == null) {
                throw new ParserException("Parser exception state=" + state + " feeding= " + token.toString() + " Expecting " + table.getExpectation(state) + "\nast stack:\n" + astStack.toString() + "\nsymbols:\n" + symbolStack.toString());
            }
            //null
            token = new Token(Symbol.NULL.identifier, token.line);
            --onHold;
        }
        switch (action.action) {
            case ParserAction.SHIFT:
                stateStack.add(new Integer(action.param));
                symbolStack.add(token.type);
                astStack.add(new AstNode(token));
                //forward
                ++onHold;
                break;
            case ParserAction.REDUCE:
                Production pro = table.getProduction(action.param);
                int n = pro.produces.length;
                AstNode node = new AstNode(new Token(pro.sym.identifier));

                //reduce ast
                Symbol[] pro_alias = table.getProductionAlias(action.param);
                if (pro_alias == null) {
                    //no alias
                    for (int i = 0; i < n; ++i) {
                        AstNode nd = astStack.get(astStack.size() - n + i);
                        //if (!nd.token.type.equals("NULL")) {
                        if (table.isArray(nd.token.type)) {
                            //as array
                            node.contents.addAll(nd.contents);
                        } else {
                            node.contents.add(nd);
                        }
                        //}

                    }
                    //pop ast stack
                    for (int i = 0; i < n; i++) {
                        astStack.pop();
                    }
                } else {
                    //reduce as alias defined
                    List<AstNode> sublist = astStack.subList(astStack.size() - n, astStack.size());

                    for (Symbol sym : pro_alias) {
                        AstNode nd = getAliasNode(sublist, sym);
                        if (nd != null) {
                            node.contents.add(nd);
                        } else {
                            //failed
                            throw new ParserException("alias symbol[" + sym + "] is not found in production " + pro);
                        }
                    }
                    //pop ast stack
                    while (!sublist.isEmpty()) {
                        sublist.remove(0);
                    }
                }

                for (int i = 0; i < n; ++i) {
                    stateStack.pop();
                    symbolStack.pop();
                }

                symbolStack.push(pro.sym.identifier);
                astStack.push(node);
                Integer newState = stateStack.peek();
                ParserAction gotoact = table.get(pro.sym.identifier, newState);
                stateStack.push(gotoact.param);
                //
                break;
            case ParserAction.ACCEPT:
                if (astStack.size() == 1) {
                    compilableAst = astStack.pop();
                    compilable = true;
                    return;
                } else {
                    throw new ParserException("Parser exception, stack not clear at ACCEPT state.");
                }
        }
        if (onHold < 1) {
            feed(oriToken); //refeed
        }
        if (compilable == false) {
            if (isCompilable()) {
                feed(new Token("EOF"));
            }
        }
    }

    private boolean isCompilable() {
        Integer state = stateStack.peek();
        //eof is trigger
        int len = 0;
        int pos = 0;
        while (true) {
            ParserAction action = table.get("EOF", state);
            if (action == null) {
                break;
            }
            if (action.action != ParserAction.REDUCE) {
                break;
            }

            Production prod = table.g.getProduction(action.param);

            len += prod.produces.length;
            int newState = stateStack.get(stateStack.size() - len - 1);

            ParserAction gotoact = table.get(prod.sym.identifier, newState);
            if (gotoact == null) {
                break;
            }
            state = gotoact.param;

            if (len == astStack.size()) {
                //so that reduced ast stack size = 1
                //hard code compile point
                if (prod.sym.identifier.equals("statement")) {
                    return true;
                }

            }
            len -= 1;
        }

        return false;
    }

    public static AstNode getAliasNode(List<AstNode> list, Symbol sym) {
        for (int j = 0; j < list.size(); j++) {
            if (sym.identifier.equals(list.get(j).token.type)) {
                //found
                AstNode ast = list.get(j);
                list.remove(j);
                return ast;
            }
        }
        if (sym.isTerminal) {
            return new AstNode(new Token(sym.identifier));
        }
        return null;
    }

    public void reduce(AstNode node) {
        while (_reduce(node, table.g)) {
            //do nothing
        }
    }

    boolean _reduce(AstNode node, Grammar g) {
        boolean changed = true;
        while (changed) {
            changed = false;

            for (int i = 0; i < node.contents.size();) {
                if (node.contents.get(i).token.isValidPos()) {
                    // node.token.col = node.contents.get(i).token.col;

                    if (node.token.line > node.contents.get(i).token.line || !node.token.isValidPos()) {
                        node.token.line = node.contents.get(i).token.line;
                        changed = true;
                    }

                }
                if (g.filterNotations.contains(node.contents.get(i).token.type)) {
                    //filter
                    node.contents.remove(i);
                    changed = true;
                } else if (node.contents.get(i).contents.isEmpty() && !g.getSymbol(node.contents.get(i).token.type).isTerminal) {
                    //remove non-terminal leaf
                    node.contents.remove(i);
                    changed = true;
                } else {
                    ++i;
                }
            }
            //
            for (AstNode c : node.contents) {
                //reduce child
                reduce(c);
                String equiv = g.classNotations.get(c.token.type);
                if (equiv != null) {
                    //process equiv classes
                    c.token.type = equiv;
                    changed = true;
                }
            }
            //only one child
            if (node.contents.size() == 1) {
                if (node.token.type.equals(node.contents.get(0).token.type)) {
                    //reduce dunplicate nest , exp-> exp 
                    node.token = node.contents.get(0).token;
                    node.contents = node.contents.get(0).contents;
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static void removeNULL(AstNode ast) {
        for (int i = 0; i < ast.contents.size();) {
            if (ast.contents.get(i).token.type.equals("NULL")) {
                ast.contents.remove(i);
            } else {
                ++i;
            }
        }
        for (AstNode astc : ast.contents) {
            removeNULL(astc);
        }
    }
}
