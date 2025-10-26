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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * The ES {@code Promise} builtin with a microtask queue
 * ({@link Machine#enqueueMicrotask}). The executor runs synchronously;
 * {@code then} reactions are scheduled as microtasks and drained when the
 * outer {@link Machine#run()} loop completes. Includes the static
 * resolve/reject/all/race and the prototype then/catch/finally.
 *
 * @author wssccc
 */
public class PromiseBuiltins {

    /** The state of a PromiseValue. */
    public static final int PENDING = 0;
    public static final int FULFILLED = 1;
    public static final int REJECTED = 2;

    /** The shared Promise.prototype object. */
    private static NgsObject promisePrototype;

    public static synchronized NgsObject getPromisePrototype() {
        if (promisePrototype == null) {
            promisePrototype = new NgsObject(null);
            promisePrototype.setPrototype(Builtins.getObjectPrototype());
            registerProto(promisePrototype, "then", (vm, args) -> then(vm, args));
            registerProto(promisePrototype, "catch", (vm, args) -> {
                Object onRejected = args.length > 0 ? args[0] : null;
                then(vm, new Object[]{null, onRejected});
            });
            registerProto(promisePrototype, "finally", (vm, args) -> finally_(vm, args));
        }
        return promisePrototype;
    }

    private static void registerProto(NgsObject proto, String name, Invokable inv) {
        proto.set(name, inv);
    }

    /** One pending then/catch/finally reaction attached to a promise. */
    static class Reaction {
        Object onFulfilled;
        Object onRejected;
        Consumer<Object> resolve;
        Consumer<Object> reject;
        boolean propagateResult = true;
    }

    /**
     * A promise value: an NgsObject (so member access and instanceof work)
     * carrying the ES promise state machine.
     */
    public static class PromiseValue extends NgsObject {
        final Machine vm;
        public int state = PENDING;
        public Object value;
        List<Reaction> reactions;

        public PromiseValue(Machine vm) {
            super((NgsObject) vm.env.read());
            this.vm = vm;
            setPrototype(getPromisePrototype());
        }

        public void resolve(Object v) {
            if (state != PENDING) {
                return;
            }
            // thenable unwrapping: a settled promise hands its value on
            if (v instanceof PromiseValue) {
                PromiseValue other = (PromiseValue) v;
                if (other.state == PENDING) {
                    other.reactions = attachForward(other.reactions, this);
                    return;
                }
                v = other.value;
                if (other.state == REJECTED) {
                    reject(v);
                    return;
                }
            } else if (v instanceof NgsObject && v != this) {
                // generic thenable: call its then method
                Object then = OpUtils.getMember(vm, v, "then");
                if (then instanceof Invokable || then instanceof ScriptFunction) {
                    try {
                        PromiseValue p = this;
                        Invokable resolveFn = (m, a) ->
                                p.resolve(a.length > 0 ? a[0] : Undefined.value);
                        Invokable rejectFn = (m, a) ->
                                p.reject(a.length > 0 ? a[0] : Undefined.value);
                        vm.callFunctionSync(then, new Object[]{resolveFn, rejectFn}, v);
                    } catch (Throwable t) {
                        reject(t);
                    }
                    return;
                }
            }
            settle(FULFILLED, v);
        }

        /** Forwards a pending promise's settlement onto this one. */
        private List<Reaction> attachForward(List<Reaction> other, PromiseValue to) {
            List<Reaction> fwd = new ArrayList<>();
            if (other != null) {
                for (Reaction r : other) {
                    Reaction fr = new Reaction();
                    fr.onFulfilled = r.onFulfilled;
                    fr.onRejected = r.onRejected;
                    fr.resolve = r.resolve;
                    fr.reject = r.reject;
                    fwd.add(fr);
                }
            }
            Reaction self = new Reaction();
            self.onFulfilled = null;
            self.onRejected = null;
            self.resolve = to::resolve;
            self.reject = to::reject;
            fwd.add(self);
            return fwd;
        }

        public void reject(Object reason) {
            if (state != PENDING) {
                return;
            }
            settle(REJECTED, reason);
        }

        private void settle(int newState, Object v) {
            state = newState;
            value = v;
            if (reactions != null) {
                List<Reaction> rs = reactions;
                reactions = null;
                for (Reaction r : rs) {
                    vm.enqueueMicrotask(() -> perform(r));
                }
            }
        }

        private void perform(Reaction r) {
            try {
                Object cb = state == FULFILLED ? r.onFulfilled : r.onRejected;
                if (cb instanceof Invokable || cb instanceof ScriptFunction) {
                    Object result = vm.callFunctionSync(cb, new Object[]{value}, Undefined.value);
                    if (r.propagateResult) {
                        r.resolve.accept(result);
                    }
                } else if (state == FULFILLED) {
                    r.resolve.accept(value);
                } else {
                    r.reject.accept(value);
                }
            } catch (Throwable t) {
                r.reject.accept(t);
            }
        }

        /** Attaches reactions and returns a fresh derived promise. */
        PromiseValue attach(Object onFulfilled, Object onRejected, Consumer<Object> resolve,
                            Consumer<Object> reject) {
            return attach(onFulfilled, onRejected, resolve, reject, true);
        }

        /**
         * Attaches reactions; with {@code propagateResult} false the callback
         * return value is discarded (Promise.all/race observers settle the
         * output promise explicitly).
         */
        public PromiseValue attach(Object onFulfilled, Object onRejected, Consumer<Object> resolve,
                            Consumer<Object> reject, boolean propagateResult) {
            Reaction r = new Reaction();
            r.onFulfilled = onFulfilled;
            r.onRejected = onRejected;
            r.resolve = resolve;
            r.reject = reject;
            r.propagateResult = propagateResult;
            if (state == PENDING) {
                if (reactions == null) {
                    reactions = new ArrayList<>();
                }
                reactions.add(r);
            } else {
                vm.enqueueMicrotask(() -> perform(r));
            }
            return this;
        }
    }

    /** The Promise constructor (new Promise(executor)). */
    public static class Ctor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            Object executor = args.length > 0 ? args[0] : null;
            if (!(executor instanceof Invokable) && !(executor instanceof ScriptFunction)) {
                throw new NgScriptRuntimeException(vm, "Promise executor is not a function");
            }
            PromiseValue p = new PromiseValue(vm);
            Invokable resolveFn = (m, a) -> p.resolve(a.length > 0 ? a[0] : Undefined.value);
            Invokable rejectFn = (m, a) -> p.reject(a.length > 0 ? a[0] : Undefined.value);
            try {
                vm.callFunctionSync(executor, new Object[]{resolveFn, rejectFn}, Undefined.value);
            } catch (Throwable t) {
                p.reject(t);
            }
            vm.eax.write(p);
        }
    }

    //----------------------------------------------------------------------
    // prototype methods
    //----------------------------------------------------------------------

    private static void then(Machine vm, Object[] args) throws NgScriptRuntimeException {
        Object t = vm.thisRef.read();
        if (!(t instanceof PromiseValue)) {
            throw new NgScriptRuntimeException(vm, "Promise.prototype.then called on a non-promise");
        }
        PromiseValue p = (PromiseValue) t;
        Object onFulfilled = args.length > 0 && (args[0] instanceof Invokable
                || args[0] instanceof ScriptFunction) ? args[0] : null;
        Object onRejected = args.length > 1 && (args[1] instanceof Invokable
                || args[1] instanceof ScriptFunction) ? args[1] : null;
        PromiseValue derived = new PromiseValue(vm);
        p.attach(onFulfilled, onRejected, derived::resolve, derived::reject);
        vm.eax.write(derived);
    }

    private static void finally_(Machine vm, Object[] args) throws NgScriptRuntimeException {
        Object t = vm.thisRef.read();
        if (!(t instanceof PromiseValue)) {
            throw new NgScriptRuntimeException(vm, "Promise.prototype.finally called on a non-promise");
        }
        PromiseValue p = (PromiseValue) t;
        Object onFinally = args.length > 0 && (args[0] instanceof Invokable
                || args[0] instanceof ScriptFunction) ? args[0] : null;
        PromiseValue derived = new PromiseValue(vm);
        // onFinally runs for both outcomes; its value is ignored unless it throws
        p.attach(
                onFinally == null ? null : (Invokable) (m, a) -> {
                    try {
                        m.callFunctionSync(onFinally, new Object[0], Undefined.value);
                        derived.resolve(p.value);
                    } catch (Throwable ex) {
                        derived.reject(ex);
                    }
                },
                onFinally == null ? null : (Invokable) (m, a) -> {
                    try {
                        m.callFunctionSync(onFinally, new Object[0], Undefined.value);
                        derived.reject(p.value);
                    } catch (Throwable ex) {
                        derived.reject(ex);
                    }
                },
                derived::resolve, derived::reject);
        vm.eax.write(derived);
    }

    //----------------------------------------------------------------------
    // statics
    //----------------------------------------------------------------------

    public static Object resolveStatic(Machine vm, Object v) {
        if (v instanceof PromiseValue) {
            return v;
        }
        PromiseValue p = new PromiseValue(vm);
        p.resolve(v);
        return p;
    }

    public static Object rejectStatic(Machine vm, Object reason) {
        PromiseValue p = new PromiseValue(vm);
        p.reject(reason);
        return p;
    }

    public static Object allStatic(Machine vm, Object iterable) throws Exception {
        PromiseValue out = new PromiseValue(vm);
        List<Object> values = new ArrayList<>();
        int[] remaining = new int[]{0};
        java.util.Iterator<Object> it = OpUtils.toJavaIterator(vm, OpUtils.getIterator(vm, iterable));
        int i = 0;
        boolean[] failed = new boolean[]{false};
        while (it.hasNext()) {
            final int idx = i++;
            Object item = it.next();
            values.add(null);
            remaining[0]++;
            PromiseValue p = (PromiseValue) resolveStatic(vm, item);
            p.attach(
                    (Invokable) (m, a) -> {
                        values.set(idx, a[0]);
                        if (--remaining[0] == 0 && !failed[0]) {
                            NgsArray arr = new NgsArray(null);
                            arr.setPrototype(m.arrayPrototype);
                            for (int k = 0; k < values.size(); k++) {
                                arr.set("" + k, values.get(k));
                            }
                            out.resolve(arr);
                        }
                    },
                    (Invokable) (m, a) -> {
                        if (!failed[0]) {
                            failed[0] = true;
                            out.reject(a[0]);
                        }
                    },
                    out::resolve, out::reject, false);
        }
        if (remaining[0] == 0) {
            NgsArray arr = new NgsArray(null);
            arr.setPrototype(vm.arrayPrototype);
            out.resolve(arr);
        }
        return out;
    }

    public static Object raceStatic(Machine vm, Object iterable) throws Exception {
        PromiseValue out = new PromiseValue(vm);
        java.util.Iterator<Object> it = OpUtils.toJavaIterator(vm, OpUtils.getIterator(vm, iterable));
        while (it.hasNext()) {
            PromiseValue p = (PromiseValue) resolveStatic(vm, it.next());
            p.attach(
                    (Invokable) (m, a) -> out.resolve(a[0]),
                    (Invokable) (m, a) -> out.reject(a[0]),
                    out::resolve, out::reject, false);
        }
        return out;
    }

    /** Static lookup for member access on the Promise constructor. */
    public static org.ngscript.value.MemRef lookup(PromiseBuiltins.Ctor ctor, String member, Machine vm) {
        switch (member) {
            case "resolve":
                return new org.ngscript.value.MemRef((Invokable) (m, a) ->
                        m.eax.write(resolveStatic(m, a.length > 0 ? a[0] : Undefined.value)));
            case "reject":
                return new org.ngscript.value.MemRef((Invokable) (m, a) ->
                        m.eax.write(rejectStatic(m, a.length > 0 ? a[0] : Undefined.value)));
            case "all":
                return new org.ngscript.value.MemRef((Invokable) (m, a) -> {
                    try {
                        m.eax.write(allStatic(m, a.length > 0 ? a[0] : Undefined.value));
                    } catch (Exception e) {
                        throw new NgScriptRuntimeException(m, e.toString());
                    }
                });
            case "race":
                return new org.ngscript.value.MemRef((Invokable) (m, a) -> {
                    try {
                        m.eax.write(raceStatic(m, a.length > 0 ? a[0] : Undefined.value));
                    } catch (Exception e) {
                        throw new NgScriptRuntimeException(m, e.toString());
                    }
                });
            default:
                return null;
        }
    }
}
