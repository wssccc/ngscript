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

package org.ngscript.parseroid.parser;


import org.ngscript.parseroid.grammar.Grammar;
import org.ngscript.parseroid.grammar.Production;
import org.ngscript.parseroid.grammar.Symbol;
import org.ngscript.parseroid.table.LALRTable;
import org.ngscript.parseroid.table.ParserAction;
import org.ngscript.utils.FastStack;

import java.util.List;

/**
 * @author wssccc
 */
public abstract class LalrParser {

    LALRTable table;
    FastStack<Integer> stateStack = new FastStack<Integer>(32);
    FastStack<String> symbolStack = new FastStack<>(32);
    FastStack<AstNode> astStack = new FastStack<>(32);

    public LalrParser(LALRTable table) {
        this.table = table;
        initParser();
    }

    protected void initParser() {
        stateStack.clear();
        symbolStack.clear();
        astStack.clear();
        stateStack.add(0);
        symbolStack.add(Symbol.EOF.identifier);
    }

    public abstract void onResult(AstNode astNode);

    public boolean feed(Token[] tokens, boolean checkCompilePoint) throws ParserException {
        for (int tokenIndex = 0; tokenIndex < tokens.length; ) {
            Token token = tokens[tokenIndex];
            Integer state = stateStack.peek();
            ParserAction action = table.get(token.type, state);
            if (action == null) {
                //try NULL symbol
                action = table.get(Symbol.NULL.identifier, state);
                if (action == null) {
                    //error occured
                    initParser();
                    throw new ParserException("Parser exception while reading " + token.toString() + " \r\n" + table.getExpectation(state));
                } else {
                    //reject 1 symbol
                    token = new Token(Symbol.NULL.identifier, token.line);
                    --tokenIndex;
                }
            }
            switch (action.action) {
                case ParserAction.SHIFT:
                    stateStack.add(new Integer(action.param));
                    symbolStack.add(token.type);
                    astStack.add(new AstNode(token));
                    //consume
                    ++tokenIndex;
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
                        List<AstNode> sublist = astStack.last(n);

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
                        astStack.pop(n);
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
                        onResult(astStack.pop());
                        initParser();
                        return true;
                    } else {
                        throw new ParserException("Parser exception, stack not clear at ACCEPT state.");
                    }
                default:
            }
        }
        if (checkCompilePoint && isCompilable()) {
            return feed(new Token[]{new Token("EOF")}, true);
        }
        return false;
    }

    private boolean isCompilable() {
        Integer state = stateStack.peek();
        int len = 0;

        while (true) {
            ParserAction action = table.get(Symbol.EOF.identifier, state);
            if (action == null || action.action != ParserAction.REDUCE) {
                break;
            }

            Production prod = table.g.getProduction(action.param);
            len += prod.produces.length;
            int newState = stateStack.get(stateStack.size() - len - 1);
            ParserAction gotoAction = table.get(prod.sym.identifier, newState);
            if (gotoAction == null) {
                break;
            }
            state = gotoAction.param;
            if (len == astStack.size() && prod.sym.identifier.equals("statement")) {
                return true;
            }
            --len;
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

            for (int i = 0; i < node.contents.size(); ) {
                if (node.contents.get(i).token.isValidPos()) {
                    // node.token.col = node.contents.get(i).token.col;

                    if (node.token.line > node.contents.get(i).token.line || !node.token.isValidPos()) {
                        node.token.line = node.contents.get(i).token.line;
                        changed = true;
                    }

                }
                if (g.getFilterNotations().contains(node.contents.get(i).token.type)) {
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
                String equiv = g.getClassNotations().get(c.token.type);
                if (equiv != null) {
                    //process equiv classes
                    c.token.type = equiv;
                    changed = true;
                }
            }
            //only one child
            if (node.contents.size() == 1) {
                if (node.token.type.equals(node.contents.get(0).token.type)) {
                    //reduce duplicate nest , exp-> exp
                    node.token = node.contents.get(0).token;
                    node.contents = node.contents.get(0).contents;
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static void removeNULL(AstNode ast) {
        for (int i = 0; i < ast.contents.size(); ) {
            if (ast.contents.get(i).token.type.equals("NULL")) {
                ast.contents.remove(i);
            } else {
                ++i;
            }
        }
        for (AstNode astNode : ast.contents) {
            removeNULL(astNode);
        }
    }
}
