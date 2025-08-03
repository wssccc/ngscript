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

import org.ngscript.value.LetBinding;
import org.ngscript.value.MemRef;

/**
 * Scope-write opcodes: declarations and assignments against the active frame.
 *
 * @author wssccc
 */
public class OpScope {

    /**
     * Binds a {@code var} declaration: the binding goes to the nearest
     * enclosing function scope, not to any block frame.
     */
    public static void set(Machine vm, String param, String param_extend) {
        ((NgsObject) vm.env.read()).setVar(param, vm.eax.read());
    }

    /**
     * Installs a let/const binding in the active (block) frame as an
     * uninitialized TDZ reference; the initializer (or undefined) must be
     * assigned through the normal assignment path afterwards.
     */
    public static void decl_let(Machine vm, String param, String param_extend) {
        boolean isConst = "1".equals(param_extend);
        ((NgsObject) vm.env.read()).putRef(param, new LetBinding(param, isConst));
    }

    /** Pushes a fresh block scope onto the environment chain. */
    public static void enter_block(Machine vm, String param, String param_extend) {
        NgsObject current = (NgsObject) vm.env.read();
        vm.env.write(new NgsObject(current));
    }

    /** Pops the active block scope off the environment chain. */
    public static void leave_block(Machine vm, String param, String param_extend) {
        vm.env.write(((NgsObject) vm.env.read()).getParent());
    }

    public static void val(Machine vm, String param, String param_extend) {
        ((NgsObject) vm.env.read()).define(param);
    }

    public static void assign(Machine vm, String param, String param_extend) {
        Object[] objs = OpUtils.get2OpParam(vm);
        ((MemRef) objs[0]).write(objs[1]);
        vm.eax.write(objs[1]);
    }
}
