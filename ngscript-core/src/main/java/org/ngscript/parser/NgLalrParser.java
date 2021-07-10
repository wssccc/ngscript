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

package org.ngscript.parser;

import org.ngscript.Configuration;
import org.ngscript.parseroid.parser.AstNode;
import org.ngscript.parseroid.parser.LalrParser;
import org.ngscript.parseroid.parser.ParserException;
import org.ngscript.parseroid.parser.Token;

/**
 * @author wssccc
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
