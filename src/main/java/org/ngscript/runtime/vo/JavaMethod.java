/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class JavaMethod {

    public Object caller;
    public ArrayList<Method> methods;

    public JavaMethod(Object caller, ArrayList<Method> methods) {
        this.caller = caller;
        this.methods = methods;
    }

    public JavaMethod(Object caller, Method method) {
        this.caller = caller;
        this.methods = new ArrayList<>();
        this.methods.add(method);
    }
}
