/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import org.ngscript.parseroid.parser.AstNode;
import org.ngscript.parseroid.parser.LalrParser;
import org.ngscript.parseroid.parser.ParserException;
import org.ngscript.parseroid.parser.Token;

import java.util.List;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class NgLalrParser extends LalrParser {

    public AstNode root = new AstNode(new Token("program"));

    public NgLalrParser() {
        super(ParserLoader.INSTANCE.getTable());
    }

    public AstNode parse(Token[] tokens) throws ParserException {
        initParser();
        feed(tokens);
        return root;
    }

    @Override
    public void onResult(AstNode astNode) {
        reduce(astNode);
        System.out.println("caca " + astNode);
        root.contents.add(astNode);
    }
}
