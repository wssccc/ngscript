/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import org.ngscript.Configuration;
import org.ngscript.parseroid.parser.AstNode;
import org.ngscript.parseroid.parser.LalrParser;
import org.ngscript.parseroid.parser.ParserException;
import org.ngscript.parseroid.parser.Token;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class NgLalrParser extends LalrParser {

    Configuration configuration;

    public AstNode result = new AstNode(new Token("program"));

    public NgLalrParser(Configuration configuration) {
        super(ParserLoader.INSTANCE.getTable());
        this.configuration = configuration;
    }

    public boolean feed(Token[] tokens) throws ParserException {
        result.contents.clear();
        return feed(tokens, configuration.isInteractive());
    }

    public AstNode getResult() {
        return result;
    }

    @Override
    public void onResult(AstNode astNode) {
        reduce(astNode);
        result.contents.add(astNode);
    }
}
