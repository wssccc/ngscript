/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm.structure;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class NativeClosure {

    public Object caller;
    public ArrayList<Method> methods;

    public NativeClosure(Object caller, ArrayList<Method> methods) {
        this.caller = caller;
        this.methods = methods;
    }

    public NativeClosure(Object caller, Method method) {
        this.caller = caller;
        this.methods = new ArrayList<Method>();
        this.methods.add(method);
    }
}
