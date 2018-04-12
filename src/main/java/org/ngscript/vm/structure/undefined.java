/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm.structure;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class undefined {

    private undefined() {
    }

    @Override
    public String toString() {
        return "undefined";
    }

    public static final undefined value = new undefined();
}
