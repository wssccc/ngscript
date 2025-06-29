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

import org.ngscript.value.MemRef;

/**
 * Scope-write opcodes: declarations and assignments against the active frame.
 *
 * @author wssccc
 */
public class OpScope {

    public static void set(Machine vm, String param, String param_extend) {
        ((Environment) vm.env.read()).data.put(param, new MemRef(vm.eax.read()));
    }

    public static void val(Machine vm, String param, String param_extend) {
        ((Environment) vm.env.read()).data.put(param, new MemRef(null, true));
    }

    public static void assign(Machine vm, String param, String param_extend) {
        Object[] objs = OpUtils.get2OpParam(vm);
        ((MemRef) objs[0]).write(objs[1]);
        vm.eax.write(objs[1]);
    }
}
