/*
 *  wssccc all rights reserved
 */
package parseroid.parser;

import java.util.ArrayList;
import java.util.Stack;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class AstNode {

    static final Stack<String> stc = new Stack<String>();

    static {
        stc.push("    ");
    }
    public Token token;
    public ArrayList<AstNode> contents;

    public AstNode(Token token) {
        this.token = token;
        this.contents = new ArrayList<AstNode>();
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
        return toString(0);
    }

    String toString(int nest) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stc.size() - 1; i++) {
            builder.append(stc.get(i));
        }

        builder.append("|-");
        builder.append(token.toString());

        builder.append("\n");

        for (int i = 0; i < contents.size(); i++) {
            if (i != contents.size() - 1) {
                stc.push("|    ");
            } else {
                stc.push("     ");
            }
            builder.append(contents.get(i).toString(nest + 1));
            stc.pop();
        }
        return builder.toString();
    }

}
