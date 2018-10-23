/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

import org.ngscript.runtime.VirtualMachine;

/**
 * @author wssccc <wssccc@qq.com>
 */
public interface BuiltInFunction {

    void invoke(VirtualMachine vm, Object[] vars) throws Exception;
}
