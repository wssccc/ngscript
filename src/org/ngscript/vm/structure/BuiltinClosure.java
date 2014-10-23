/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm.structure;

import java.util.LinkedList;
import org.ngscript.vm.WscVM;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public abstract class BuiltinClosure {

    public abstract void invoke(WscVM vm, LinkedList<Object> vars) throws Exception;
}
