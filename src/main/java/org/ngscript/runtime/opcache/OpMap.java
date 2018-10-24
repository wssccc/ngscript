package org.ngscript.runtime.opcache;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import org.apache.commons.lang3.StringUtils;
import org.ngscript.runtime.Op;
import org.ngscript.runtime.InvokableInstruction;
import org.ngscript.runtime.VirtualMachine;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpMap {

    private Map<String, InvokableInstruction> map;
    private ClassPool classPool = new ClassPool(true);

    public static OpMap INSTANCE = new OpMap();

    private OpMap() {
        init();
    }

    public Map<String, InvokableInstruction> getMap() {
        return map;
    }

    public void init() {
        map = new HashMap<>();
        classPool.importPackage(VirtualMachine.class.getPackage().getName());
        Class clazz = Op.class;
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

    private InvokableInstruction toInstruction(String inst, String appendInst, String appendCode) throws Exception {
        CtClass mCtc = classPool.makeClass(InvokableInstruction.class.getName() + StringUtils.capitalize(inst + appendInst));
        mCtc.addInterface(classPool.get(InvokableInstruction.class.getName()));
        mCtc.addConstructor(CtNewConstructor.defaultConstructor(mCtc));
        mCtc.addMethod(CtNewMethod.make("public void invoke(VirtualMachine runtime, String param, String param_extend) throws VmRuntimeException { Op." + inst + "(runtime,param,param_extend);" + appendCode + "}", mCtc));
        Class pc = mCtc.toClass();
        InvokableInstruction bytecodeProxy = (InvokableInstruction) pc.newInstance();

        FileOutputStream fos = new FileOutputStream("target/" + pc.getSimpleName() + ".class");
        fos.write(mCtc.toBytecode());
        fos.close();
        //
        return bytecodeProxy;
    }
}
