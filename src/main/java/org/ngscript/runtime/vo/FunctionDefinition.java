/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

import org.ngscript.runtime.ScopeHash;
import org.ngscript.runtime.VirtualMachine;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class FunctionDefinition {

    public String functionLable;
    public ScopeHash closure_env;
    public VirtualMachine vm;

    public FunctionDefinition(String functionLable, ScopeHash closure_env, VirtualMachine vm) {
        this.functionLable = functionLable;
        this.closure_env = closure_env;
        this.vm = vm;
    }

}
