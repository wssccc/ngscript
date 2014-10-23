/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm.cpu;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import org.ngscript.vm.VmCpu;
import org.ngscript.vm.WscVM;

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
        FileWriter fw = new FileWriter("src/org/ngscript/vm/cpu/AutoCreatedCpuDispatcher.java");
        fw.write("/* DO NOT WRITE ANYTHING MANUALLY */package " + DispatcherGenerator.class.getPackage().getName() + ";");
        fw.write("import org.ngscript.common.Instruction;");
        fw.write("import org.ngscript.vm.*;");
        fw.write("public class AutoCreatedCpuDispatcher {");
        fw.write("public static boolean dispatch(Instruction instruction, WscVM vm) throws WscVMException, Exception{");
        for (Method m : methods) {
            Class[] cls = m.getParameterTypes();
            if (cls.length > 0 && cls[0] == WscVM.class) {
                String name = m.getName();
                System.out.println("generating " + name);
                fw.write("if (instruction.op.equals(\"" + name + "\")) {");
                fw.write("VmCpu." + name + "(vm, instruction.param, instruction.param_extend);");
                fw.write("return true;}");
            }
        }

        fw.write("return false;}}");
        fw.close();
        System.out.println("done!");
    }
}
