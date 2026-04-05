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
import org.ngscript.value.Undefined;

/**
 * An ES {@code Proxy} value: an NgsObject that forwards member access
 * (and, from the call paths, invocation) to a handler object. The engine
 * does not model full property descriptors, so only the traps that fit
 * the lightweight object model are honoured: get, set, apply, construct,
 * has and deleteProperty. Missing traps fall back to the target directly,
 * per the ES spec.
 *
 * @author wssccc
 */
public class ProxyValue extends NgsObject {

    /** The wrapped target object (any value). */
    public final Object target;
    /** The handler object; traps are read from it on demand. */
    public final Object handler;
    /** Set by {@code Proxy.revocable()}; access after revocation throws. */
    public boolean revoked;
    /** Whether the target is callable (affects the call paths). */
    public final boolean callable;

    public ProxyValue(Object target, Object handler, boolean callable) {
        super(null);
        this.target = target;
        this.handler = handler;
        this.callable = callable;
        if (target instanceof NgsObject) {
            setPrototype(((NgsObject) target).getPrototype());
        }
    }

    public void checkRevokedPublic(Machine vm) {
        checkRevoked(vm);
    }

    public Object getTrapPublic(Machine vm, String name) {
        return getTrap(vm, name);
    }

    private void checkRevoked(Machine vm) {
        if (revoked) {
            throw new NgScriptRuntimeException(vm, "Cannot perform operation on a revoked Proxy");
        }
    }

    /** Reads the trap from the handler (returns null when absent). */
    private Object getTrap(Machine vm, String name) {
        if (handler == null || handler == Undefined.value) {
            return null;
        }
        if (!(handler instanceof NgsObject)) {
            return null;
        }
        return ((NgsObject) handler).get(name);
    }

    /** Invokes the given trap with receiver = handler. */
    private Object callTrap(Machine vm, Object trap, Object[] args) throws Exception {
        return OpUtils.callValue(vm, trap, handler, args);
    }

    /**
     * Performs the ES [[Get]] operation with the get trap: the trap (when
     * present) receives (target, key, receiver) and its result is returned;
     * otherwise the value is read from the target itself.
     */
    public Object proxyGet(Machine vm, String key) throws Exception {
        checkRevoked(vm);
        Object trap = getTrap(vm, "get");
        if (trap != null && trap != Undefined.value) {
            return callTrap(vm, trap, new Object[]{target, key, this});
        }
        return OpUtils.getMember(vm, target, key);
    }

    /**
     * Performs the ES [[Set]] operation with the set trap: (target, key,
     * value, receiver). The trap result decides whether the target is
     * written (only when the trap is absent).
     */
    public boolean proxySet(Machine vm, String key, Object value) throws Exception {
        checkRevoked(vm);
        Object trap = getTrap(vm, "set");
        if (trap != null && trap != Undefined.value) {
            Object r = callTrap(vm, trap, new Object[]{target, key, value, this});
            return !(r instanceof Boolean) || (Boolean) r;
        }
        if (target instanceof NgsObject) {
            ((NgsObject) target).set(key, value);
        }
        return true;
    }

    /**
     * Performs the ES [[HasProperty]] operation with the has trap:
     * (target, key). Absent trap -> the target's own 'has' check.
     */
    public boolean proxyHas(Machine vm, String key) throws Exception {
        checkRevoked(vm);
        Object trap = getTrap(vm, "has");
        if (trap != null && trap != Undefined.value) {
            Object r = callTrap(vm, trap, new Object[]{target, key});
            return r instanceof Boolean && (Boolean) r;
        }
        if (target instanceof NgsObject) {
            return ((NgsObject) target).has(key);
        }
        return false;
    }

    /**
     * Performs the ES [[Delete]] operation with the deleteProperty trap:
     * (target, key). Absent trap -> remove from the target.
     */
    public boolean proxyDelete(Machine vm, String key) throws Exception {
        checkRevoked(vm);
        Object trap = getTrap(vm, "deleteProperty");
        if (trap != null && trap != Undefined.value) {
            Object r = callTrap(vm, trap, new Object[]{target, key});
            return !(r instanceof Boolean) || (Boolean) r;
        }
        if (target instanceof NgsObject) {
            return ((NgsObject) target).remove(key);
        }
        return true;
    }

    /**
     * Performs the ES [[Call]] operation with the apply trap:
     * (target, thisArg, argArray). Absent trap -> the target is invoked
     * with the same arguments.
     */
    public Object proxyApply(Machine vm, Object thisArg, Object[] args) throws Exception {
        checkRevoked(vm);
        Object trap = getTrap(vm, "apply");
        if (trap != null && trap != Undefined.value) {
            return callTrap(vm, trap, new Object[]{target, thisArg, toArgArray(vm, args)});
        }
        return OpUtils.callValue(vm, target, thisArg, args);
    }

    /**
     * Performs the ES [[Construct]] operation with the construct trap:
     * (target, argArray, newTarget). Absent trap -> construct the target.
     */
    public Object proxyConstruct(Machine vm, Object[] args) throws Exception {
        checkRevoked(vm);
        Object trap = getTrap(vm, "construct");
        if (trap != null && trap != Undefined.value) {
            return callTrap(vm, trap, new Object[]{target, toArgArray(vm, args), this});
        }
        return OpUtils.callValue(vm, target, null, args);
    }

    /** Wraps a Java args array as a script-visible NgsArray. */
    private Object toArgArray(Machine vm, Object[] args) {
        NgsArray arr = new NgsArray(null);
        arr.setPrototype(org.ngscript.builtins.Builtins.getArrayPrototype());
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                arr.set("" + i, args[i]);
            }
        }
        return arr;
    }

    /**
     * A MemRef that routes reads through the get trap and writes through
     * the set trap (used by the member access opcodes).
     */
    static class ProxyMemRef extends MemRef {
        final ProxyValue proxy;
        final String key;
        final Machine vm;

        ProxyMemRef(ProxyValue proxy, String key, Machine vm) {
            this.proxy = proxy;
            this.key = key;
            this.vm = vm;
        }

        @Override
        public Object read() {
            try {
                return proxy.proxyGet(vm, key);
            } catch (NgScriptRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
        }

        @Override
        public void write(Object v) {
            try {
                proxy.proxySet(vm, key, v);
            } catch (NgScriptRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
        }
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) {
        if (member.startsWith("#")) {
            // ES PrivateGet/PrivateSet: private names are brand-checked
            // slots, never proxy traps — a proxy has no private slots, so
            // accessing a private member through it is always a TypeError
            // even when the target carries the same-named key
            throw new IllegalArgumentException(
                    "Cannot access private member '" + member + "'");
        }
        return new ProxyMemRef(this, member, vm);
    }
}
