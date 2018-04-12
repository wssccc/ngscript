/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm.structure;

import org.ngscript.vm.ScopeHash;
import org.ngscript.vm.WscVM;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class VmClosure {

    public String func_label;
    public ScopeHash closure_env;
    public WscVM vm;

    public VmClosure(String func_label, ScopeHash closure_env, WscVM vm) {
        this.func_label = func_label;
        this.closure_env = closure_env;
        this.vm = vm;
    }

}
