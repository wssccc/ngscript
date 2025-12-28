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
import org.ngscript.value.Undefined;

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

    /**
     * Pushes a {@code with (obj)} scope: the object's own properties (and
     * its [[Prototype]] chain) shadow the outer chain for variable lookups;
     * absent properties fall through to the outer environment. Writes to
     * present properties land on the object's own references.
     */
    public static void enter_with(Machine vm, String param, String param_extend) {
        NgsObject current = (NgsObject) vm.env.read();
        Object obj = vm.eax.read();
        vm.env.write(obj instanceof NgsObject
                ? new org.ngscript.runtime.NgsObject.WithFrame(current, (NgsObject) obj)
                : new NgsObject(current));
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

    /**
     * Resolves an assignment target reference without reading it: eax gets
     * the binding's MemRef, or the undefined marker when the name is
     * undeclared (the implicit global is only created by
     * {@link #assign_ref}, after the right-hand side has evaluated, so
     * {@code x = x} on an undeclared x throws from the RHS read and a
     * direct eval declaring x in the meantime does not capture the
     * already-resolved reference).
     */
    public static void resolve_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        try {
            vm.eax.write(vm.lookup(param));
        } catch (NgScriptRuntimeException e) {
            if (e.getMessage() != null && e.getMessage().endsWith(" is not declared")) {
                vm.eax.write(Undefined.value);
                return;
            }
            throw e;
        }
    }

    /**
     * Stores eax into the reference resolved by {@link #resolve_ref} (on
     * the stack top): a MemRef is written through; the undefined marker
     * creates an implicit global binding (sloppy-mode assignment).
     */
    public static void assign_ref(Machine vm, String param, String param_extend) {
        Object val = vm.eax.read();
        Object ref = vm.stack.peek(0);
        if (ref instanceof MemRef) {
            ((MemRef) ref).write(val);
        } else {
            ((NgsObject) vm.env.read()).rootFrame().putRef(param, new MemRef(val));
        }
        vm.eax.write(val);
    }

    /**
     * Assigns eax to the named binding, creating an implicit global when
     * the name is undeclared (sloppy-mode assignment). Unlike the
     * reference-load path this does not read the target first, so the
     * right-hand side is evaluated before any binding is created
     * ({@code x = x} on an undeclared x throws from the RHS read).
     */
    public static void assign_or_create(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object val = vm.eax.read();
        try {
            MemRef ref = vm.lookup(param);
            ref.write(val);
        } catch (NgScriptRuntimeException e) {
            if (e.getMessage() != null && e.getMessage().endsWith(" is not declared")) {
                ((NgsObject) vm.env.read()).rootFrame().putRef(param, new MemRef(val));
            } else {
                throw e;
            }
        }
        vm.eax.write(val);
    }
}
