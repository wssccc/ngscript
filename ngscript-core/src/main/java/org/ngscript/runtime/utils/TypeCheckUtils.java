/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.runtime.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author wssccc
 */
public class TypeCheckUtils {

    static final HashMap<Class, HashSet<Class>> typeHierarchy = new HashMap<Class, HashSet<Class>>();

    static {
        HashSet<Class> intSet
                = new HashSet<Class>(Arrays.asList(Integer.class, Object.class, int.class, float.class, double.class));
        typeHierarchy.put(Integer.class, intSet);
        typeHierarchy.put(int.class, intSet);

        HashSet<Class> doubleSet = new HashSet<Class>(Arrays.asList(Double.class, Object.class, double.class));

        typeHierarchy.put(Double.class, doubleSet);
        typeHierarchy.put(double.class, doubleSet);
    }

    public static HashSet<Class> getSuperClasses(Class cls) {
        if (typeHierarchy.containsKey(cls)) {
            return typeHierarchy.get(cls);
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
            typeHierarchy.put(cls, classes);
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
        return (in == null || getSuperClasses(in).contains(def));
    }
}
