/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class VmMemRef {

    private Object value;

    public VmMemRef() {
        this.value = null;
    }

    public VmMemRef(Object obj_v) {
        this.value = obj_v;
    }

    public void write(Object v) {
        this.value = v;
    }

    public Object read() {
        return this.value;
    }

    @Override
    public String toString() {
        return "VmMemRef{" + "value=" + value + '}';
    }

}
