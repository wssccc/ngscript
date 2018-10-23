/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

/**
 *
 * @author wssccc <wssccc@qq.com>
 * @param <T>
 */
public class VmMemRef<T> {

    private T value;

    public VmMemRef() {
        this.value = null;
    }

    public VmMemRef(T obj_v) {
        this.value = obj_v;
    }

    public void write(T v) {
        this.value = v;
    }

    public T read() {
        return this.value;
    }

    @Override
    public String toString() {
        return "VmMemRef{" + "value=" + value + '}';
    }

}
