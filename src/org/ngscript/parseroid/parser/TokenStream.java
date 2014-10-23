/*
 *  wssccc all rights reserved
 */
package org.ngscript.parseroid.parser;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public abstract class TokenStream {

    private Token buf = null;
    private boolean onhold = false;

    public Token peek() {
        if (buf == null) {
            buf = next();
        }
        return buf;
    }

    public void forward() {
        if (onhold) {
            onhold = false;
        } else {
            buf = null;
        }
    }

    public void setOnHold() {
        onhold = true;
    }

    public abstract Token next();
}
