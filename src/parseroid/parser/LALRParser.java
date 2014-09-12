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
public class LALRParser {

    LALRTable table;
    Stack<Integer> stateStack = new Stack<Integer>();
    Stack<String> symbolStack = new Stack<String>();
    Stack<AstNode> astStack = new Stack<AstNode>();

    public LALRParser(LALRTable table) {
        this.table = table;
    }

    void initParser() {
        stateStack.clear();
        symbolStack.clear();
        astStack.clear();
        stateStack.add(0);
        symbolStack.add(Symbol.EOF.identifier);
    }

    public AstNode parse(TokenStream tokenStream) throws ParserException {
        initParser();
        while (true) {
            Token token = tokenStream.peek();
            Integer state = stateStack.peek();
            ParserAction action = table.get(token.type, state);

            if (action == null) {
                //try NULL sym
                action = table.get(Symbol.NULL.identifier, state);
                if (action == null) {
                    //error occured
                    String origErrorMsg = "Expecting " + table.getExpectation(state);
                    Token original_token = token;
                    //try pop stack, until error can shift
                    while (!stateStack.empty()) {
                        state = stateStack.peek();
                        action = table.get(Symbol.ERROR.identifier, state);
                        if (action != null) {
                            if (action.action == 's') {
                                break;
                            }
                        }
                        stateStack.pop();
                        symbolStack.pop();
                        if (!astStack.isEmpty()) {
                            astStack.pop();
                        }
                    }
                    //if still has no action, raise error
                    if (action == null) {
                        throw new ParserException("Parser exception at error recovery phase 1, no near shift action found. " + original_token.toString() + " [state=" + state + "]\r\n" + origErrorMsg);
                    }
                    //shift error in
                    stateStack.add(action.param);
                    state = stateStack.peek();
                    symbolStack.add(Symbol.ERROR.identifier);
                    astStack.add(new AstNode(new Token("ERROR", token.line)));
                    //abandon input symbol, until a lh symbol maps a non-error action
                    while (tokenStream.peek() != null) {
                        token = tokenStream.peek();
                        action = table.get(token.type, state);
                        if (action != null) {
                            break;
                        } else {
                            //try null
                            action = table.get(Symbol.NULL.identifier, state);
                            if (action != null) {
                                token = new Token(Symbol.NULL.identifier, token.line);
                                tokenStream.setOnHold();
                                break;
                            }
                        }
                        tokenStream.forward();
                    }
                    if (action == null) {
                        throw new ParserException("Parser exception at error recovery phase 2, no proper error recovery production found. " + original_token.toString() + " \r\n" + origErrorMsg);
                    }
                    //
                } else {
                    //null
                    token = new Token(Symbol.NULL.identifier, token.line);
                    tokenStream.setOnHold();
                }
            }
            switch (action.action) {
                case ParserAction.SHIFT:
                    stateStack.add(new Integer(action.param));
                    symbolStack.add(token.type);
                    astStack.add(new AstNode(token));
                    tokenStream.forward();
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
                    break;
                case ParserAction.ACCEPT:
                    if (astStack.size() == 1) {
                        return astStack.pop();

                    } else {
                        throw new ParserException("Parser exception, stack not clear at ACCEPT state.");
                    }
            }
        }
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