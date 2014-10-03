/*
 *  wssccc all rights reserved
 */
package ngscript.vm.structure;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class NativeMemref extends VmMemRef {

    Object obj;
    Field field;

    public NativeMemref(Object obj, Field field) {
        this.obj = obj;
        this.field = field;
    }

    @Override
    public Object read() {
        try {
            return field.get(obj);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(NativeMemref.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(NativeMemref.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void write(Object v) {
        try {
            field.set(obj, v);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(NativeMemref.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(NativeMemref.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
