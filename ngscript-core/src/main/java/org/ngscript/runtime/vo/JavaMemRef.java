/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
@Slf4j
public class JavaMemRef extends VmMemRef {

    private Object obj;
    private Field field;

    public JavaMemRef(Object obj, Field field) {
        this.obj = obj;
        this.field = field;
    }

    @Override
    public Object read() {
        try {
            return field.get(obj);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            log.error("error read " + field.getName() + " of " + obj.getClass().getName(), ex);
        }
        return null;
    }

    @Override
    public void write(Object v) {
        try {
            field.set(obj, v);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            log.error("error write " + field.getName() + " of " + obj.getClass().getName(), ex);
        }
    }

}
