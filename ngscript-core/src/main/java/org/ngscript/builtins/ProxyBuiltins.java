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
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.runtime.ProxyValue;
import org.ngscript.value.Invokable;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * The ES {@code Proxy} constructor and the {@code Reflect} object. The
 * engine's lightweight object model supports the get/set/apply/construct/
 * has/deleteProperty traps; the other traps (ownKeys, getOwnProperty-
 * Descriptor, ...) are approximated through direct target access.
 *
 * @author wssccc
 */
public class ProxyBuiltins {

    /** The shared Proxy.prototype. */
    private static NgsObject proxyPrototype;

    public static synchronized NgsObject getProxyPrototype() {
        if (proxyPrototype == null) {
            proxyPrototype = new NgsObject(null);
            proxyPrototype.setPrototype(Builtins.getObjectPrototype());
        }
        return proxyPrototype;
    }

    /** The Proxy constructor: new Proxy(target, handler). */
    public static class ProxyCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            Object target = args.length > 0 ? args[0] : null;
            Object handler = args.length > 1 ? args[1] : null;
            if (target == null || target == Undefined.value) {
                throw new NgScriptRuntimeException(vm, "Proxy target cannot be null");
            }
            if (handler == null || handler == Undefined.value) {
                throw new NgScriptRuntimeException(vm, "Proxy handler cannot be null");
            }
            if (!(handler instanceof NgsObject)) {
                throw new NgScriptRuntimeException(vm, "Proxy handler must be an object");
            }
            boolean callable = target instanceof Invokable
                    || target instanceof ScriptFunction;
            ProxyValue proxy = new ProxyValue(target, handler, callable);
            vm.eax.write(proxy);
        }
    }

    /** Proxy.revocable(target, handler) -> {proxy, revoke}. */
    public static class Revocable implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            Object target = args.length > 0 ? args[0] : null;
            Object handler = args.length > 1 ? args[1] : null;
            if (target == null || target == Undefined.value) {
                throw new NgScriptRuntimeException(vm, "Proxy target cannot be null");
            }
            if (handler == null || handler == Undefined.value
                    || !(handler instanceof NgsObject)) {
                throw new NgScriptRuntimeException(vm, "Proxy handler must be an object");
            }
            boolean callable = target instanceof Invokable
                    || target instanceof ScriptFunction;
            final ProxyValue proxy = new ProxyValue(target, handler, callable);
            NgsObject result = new NgsObject(null);
            result.setPrototype(Builtins.getObjectPrototype());
            result.set("proxy", proxy);
            result.set("revoke", (Invokable) (m, a) -> {
                proxy.revoked = true;
                m.eax.write(Undefined.value);
            });
            vm.eax.write(result);
        }
    }

    //----------------------------------------------------------------------
    // Reflect
    //----------------------------------------------------------------------

    /** The shared Reflect object holding the static methods. */
    public static NgsObject createReflect() {
        NgsObject reflect = new NgsObject(null);
        reflect.setPrototype(Builtins.getObjectPrototype());
        reflect.set("get", (Invokable) ProxyBuiltins::reflectGet);
        reflect.set("set", (Invokable) ProxyBuiltins::reflectSet);
        reflect.set("has", (Invokable) ProxyBuiltins::reflectHas);
        reflect.set("deleteProperty", (Invokable) ProxyBuiltins::reflectDelete);
        reflect.set("apply", (Invokable) ProxyBuiltins::reflectApply);
        reflect.set("construct", (Invokable) ProxyBuiltins::reflectConstruct);
        reflect.set("ownKeys", (Invokable) ProxyBuiltins::reflectOwnKeys);
        reflect.set("getPrototypeOf", (Invokable) ProxyBuiltins::reflectGetProto);
        reflect.set("setPrototypeOf", (Invokable) ProxyBuiltins::reflectSetProto);
        reflect.set("isExtensible", (Invokable) ProxyBuiltins::reflectIsExtensible);
        reflect.set("preventExtensions", (Invokable) ProxyBuiltins::reflectPrevent);
        reflect.set("getOwnPropertyDescriptor", (Invokable) ProxyBuiltins::reflectGetOwnDesc);
        return reflect;
    }

    private static Object requireObject(Machine vm, Object o, String what) {
        if (o == null || o == Undefined.value || !(o instanceof NgsObject)) {
            throw new NgScriptRuntimeException(vm, what + " must be an object");
        }
        return o;
    }

    /** Reflect.get(target, key[, receiver]) */
    private static void reflectGet(Machine vm, Object[] args) throws Exception {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.get target");
        String key = args.length > 1
                ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
        Object receiver = args.length > 2 ? args[2] : null;
        if (target instanceof ProxyValue) {
            if (receiver != null && receiver != Undefined.value) {
                vm.eax.write(proxyGetWithReceiver(vm, (ProxyValue) target, key, receiver));
                return;
            }
            vm.eax.write(((ProxyValue) target).proxyGet(vm, key));
            return;
        }
        vm.eax.write(OpUtils.getMember(vm, target, key));
    }

    /** get with an explicit receiver (used by Reflect.get on proxies). */
    private static Object proxyGetWithReceiver(Machine vm, ProxyValue proxy,
                                               String key, Object receiver) throws Exception {
        proxy.checkRevokedPublic(vm);
        Object trap = proxy.getTrapPublic(vm, "get");
        if (trap != null && trap != Undefined.value) {
            return OpUtils.callValue(vm, trap, proxy.handler,
                    new Object[]{proxy.target, key, receiver});
        }
        return OpUtils.getMember(vm, proxy.target, key);
    }

    /** Reflect.set(target, key, value[, receiver]) */
    private static void reflectSet(Machine vm, Object[] args) throws Exception {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.set target");
        String key = args.length > 1
                ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
        Object value = args.length > 2 ? args[2] : Undefined.value;
        Object receiver = args.length > 3 ? args[3] : null;
        if (target instanceof ProxyValue) {
            if (receiver != null && receiver != Undefined.value) {
                vm.eax.write(proxySetWithReceiver(vm, (ProxyValue) target, key, value, receiver));
                return;
            }
            vm.eax.write(((ProxyValue) target).proxySet(vm, key, value));
            return;
        }
        if (target instanceof NgsObject) {
            ((NgsObject) target).set(key, value);
            vm.eax.write(Boolean.TRUE);
            return;
        }
        vm.eax.write(Boolean.FALSE);
    }

    private static Object proxySetWithReceiver(Machine vm, ProxyValue proxy,
                                               String key, Object value,
                                               Object receiver) throws Exception {
        proxy.checkRevokedPublic(vm);
        Object trap = proxy.getTrapPublic(vm, "set");
        if (trap != null && trap != Undefined.value) {
            Object r = OpUtils.callValue(vm, trap, proxy.handler,
                    new Object[]{proxy.target, key, value, receiver});
            return r instanceof Boolean ? r : Boolean.TRUE;
        }
        if (proxy.target instanceof NgsObject) {
            ((NgsObject) proxy.target).set(key, value);
        }
        return Boolean.TRUE;
    }

    /** Reflect.has(target, key) */
    private static void reflectHas(Machine vm, Object[] args) throws Exception {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.has target");
        String key = args.length > 1
                ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
        if (target instanceof ProxyValue) {
            vm.eax.write(((ProxyValue) target).proxyHas(vm, key) ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        if (target instanceof NgsObject) {
            vm.eax.write(((NgsObject) target).has(key) ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        vm.eax.write(Boolean.FALSE);
    }

    /** Reflect.deleteProperty(target, key) */
    private static void reflectDelete(Machine vm, Object[] args) throws Exception {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.deleteProperty target");
        String key = args.length > 1
                ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
        if (target instanceof ProxyValue) {
            vm.eax.write(((ProxyValue) target).proxyDelete(vm, key) ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        if (target instanceof NgsObject) {
            vm.eax.write(((NgsObject) target).remove(key) ? Boolean.TRUE : Boolean.FALSE);
            return;
        }
        vm.eax.write(Boolean.TRUE);
    }

    /** Reflect.apply(target, thisArgument, argumentsList) */
    private static void reflectApply(Machine vm, Object[] args) throws Exception {
        Object target = args.length > 0 ? args[0] : null;
        Object thisArg = args.length > 1 ? args[1] : Undefined.value;
        Object argList = args.length > 2 ? args[2] : null;
        if (thisArg == null || thisArg == Undefined.value) {
            thisArg = Undefined.value;
        }
        Object[] callArgs = toArgsArray(vm, argList);
        if (target instanceof ProxyValue) {
            vm.eax.write(((ProxyValue) target).proxyApply(vm, thisArg, callArgs));
            return;
        }
        vm.eax.write(OpUtils.callValue(vm, target, thisArg, callArgs));
    }

    /** Reflect.construct(target, argumentsList[, newTarget]) */
    private static void reflectConstruct(Machine vm, Object[] args) throws Exception {
        Object target = args.length > 0 ? args[0] : null;
        Object argList = args.length > 1 ? args[1] : null;
        Object[] callArgs = toArgsArray(vm, argList);
        if (target instanceof ProxyValue) {
            vm.eax.write(((ProxyValue) target).proxyConstruct(vm, callArgs));
            return;
        }
        // construct a script function: create the instance, then call with NEW
        if (target instanceof ScriptFunction) {
            NgsObject instance = new NgsObject(null);
            instance.setPrototype(((ScriptFunction) target).getPrototypeObject());
            // run the constructor body with this bound to the instance
            Object savedThis = vm.thisRef.read();
            vm.thisRef.write(instance);
            try {
                vm.callFunctionSync(target, callArgs, instance);
            } finally {
                vm.thisRef.write(savedThis);
            }
            Object result = vm.eax.read();
            vm.eax.write(result instanceof NgsObject ? result : instance);
            return;
        }
        vm.eax.write(OpUtils.callValue(vm, target, null, callArgs));
    }

    /** Reflect.ownKeys(target) */
    private static void reflectOwnKeys(Machine vm, Object[] args) throws Exception {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.ownKeys target");
        Object trap = target instanceof ProxyValue
                ? ((ProxyValue) target).getTrapPublic(vm, "ownKeys") : null;
        if (trap != null && trap != Undefined.value) {
            vm.eax.write(OpUtils.callValue(vm, trap, ((ProxyValue) target).handler,
                    new Object[]{((ProxyValue) target).target}));
            return;
        }
        Object real = target instanceof ProxyValue ? ((ProxyValue) target).target : target;
        NgsArray arr = new NgsArray(null);
        arr.setPrototype(Builtins.getArrayPrototype());
        if (real instanceof NgsObject) {
            for (String k : ((NgsObject) real).keys()) {
                arr.set("" + arr.length(), k);
            }
        }
        vm.eax.write(arr);
    }

    /** Reflect.getPrototypeOf(target) */
    private static void reflectGetProto(Machine vm, Object[] args) {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.getPrototypeOf target");
        Object trap = target instanceof ProxyValue
                ? ((ProxyValue) target).getTrapPublic(vm, "getPrototypeOf") : null;
        if (trap != null && trap != Undefined.value) {
            try {
                vm.eax.write(OpUtils.callValue(vm, trap, ((ProxyValue) target).handler,
                        new Object[]{((ProxyValue) target).target}));
                return;
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
        }
        Object real = target instanceof ProxyValue ? ((ProxyValue) target).target : target;
        if (real instanceof NgsObject) {
            NgsObject p = ((NgsObject) real).getPrototype();
            vm.eax.write(p == null ? Undefined.value : p);
        } else {
            vm.eax.write(Undefined.value);
        }
    }

    /** Reflect.setPrototypeOf(target, proto) */
    private static void reflectSetProto(Machine vm, Object[] args) {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.setPrototypeOf target");
        NgsObject proto = args.length > 1 && args[1] instanceof NgsObject ? (NgsObject) args[1] : null;
        if (target instanceof ProxyValue) {
            Object trap = ((ProxyValue) target).getTrapPublic(vm, "setPrototypeOf");
            if (trap != null && trap != Undefined.value) {
                try {
                    Object r = OpUtils.callValue(vm, trap, ((ProxyValue) target).handler,
                            new Object[]{((ProxyValue) target).target, proto});
                    vm.eax.write(r instanceof Boolean ? r : Boolean.TRUE);
                } catch (Exception e) {
                    throw new NgScriptRuntimeException(vm, e.getMessage() == null
                            ? e.toString() : e.getMessage());
                }
                return;
            }
            target = ((ProxyValue) target).target;
        }
        if (target instanceof NgsObject) {
            ((NgsObject) target).setPrototype(proto);
            vm.eax.write(Boolean.TRUE);
        } else {
            vm.eax.write(Boolean.FALSE);
        }
    }

    /** Reflect.isExtensible(target) — the model has no sealed objects. */
    private static void reflectIsExtensible(Machine vm, Object[] args) {
        requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.isExtensible target");
        vm.eax.write(Boolean.TRUE);
    }

    /** Reflect.preventExtensions(target) — no-op in this model. */
    private static void reflectPrevent(Machine vm, Object[] args) {
        requireObject(vm, args.length > 0 ? args[0] : null, "Reflect.preventExtensions target");
        vm.eax.write(Boolean.TRUE);
    }

    /** Reflect.getOwnPropertyDescriptor(target, key) */
    private static void reflectGetOwnDesc(Machine vm, Object[] args) {
        Object target = requireObject(vm, args.length > 0 ? args[0] : null,
                "Reflect.getOwnPropertyDescriptor target");
        String key = args.length > 1
                ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
        Object real = target instanceof ProxyValue ? ((ProxyValue) target).target : target;
        Object value = null;
        if (real instanceof NgsObject) {
            value = ((NgsObject) real).get(key);
        }
        if (value == null) {
            vm.eax.write(Undefined.value);
            return;
        }
        NgsObject desc = new NgsObject(null);
        desc.setPrototype(Builtins.getObjectPrototype());
        desc.set("value", value);
        desc.set("writable", Boolean.TRUE);
        desc.set("enumerable", Boolean.TRUE);
        desc.set("configurable", Boolean.TRUE);
        vm.eax.write(desc);
    }

    private static Object[] toArgsArray(Machine vm, Object argList) {
        if (argList == null || argList == Undefined.value) {
            return new Object[0];
        }
        if (argList instanceof NgsArray) {
            NgsArray a = (NgsArray) argList;
            Object[] r = new Object[a.length()];
            for (int i = 0; i < r.length; i++) {
                r[i] = a.get("" + i);
            }
            return r;
        }
        throw new NgScriptRuntimeException(vm, "arguments list must be an array");
    }
}
