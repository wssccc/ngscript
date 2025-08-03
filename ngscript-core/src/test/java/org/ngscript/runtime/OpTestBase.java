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

package org.ngscript.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.ngscript.bytecode.Instruction;

/**
 * Base class for instruction-level opcode tests: builds a machine and runs
 * single instructions followed by {@code halt}.
 *
 * @author wssccc
 */
public abstract class OpTestBase {

    protected Machine vm;

    @Before
    public void setUp() {
        vm = new Machine(new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()));
    }

    /** Runs a single opcode with the given parameters, followed by halt. */
    protected void runOp(String op, String param, String paramExt) {
        runOps(new Instruction(op, param, paramExt), new Instruction("halt", "", ""));
    }

    /** Runs the given instruction sequence; the last one should end execution. */
    protected void runOps(Instruction... ins) {
        runOps(null, ins);
    }

    /**
     * Runs the given instruction sequence, invoking {@code preRun} after the
     * machine has been reset but before execution starts (e.g. to seed the
     * call stack).
     */
    protected void runOps(Runnable preRun, Instruction... ins) {
        List<Instruction> list = new ArrayList<>();
        for (Instruction i : ins) {
            list.add(i);
        }
        vm.installInstructions(vm.bindInstructions(list));
        NgsObject frame = new NgsObject(vm.getInitialEnvironment());
        // script/function frames are function scopes for var declarations
        frame.functionScope = true;
        vm.env.write(frame);
        vm.resetForExecution();
        if (preRun != null) {
            preRun.run();
        }
        vm.run();
    }

    /** Pushes the left operand (as compiled code would) and sets eax to the right operand. */
    protected void pushBinaryOperands(Object left, Object right) {
        vm.eax.write(left);
        vm.stack.push(left);
        vm.eax.write(right);
    }
}
