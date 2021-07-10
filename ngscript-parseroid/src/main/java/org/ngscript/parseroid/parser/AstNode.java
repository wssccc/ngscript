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

import java.util.ArrayList;

/**
 * @author wssccc
 */
public class AstNode {

    public Token token;
    public ArrayList<AstNode> contents;

    public AstNode(Token token) {
        this.token = token;
        this.contents = new ArrayList<>();
    }

    public AstNode(Token token, ArrayList<AstNode> children) {
        this.token = token;
        this.contents = children;
    }

    public AstNode getNode(String type) {
        for (AstNode content : contents) {
            if (content.token.type.equals(type)) {
                return content;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return toString(0, "", "    ");
    }

    String toString(int nest, String margin, String subMargin) {
        StringBuilder builder = new StringBuilder(margin);
        builder.append("|-");
        builder.append(token.toString());
        builder.append("\n");
        for (int i = 0; i < contents.size(); i++) {
            if (i != contents.size() - 1) {
                builder.append(contents.get(i).toString(nest + 1, subMargin, subMargin + "|    "));
            } else {
                builder.append(contents.get(i).toString(nest + 1, subMargin, subMargin + "     "));
            }
        }
        return builder.toString();
    }

}
