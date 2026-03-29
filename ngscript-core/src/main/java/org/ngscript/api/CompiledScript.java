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

package org.ngscript.api;

import java.util.List;

import org.ngscript.bytecode.Instruction;
import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.opcache.OpBinding;

/**
 * A compiled script backed by a {@link Machine}. Each {@link #execute()}
 * snapshots the machine state, runs the script in a fresh root scope on top
 * of the machine's baseline environment (builtins), then restores the
 * previous state, so executions are repeatable and do not leak state.
 *
 * @author wssccc
 */
public class CompiledScript implements Script {

    private final NgScriptEngine engine;
    private final OpBinding[] boundInstructions;
    private final String sourceName;

    CompiledScript(NgScriptEngine engine, List<Instruction> instructions, String sourceName) {
        this.engine = engine;
        this.boundInstructions = engine.vm.bindInstructions(instructions);
        this.sourceName = sourceName;
    }

    public String getSourceName() {
        return sourceName;
    }

    @Override
    public Object execute() throws NgScriptException {
        Machine machine = engine.vm;
        Machine.MachineState state = machine.captureState();
        machine.execNesting++;
        // isolate the exception-state stack: an exception inside this
        // replace-style execution (eval/CompiledScript) must propagate to
        // the *caller's* run loop as a wrapped value, not consume the
        // caller's try contexts via restore_machine_state (the machine
        // stack is reset for this execution, so restoring a caller context
        // would resume with an empty stack and later drop_machine_state
        // would pop an empty exception-state stack)
        org.ngscript.util.FastStack<org.ngscript.runtime.Context> savedMss = machine.isolateMss();
        try {
            machine.installInstructions(boundInstructions);
            machine.env.write(new NgsObject(machine.getInitialEnvironment()));
            // top-level var declarations bind to the global object (the
            // baseline environment carries functionScope), so a later
            // eval/execute sees them — ES global code declares vars on the
            // global object; let/const bindings stay on this script frame
            // and are discarded when the execution ends
            machine.resetForExecution();
            // top-level `this` is the global object (restored afterwards)
            machine.thisRef.write(Machine.globalThis);
            machine.run();
            return machine.eax.read();
        } finally {
            machine.restoreMss(savedMss);
            machine.execNesting--;
            machine.restoreState(state);
        }
    }
}
