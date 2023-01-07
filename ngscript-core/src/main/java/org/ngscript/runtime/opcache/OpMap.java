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
import org.ngscript.runtime.OpImpl;
import org.ngscript.runtime.VirtualMachine;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpMap {

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
        classPool.importPackage(VirtualMachine.class.getPackage().getName());
        Class clazz = OpImpl.class;
        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            Class[] cls = m.getParameterTypes();
            if (cls.length > 0 && cls[0] == VirtualMachine.class) {
                try {
                    map.put(m.getName(), toInstruction(m.getName(), "", ""));
                    map.put(m.getName() + "_pe", toInstruction(m.getName(), "_pe", "runtime.stack.push(runtime.eax.read());"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        map = Collections.unmodifiableMap(map);
    }

    private OpInvokable toInstruction(String inst, String appendInst, String appendCode) throws Exception {
        CtClass mCtc = classPool.makeClass(OpInvokable.class.getName() + StringUtils.capitalize(inst + appendInst));
        mCtc.addInterface(classPool.get(OpInvokable.class.getName()));
        mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
        mCtc.addMethod(CtNewMethod.make("public void invoke(VirtualMachine runtime, String param, String param_extend) throws VmRuntimeException { OpImpl." + inst + "(runtime,param,param_extend);" + appendCode + "}", mCtc));
        Class pc = mCtc.toClass();
        OpInvokable bytecodeProxy = (OpInvokable) pc.newInstance();
        return bytecodeProxy;
    }
}
