/*
 *  wssccc all rights reserved
 */
package ngscript.vm.strcuture;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class VmMemRef {

    private Object value;
    private int type;

    public VmMemRef() {
        this.value = null;
    }

    public VmMemRef(Object obj_v, int type) {
        this.type = type;
        this.value = obj_v;
    }

    public VmMemRef(Object obj_v) {
        this.type = 0;
        this.value = obj_v;
    }

    public void write(Object v) {
        this.value = v;
    }

    public Object read() {
        return this.value;
    }

}
