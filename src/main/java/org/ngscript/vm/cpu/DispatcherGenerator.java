/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm.cpu;

import org.ngscript.vm.VmCpu;
import org.ngscript.vm.WscVM;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class DispatcherGenerator {

    /**
     * Generate AutoCreatedCpuDispatcher class
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Class clazz = VmCpu.class;
        Method[] methods = clazz.getMethods();
        FileWriter fw = new FileWriter("ngscript/src/main/java/org/ngscript/vm/cpu/AutoCreatedCpuDispatcher.java");
        fw.write("/* DO NOT WRITE ANYTHING MANUALLY */package " + DispatcherGenerator.class.getPackage().getName() + ";");
        fw.write("import org.ngscript.common.Instruction;");
        fw.write("import org.ngscript.vm.*;");
        fw.write("public class AutoCreatedCpuDispatcher {");
        fw.write("public static boolean dispatch(Instruction instruction, WscVM vm) throws WscVMException, Exception{");
        fw.write("switch (instruction.op){");
        for (Method m : methods) {
            Class[] cls = m.getParameterTypes();
            if (cls.length > 0 && cls[0] == WscVM.class) {
                String name = m.getName();
                System.out.println("generating " + name);
                fw.write("case \""+name+"\":");
                fw.write("VmCpu." + name + "(vm, instruction.param, instruction.paramExtended);");
                fw.write("return true;");
            }
        }

        fw.write("}");
        fw.write("return false;}}");
        fw.close();
        System.out.println("done!");
    }
}
