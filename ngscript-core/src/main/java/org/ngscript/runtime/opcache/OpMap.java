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

package org.ngscript.runtime.opcache;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import org.apache.commons.lang3.StringUtils;
import org.ngscript.runtime.Machine;
import org.ngscript.runtime.OpArithmetic;
import org.ngscript.runtime.OpArray;
import org.ngscript.runtime.OpCall;
import org.ngscript.runtime.OpConstant;
import org.ngscript.runtime.OpControlFlow;
import org.ngscript.runtime.OpMove;
import org.ngscript.runtime.OpObject;
import org.ngscript.runtime.OpScope;
import org.ngscript.runtime.OpStack;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OpMap {

    /**
     * Host classes scanned for opcode methods. Method names must be unique
     * across all host classes: generated proxy class names derive from them.
     */
    private static final Class<?>[] HOST_CLASSES = {
            OpArithmetic.class,
            OpObject.class,
            OpArray.class,
            OpControlFlow.class,
            OpCall.class,
            OpMove.class,
            OpStack.class,
            OpConstant.class,
            OpScope.class,
    };

    private Map<String, OpInvokable> map;
    private ClassPool classPool = new ClassPool(true);

    public static OpMap INSTANCE = new OpMap();

    private OpMap() {
        init();
    }

    public Map<String, OpInvokable> getMap() {
        return map;
    }

    public void init() {
        map = new HashMap<>();
        classPool.importPackage(Machine.class.getPackage().getName());
        Set<String> seen = new HashSet<>();
        for (Class<?> host : HOST_CLASSES) {
            Method[] methods = host.getMethods();
            for (Method m : methods) {
                Class[] cls = m.getParameterTypes();
                if (cls.length > 0 && cls[0] == Machine.class) {
                    String name = m.getName();
                    if (!seen.add(name)) {
                        throw new IllegalStateException(
                                "duplicate opcode method '" + name + "' in " + host.getName());
                    }
                    try {
                        map.put(name, toInstruction(host, name, "", ""));
                        map.put(name + "_pe", toInstruction(host, name, "_pe",
                                "runtime.stack.push(runtime.eax.read());"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        map = Collections.unmodifiableMap(map);
    }

    private OpInvokable toInstruction(Class<?> host, String inst, String appendInst, String appendCode) throws Exception {
        CtClass mCtc = classPool.makeClass(OpInvokable.class.getName() + StringUtils.capitalize(inst + appendInst));
        mCtc.addInterface(classPool.get(OpInvokable.class.getName()));
        mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
        // generated sources use fully qualified names so host classes can live
        // in any package without extra importPackage declarations
        mCtc.addMethod(CtNewMethod.make("public void invoke(org.ngscript.runtime.Machine runtime, String param, String param_extend) throws org.ngscript.runtime.NgScriptRuntimeException { " + host.getName() + "." + inst + "(runtime,param,param_extend);" + appendCode + "}", mCtc));
        Class pc = mCtc.toClass();
        OpInvokable bytecodeProxy = (OpInvokable) pc.newInstance();
        return bytecodeProxy;
    }
}
