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

package org.ngscript.builtins;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;

/**
 * The ES {@code Function} constructor: {@code Function(p1, p2, ..., body)}
 * compiles the body into a new script function (via the engine's eval
 * hook). Property access (Function.prototype.call/apply/bind) is handled by
 * {@link org.ngscript.runtime.OpObject#functionMember} on ScriptFunction
 * values; the constructor value itself is this Invokable.
 *
 * @author wssccc
 */
public class FunctionCtor implements Invokable {

    /** Shared instance: the ES Function constructor object itself. */
    public static final FunctionCtor INSTANCE = new FunctionCtor();

    @Override
    public void invoke(Machine vm, Object[] args) {
        String body = args.length > 0 ? OpUtils.toStringValue(args[args.length - 1]) : "";
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < args.length - 1; i++) {
            if (i > 0) {
                params.append(", ");
            }
            params.append(OpUtils.toStringValue(args[i]));
        }
        String src = "(function anonymous(" + params + ") { " + body + " })";
        if (Machine.evalHook == null) {
            throw new NgScriptRuntimeException(vm, "Function constructor requires an engine eval hook");
        }
        // the Function constructor runs in the global scope, not the
        // caller's scope (null caller env)
        Object f = Machine.evalHook.apply(src, null);
        vm.eax.write(f);
    }
}
