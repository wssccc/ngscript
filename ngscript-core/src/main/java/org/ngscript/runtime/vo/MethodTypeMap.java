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

package org.ngscript.runtime.vo;

import org.ngscript.runtime.utils.TypeCheckUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
                    if (Modifier.isPublic(m.getDeclaringClass().getModifiers()) && Modifier.isPublic(m.getModifiers())) {
                        if (TypeCheckUtils.typeAcceptable(types, m.getParameterTypes())) {
                            map.put(key, m);
                            return m;
                        }
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
