/*
 *  wssccc all rights reserved
 */
package ngscript.vm.structure;

import java.util.LinkedList;
import ngscript.vm.WscVM;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public abstract class BuiltinClosure {

    public abstract void invoke(WscVM vm, LinkedList<Object> vars) throws Exception;
}
