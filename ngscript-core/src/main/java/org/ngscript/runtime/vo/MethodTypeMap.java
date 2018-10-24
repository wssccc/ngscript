package org.ngscript.runtime.vo;

import org.ngscript.runtime.utils.TypeCheck;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodTypeMap {

    Map<String, Method> map = new HashMap<>();

    public static MethodTypeMap INSTANCE = new MethodTypeMap();

    private MethodTypeMap() {
    }

    public Method getProperMethod(String methodName, List<Method> methods, Class[] types) {
        if (methods.size() == 1) {
            return methods.get(0);
        } else {
            int typeHash = typeHash(types);
            String key = methodName + "#" + typeHash;
            if (map.containsKey(key)) {
                return map.get(key);
            } else {
                for (Method m : methods) {
                    if (TypeCheck.typeAcceptable(types, m.getParameterTypes())) {
                        map.put(key, m);
                        return m;
                    }
                }
            }
        }
        return null;
    }

    int typeHash(Class[] types) {
        int hash = 0;
        for (Class type : types) {
            hash = (hash >> 12 | hash << 20) ^ type.hashCode();
        }
        return hash;
    }
}
