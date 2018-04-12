/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm;

import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class TypeCheck {

    //cached type hierarchy
    static final HashMap<Class, HashSet<Class>> supers = new HashMap<Class, HashSet<Class>>();

    static {
        HashSet<Class> int_set = new HashSet<Class>();
        int_set.add(Integer.class);
        int_set.add(Object.class);
        int_set.add(int.class);
        int_set.add(float.class);
        int_set.add(double.class);
        supers.put(Integer.class, int_set);
        supers.put(int.class, int_set);

        HashSet<Class> db_set = new HashSet<Class>();
        db_set.add(Double.class);
        db_set.add(Object.class);
        db_set.add(double.class);
        //db_set.add(float.class);

        supers.put(Double.class, db_set);
        supers.put(double.class, db_set);
    }

    public static HashSet<Class> getCastableClasses(Class cls) {
        if (supers.containsKey(cls)) {
            return supers.get(cls);
        } else {
            //collect super classes
            HashSet<Class> classes = new HashSet<Class>();
            Class cursor = cls;
            classes.add(cls);
            while (true) {
                cursor = cursor.getSuperclass();
                if (cursor != null) {
                    classes.add(cursor);
                } else {
                    break;
                }
            }
            supers.put(cls, classes);
            return classes;
        }
    }

    public static boolean typeAcceptable(Class[] in, Class[] def) {
        if (def.length > 0 && def[def.length - 1].isArray()) {
            //multi
            if (_typeAcceptable(in, def, def.length - 1)) {
                Class defLastType = def[def.length - 1].getComponentType();
                //check def.length-1 to in.length-1
                for (int i = def.length - 1; i < in.length; i++) {
                    if (!_typeCheck(in[i], defLastType)) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        } else {
            if (in.length != def.length) {
                return false;
            }
            return _typeAcceptable(in, def, def.length);
        }
    }

    static boolean _typeAcceptable(Class[] in, Class[] def, int n) {
        for (int i = 0; i < n; i++) {
            if (!_typeCheck(in[i], def[i])) {
                return false;
            }
        }
        return true;
    }

    static boolean _typeCheck(Class in, Class def) {
        //null is acceptable for any Object type
        return (in == null || getCastableClasses(in).contains(def));
    }
}
