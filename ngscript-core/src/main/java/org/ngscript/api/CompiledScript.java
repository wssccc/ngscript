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
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.Machine;
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
        try {
            machine.installInstructions(boundInstructions);
            machine.env.write(new NgsObject(machine.getInitialEnvironment()));
            machine.resetForExecution();
            machine.run();
            return machine.eax.read();
        } finally {
            machine.restoreState(state);
        }
    }
}
