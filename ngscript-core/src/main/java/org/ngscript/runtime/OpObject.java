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

import java.util.Arrays;

import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.RegExpValue;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;
import org.ngscript.api.NgScriptException;

/**
 * Object opcodes: literal construction, member access, closures,
 * {@code typeof} and the {@code new} operator.
 *
 * @author wssccc
 */
public class OpObject {

    /**
     * The stack marker pushed by object spreads ({@code {...expr}}):
     * object_new expands its keys in place, keeping source order.
     */
    public static class ObjSpread {
        public final Object value;

        public ObjSpread(Object value) {
            this.value = value;
        }
    }

    /** The stack sentinel marking the bottom of one object literal. */
    public static final Object OBJ_START = new Object();

    /**
     * An accessor property inside an object literal ({@code { get v() {} }}):
     * object_new calls defineAccessor with the function as getter or setter.
     */
    public static class ObjAccessor {
        public final Object key;
        public final Object fn;
        public final String kind;

        public ObjAccessor(Object key, Object fn, String kind) {
            this.key = key;
            this.fn = fn;
            this.kind = kind;
        }
    }

    /** Pushes an accessor pair for object_new to define. */
    public static void obj_accessor(Machine vm, String param, String param_extend) {
        Object fn = vm.eax.read();
        Object key = vm.stack.pop();
        vm.stack.push(new ObjAccessor(key, fn, param));
    }

    /** Pushes the object literal bottom sentinel. */
    public static void obj_start(Machine vm, String param, String param_extend) {
        vm.stack.push(OBJ_START);
    }

    /** Pushes the current eax value as an object spread element. */
    public static void obj_spread(Machine vm, String param, String param_extend) {
        vm.stack.push(new ObjSpread(vm.eax.read()));
    }

    public static void object_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        NgsObject sh = new NgsObject((NgsObject) vm.env.read());
        sh.setPrototype(vm.objectPrototype);
        // collect everything down to the OBJ_START sentinel: spreads expand
        // in place, explicit pairs pop value-then-key; the resulting list is
        // in reverse source order, so applying it backwards makes a later
        // duplicate key win (ES semantics)
        java.util.List<Object> keys = new java.util.ArrayList<>();
        java.util.List<Object> vals = new java.util.ArrayList<>();
        while (!vm.stack.isEmpty()) {
            Object top = vm.stack.pop();
            if (top == OBJ_START) {
                break;
            }
            if (top instanceof ObjAccessor) {
                ObjAccessor acc = (ObjAccessor) top;
                String k = acc.key instanceof String ? (String) acc.key
                        : OpArray.keyString(acc.key);
                if ("get".equals(acc.kind)) {
                    sh.defineAccessor(k, acc.fn,
                            sh.getAccessor(k, "set"));
                } else {
                    sh.defineAccessor(k, sh.getAccessor(k, "get"), acc.fn);
                }
                continue;
            }
            if (top instanceof ObjSpread) {
                Object value = ((ObjSpread) top).value;
                if (value instanceof NgsObject) {
                    for (String k : ((NgsObject) value).keys()) {
                        keys.add(k);
                        vals.add(((NgsObject) value).get(k));
                    }
                } else if (value != null && value != Undefined.value) {
                    throw new NgScriptRuntimeException(vm,
                            "spread of a non-object value is not supported");
                }
            } else {
                // explicit pair: value on top, key below
                Object key = vm.stack.pop();
                keys.add(key);
                vals.add(top);
            }
        }
        for (int i = keys.size() - 1; i >= 0; i--) {
            sh.set(keys.get(i) instanceof String ? (String) keys.get(i)
                    : OpArray.keyString(keys.get(i)), vals.get(i));
        }
        vm.eax.write(sh);
    }

    public static void member_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        String member = (String) objs[1];
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            MemRef ref = env.lookup(member, vm, true);
            if (System.getProperty("dbg.set") != null) System.err.println("MEMBER_REF " + member + " -> " + ref.getClass().getSimpleName());
            vm.eax.write(ref);
            return;
        }
        memberRefNative(vm, objs, member);
    }

    /**
     * Computed-key member read for destructuring: the stack holds
     * [.., obj, key, obj-copy]; the object copy is popped as the receiver
     * and the key below it is read (left on the stack for
     * destr_obj_rest).
     */
    public static void member_ref2(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object obj = vm.stack.pop();
        Object key = vm.stack.peek(0);
        Object[] objs = new Object[]{obj, key};
        memberRefNative(vm, objs, key.toString());
    }

    private static void memberRefNative(Machine vm, Object[] objs, String member) throws NgScriptRuntimeException {
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            MemRef ref = env.lookup(member, vm, true);
            if (System.getProperty("dbg.set") != null) System.err.println("MEMBER_REF " + member + " -> " + ref.getClass().getSimpleName());
            vm.eax.write(ref);
            return;
        }
        //member of native
        if (objs[0] instanceof ScriptFunction) {
            // class-defined static members (static m() {}, static x = 1)
            // take precedence over the intrinsic function properties
            // (call/apply/bind/name/length/prototype), per ES: class
            // elements are defined as own properties of the constructor
            java.util.Map<String, MemRef> own = vm.hostProps.get(objs[0]);
            if (own != null) {
                MemRef dyn = own.get(member);
                if (dyn != null) {
                    vm.eax.write(dyn);
                    return;
                }
            }
            MemRef ref = functionMember((ScriptFunction) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
            // functions inherit Object.prototype methods (hasOwnProperty,
            // toString, valueOf, isPrototypeOf) through Function.prototype
            // -> Object.prototype
            ref = objectProtoMember(member);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.ObjectCtor) {
            MemRef ref = org.ngscript.builtins.ObjectCtor.lookup(
                    (org.ngscript.builtins.ObjectCtor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof Invokable) {
            // native callables get the generic call/apply/bind placeholders
            // (the runtime this binding is the target when invoked), so
            // Object.prototype.hasOwnProperty.call(obj, k) chains
            if ("call".equals(member) || "apply".equals(member) || "bind".equals(member)) {
                ScriptFunction generic = new ScriptFunction(null, null, vm);
                generic.placeholder = true;
                generic.callMode = member;
                vm.eax.write(new MemRef(generic));
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.ArrayCtor) {
            MemRef ref = org.ngscript.builtins.ArrayCtor.lookup(
                    (org.ngscript.builtins.ArrayCtor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof String) {
            // ES String.prototype methods take precedence over the Java
            // reflection fallback (charAt/substring/indexOf/... stay native)
            MemRef ref = org.ngscript.builtins.StringProto.lookup(member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
            if (member.equals("length")) {
                // String.prototype.length is a data property, not a method
                vm.eax.write(new MemRef(((String) objs[0]).length()));
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.StringCtor) {
            MemRef ref = org.ngscript.builtins.StringCtor.lookup(
                    (org.ngscript.builtins.StringCtor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
            // fall back to Java static methods (String.format, valueOf, ...)
            Object jref = NgsObject.lookupNative(String.class, member, vm);
            if (jref != null) {
                vm.eax.write(jref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.NumberCtor) {
            MemRef ref = org.ngscript.builtins.NumberCtor.lookup(
                    (org.ngscript.builtins.NumberCtor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.FunctionCtor) {
            if (member.equals("prototype")) {
                // the shared Function.prototype: a placeholder function whose
                // call/apply/bind use the runtime this as the target
                ScriptFunction proto = new ScriptFunction(null, null, vm);
                proto.placeholder = true;
                vm.eax.write(new MemRef(proto));
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.PromiseBuiltins.Ctor) {
            MemRef ref = org.ngscript.builtins.PromiseBuiltins.lookup(
                    (org.ngscript.builtins.PromiseBuiltins.Ctor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.JSONBuiltins.Json) {
            MemRef ref = org.ngscript.builtins.JSONBuiltins.lookup(
                    (org.ngscript.builtins.JSONBuiltins.Json) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.ProxyBuiltins.ProxyCtor) {
            if (member.equals("revocable")) {
                vm.eax.write(new MemRef(new org.ngscript.builtins.ProxyBuiltins.Revocable()));
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.BigIntBuiltins.Ctor) {
            if (member.equals("asIntN")) {
                vm.eax.write(new MemRef((org.ngscript.value.Invokable) (m, a) ->
                        org.ngscript.builtins.BigIntBuiltins.asIntNPublic(m, a)));
                return;
            }
            if (member.equals("asUintN")) {
                vm.eax.write(new MemRef((org.ngscript.value.Invokable) (m, a) ->
                        org.ngscript.builtins.BigIntBuiltins.asUintNPublic(m, a)));
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.TypedArrayBuiltins.TypedArrayCtor) {
            org.ngscript.builtins.TypedArrayBuiltins.TypedArrayCtor ctor =
                    (org.ngscript.builtins.TypedArrayBuiltins.TypedArrayCtor) objs[0];
            if (member.equals("BYTES_PER_ELEMENT")) {
                vm.eax.write(new MemRef(TypedArrayValue.elementSize(ctor.kind)));
                return;
            }
            if (member.equals("prototype")) {
                vm.eax.write(new MemRef(
                        org.ngscript.builtins.TypedArrayBuiltins.getTypedArrayPrototype(ctor.kind)));
                return;
            }
        }
        // script-created properties on host objects (e.g. list.customFn = f)
        java.util.Map<String, MemRef> props = vm.hostProps.get(objs[0]);
        if (props != null) {
            MemRef dynamic = props.get(member);
            if (dynamic != null) {
                vm.eax.write(dynamic);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.value.BigIntValue) {
            // BigInt.prototype methods (the primitive is not an NgsObject)
            MemRef ref2 = org.ngscript.builtins.BigIntBuiltins.lookupPrimitive(
                    (org.ngscript.value.BigIntValue) objs[0], member, vm);
            if (ref2 != null) {
                vm.eax.write(ref2);
                return;
            }
        }
        Object ref = NgsObject.lookupNative(objs[0], member, vm);

        if (ref != null) {
            vm.eax.write(ref);
            return;
        }
        // Java bean property access: e.message -> e.getMessage() (a
        // capitalized getter is tried whenever the plain member lookup
        // missed); also maps the ES error name for thrown Java exceptions
        if (!(objs[0] instanceof NgsObject)) {
            if (objs[0] instanceof Throwable && member.equals("name")) {
                vm.eax.write(new MemRef(errorName((Throwable) objs[0])));
                return;
            }
            if (objs[0] instanceof Throwable && member.equals("constructor")) {
                // ES: thrown.constructor === TypeError/... — map the Java
                // exception class back to the ES error constructor
                vm.eax.write(new MemRef(errorCtor((Throwable) objs[0])));
                return;
            }
            String getterName = "get" + Character.toUpperCase(member.charAt(0)) + member.substring(1);
            if (!getterName.equals(member)) {
                Object getter = NgsObject.lookupNative(objs[0], getterName, vm);
                if (getter instanceof MemRef) {
                    Object val = ((MemRef) getter).read();
                    if (val instanceof org.ngscript.interop.JavaMethod) {
                        // direct reflection: JavaMethod.invoke expects a call
                        // frame on the stack, which a property read has not
                        try {
                            java.lang.reflect.Method m = objs[0].getClass().getMethod(getterName);
                            vm.eax.write(new MemRef(m.invoke(objs[0])));
                            return;
                        } catch (Exception ignored) {
                            // fall through to the undefined slot below
                        }
                    } else if (val != null && val != Undefined.value) {
                        vm.eax.write(new MemRef(val));
                        return;
                    }
                }
            }
        }
        // an unset property of a host object reads/writes as undefined
        java.util.Map<String, MemRef> created = vm.hostProps.computeIfAbsent(objs[0],
                k -> new java.util.HashMap<>());
        MemRef slot = created.get(member);
        if (slot == null) {
            slot = new MemRef(Undefined.value);
            created.put(member, slot);
        }
        vm.eax.write(slot);
    }

    /** The ES error name for the Java exception class mapping. */
    private static String errorName(Throwable t) {
        if (t instanceof org.ngscript.parser.ParserException
                || t instanceof org.ngscript.compiler.CompilerException) {
            // parse/compile failures are early errors: SyntaxError
            return "SyntaxError";
        }
        if (t instanceof org.ngscript.runtime.NgScriptRuntimeException
                || t instanceof NgScriptException) {
            // VM-internal errors (unresolvable reference, bad member
            // access, ...) surface as ReferenceError to script code
            return "ReferenceError";
        }
        if (t instanceof IllegalArgumentException) {
            return "TypeError";
        }
        if (t instanceof IndexOutOfBoundsException) {
            return "RangeError";
        }
        if (t instanceof IllegalStateException) {
            return "SyntaxError";
        }
        if (t instanceof java.net.URISyntaxException) {
            return "URIError";
        }
        return t.getClass().getSimpleName();
    }

    /** The ES error constructor class for the Java exception class mapping. */
    private static Class<?> errorCtor(Throwable t) {
        if (t instanceof org.ngscript.parser.ParserException
                || t instanceof org.ngscript.compiler.CompilerException) {
            // parse/compile failures are early errors: SyntaxError
            return IllegalStateException.class;
        }
        if (t instanceof IllegalArgumentException) {
            return IllegalArgumentException.class;
        }
        if (t instanceof IndexOutOfBoundsException) {
            return IndexOutOfBoundsException.class;
        }
        if (t instanceof IllegalStateException) {
            return IllegalStateException.class;
        }
        if (t instanceof java.net.URISyntaxException) {
            return java.net.URISyntaxException.class;
        }
        if (t instanceof RuntimeException) {
            return RuntimeException.class;
        }
        if (t instanceof Error) {
            return Error.class;
        }
        return t.getClass();
    }

    /**
     * ES function object properties: prototype, name, length, call, apply,
     * bind. call/apply/bind return generic placeholder functions (the
     * runtime this binding is the target function when they are invoked),
     * so they chain like real functions (Function.prototype.call.bind(...)).
     */
    public static MemRef functionMember(ScriptFunction fn, String member, Machine vm) {
        switch (member) {
            case "prototype":
                return new MemRef(fn.getPrototypeObject());
            case "name":
                return new MemRef(fn.name == null ? "" : fn.name);
            case "length":
                return new MemRef(fn.params);
            case "call":
            case "apply":
            case "bind":
                ScriptFunction generic = new ScriptFunction(null, null, vm);
                generic.placeholder = true;
                generic.callMode = member;
                return new MemRef(generic);
            default:
                return null;
        }
    }

    /**
     * Object.prototype methods reachable on functions (functions inherit
     * through Function.prototype -> Object.prototype). The generic
     * implementations live in {@link org.ngscript.builtins.ObjectProto} but
     * are re-registered here against the shared prototype object.
     */
    public static MemRef objectProtoMember(String member) {
        if (!"hasOwnProperty".equals(member) && !"toString".equals(member)
                && !"valueOf".equals(member) && !"isPrototypeOf".equals(member)) {
            return null;
        }
        Object v = org.ngscript.builtins.Builtins.getObjectPrototype().get(member);
        if (v == null) {
            return null;
        }
        return new MemRef(v);
    }

    /** Splits "enter|exit:label" into {enter, exitLabel}. */
    private static String[] splitEnter(String paramExtend) {
        if (paramExtend != null && paramExtend.contains("|")) {
            int bar = paramExtend.indexOf('|');
            String exitPart = paramExtend.substring(bar + 1);
            if (exitPart.startsWith("exit:")) {
                return new String[]{paramExtend.substring(0, bar), exitPart.substring(5)};
            }
        }
        return new String[]{paramExtend, null};
    }

    public static void static_func(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        String[] parts = splitEnter(param_extend);
        OpCall.new_closure(vm, parts[0], null);
        ScriptFunction f = (ScriptFunction) vm.eax.read();
        f.functionExitLabel = parts[1];
        f.name = param;
        OpScope.set(vm, param, null);
    }

    /** Binds a {@code function*} declaration (a generator function). */
    public static void static_func_gen(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        String[] parts = splitEnter(param_extend);
        OpCall.new_closure(vm, parts[0], null);
        ScriptFunction f = (ScriptFunction) vm.eax.read();
        f.functionExitLabel = parts[1];
        f.generator = true;
        f.name = param;
        OpScope.set(vm, param, null);
    }

    /** Binds an {@code async function} declaration. */
    public static void static_func_async(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        String[] parts = splitEnter(param_extend);
        OpCall.new_closure(vm, parts[0], null);
        ScriptFunction f = (ScriptFunction) vm.eax.read();
        f.functionExitLabel = parts[1];
        f.async = true;
        f.name = param;
        OpScope.set(vm, param, null);
    }

    /**
     * Script-visible value types whose {@code typeof} is reported as their
     * simple class name. Foreign Java classes always report their fully
     * qualified name.
     */
    private static final java.util.Set<Class<?>> TYPE_OF_SHORT_NAMES = new java.util.HashSet<>(Arrays.asList(
            Undefined.class, ScriptFunction.class, MemRef.class, Invokable.class,
            NgsObject.class, NgsArray.class, RegExpValue.class));

    public static void regex_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(new RegExpValue(param, param_extend));
    }

    public static void delete_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[0];
        // symbol keys canonicalize to their internal form (@@iterator etc.)
        String key = OpArray.keyString(objs[1]);
        if (obj instanceof ProxyValue) {
            try {
                vm.eax.write(((ProxyValue) obj).proxyDelete(vm, key) ? Boolean.TRUE : Boolean.FALSE);
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
        } else if (obj instanceof NgsObject) {
            vm.eax.write(((NgsObject) obj).remove(key) ? Boolean.TRUE : Boolean.FALSE);
        } else if (obj instanceof NgsArray) {
            vm.eax.write(((NgsArray) obj).remove(key) ? Boolean.TRUE : Boolean.FALSE);
        } else {
            vm.eax.write(Boolean.TRUE); // deleting a property of a host object: treat as success
        }
    }

    public static void void_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(Undefined.value);
    }

    /** Converts the current eax value to a property key string. */
    public static void to_string(Machine vm, String param, String param_extend) {
        Object v = vm.eax.read();
        if (v instanceof org.ngscript.value.SymbolValue) {
            vm.eax.write(((org.ngscript.value.SymbolValue) v).getId());
        } else {
            vm.eax.write(OpUtils.convertKey(vm, v));
        }
    }

    public static void in(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[1];
        String key = objs[0].toString();
        if (obj instanceof ProxyValue) {
            try {
                vm.eax.write(((ProxyValue) obj).proxyHas(vm, key) ? Boolean.TRUE : Boolean.FALSE);
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
        } else if (obj instanceof NgsObject) {
            vm.eax.write(((NgsObject) obj).has(key) ? Boolean.TRUE : Boolean.FALSE);
        } else if (obj instanceof String) {
            try {
                int idx = Integer.parseInt(key);
                vm.eax.write(idx >= 0 && idx < ((String) obj).length() ? Boolean.TRUE : Boolean.FALSE);
            } catch (NumberFormatException e) {
                vm.eax.write(Boolean.FALSE);
            }
        } else {
            vm.eax.write(Boolean.FALSE);
        }
    }

    public static void instanceof_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[0];
        Object ctor = objs[1];
        if (ctor instanceof ScriptFunction) {
            if (obj instanceof NgsObject) {
                vm.eax.write(((NgsObject) obj).isInstanceOf((ScriptFunction) ctor) ? Boolean.TRUE : Boolean.FALSE);
            } else {
                vm.eax.write(Boolean.FALSE);
            }
        } else if (ctor instanceof Class) {
            // Java classes: ordinary instanceof (e.g. e instanceof Error)
            vm.eax.write(((Class) ctor).isInstance(obj) ? Boolean.TRUE : Boolean.FALSE);
        } else if (ctor instanceof org.ngscript.builtins.ObjectCtor) {
            // x instanceof Object: match the shared Object.prototype
            vm.eax.write(obj instanceof NgsObject
                    && protoChainContains((NgsObject) obj,
                            ((org.ngscript.builtins.ObjectCtor) ctor).getPrototype())
                    ? Boolean.TRUE : Boolean.FALSE);
        } else if (ctor instanceof org.ngscript.builtins.ArrayCtor) {
            // x instanceof Array: match the shared Array.prototype
            vm.eax.write(obj instanceof NgsObject
                    && protoChainContains((NgsObject) obj,
                            ((org.ngscript.builtins.ArrayCtor) ctor).getPrototype())
                    ? Boolean.TRUE : Boolean.FALSE);
        } else if (ctor instanceof org.ngscript.builtins.NgsMap.Ctor) {
            vm.eax.write(obj instanceof org.ngscript.builtins.NgsMap);
        } else if (ctor instanceof org.ngscript.builtins.NgsSet.Ctor) {
            vm.eax.write(obj instanceof org.ngscript.builtins.NgsSet);
        } else if (ctor instanceof org.ngscript.builtins.NgsWeakMap.Ctor) {
            vm.eax.write(obj instanceof org.ngscript.builtins.NgsWeakMap);
        } else if (ctor instanceof org.ngscript.builtins.NgsWeakSet.Ctor) {
            vm.eax.write(obj instanceof org.ngscript.builtins.NgsWeakSet);
        } else if (ctor instanceof org.ngscript.builtins.PromiseBuiltins.Ctor) {
            vm.eax.write(obj instanceof org.ngscript.builtins.PromiseBuiltins.PromiseValue);
        } else if (ctor instanceof org.ngscript.builtins.SymbolCtor) {
            vm.eax.write(obj instanceof org.ngscript.value.SymbolValue);
        } else if (ctor instanceof org.ngscript.builtins.StringCtor) {
            vm.eax.write(obj instanceof String);
        } else {
            throw new NgScriptRuntimeException(vm, "right-hand side of instanceof is not callable");
        }
    }

    private static boolean protoChainContains(NgsObject obj, NgsObject target) {
        NgsObject p = obj.getPrototype();
        while (p != null) {
            if (p == target) {
                return true;
            }
            p = p.getPrototype();
        }
        return false;
    }

    /**
     * Prepares a for-in loop: converts the current value to an array of keys
     * and pushes it with the loop index on the stack.
     */
    public static void forin_init(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object obj = vm.eax.read();
        String[] keys;
        if (obj instanceof NgsObject) {
            java.util.Set<String> set = ((NgsObject) obj).keys();
            keys = set.toArray(new String[0]);
        } else if (obj instanceof String) {
            String s = (String) obj;
            keys = new String[s.length()];
            for (int i = 0; i < s.length(); i++) {
                keys[i] = i + "";
            }
        } else {
            keys = new String[0];
        }
        vm.stack.push(keys);
        vm.stack.push(0);
    }

    /**
     * Prepares a for-of loop: obtains an iterator for the value in eax (the
     * ES iterator protocol via @@iterator, Java iterables, or a code-point
     * iterator for strings) and pushes it on the stack.
     */
    public static void forof_init(Machine vm, String param, String param_extend) throws Exception {
        Object iterable = vm.eax.read();
        vm.stack.push(OpUtils.getIterator(vm, iterable));
    }

    /**
     * The sentinel marking a completed for-of iteration (so falsy element
     * values like 0 or "" never terminate the loop early).
     */
    private static final Object FOROF_DONE = new Object();

    /**
     * Advances a for-of loop: yields the next value in eax, or the
     * {@link #FOROF_DONE} sentinel when done (popping the iterator). JS
     * iterators are polled via next() and the result object's done/value
     * properties.
     */
    public static void forof_next(Machine vm, String param, String param_extend) throws Exception {
        Object iterator = vm.stack.peek(0);
        if (iterator instanceof java.util.Iterator) {
            java.util.Iterator it = (java.util.Iterator) iterator;
            if (it.hasNext()) {
                vm.eax.write(it.next());
                return;
            }
            vm.stack.pop();
            vm.eax.write(FOROF_DONE);
            return;
        }
        // generic ES iterator protocol: result = iterator.next()
        Object method = OpUtils.getMember(vm, iterator, "next");
        if (method == null || method == Undefined.value) {
            vm.stack.pop();
            vm.eax.write(FOROF_DONE);
            return;
        }
        Object result = OpUtils.callValue(vm, method, iterator, new Object[0]);
        if (result instanceof NgsObject) {
            Object done = ((NgsObject) result).get("done");
            if (OpUtils.testValue(done)) {
                vm.stack.pop();
                vm.eax.write(FOROF_DONE);
                return;
            }
            vm.eax.write(((NgsObject) result).get("value"));
            return;
        }
        vm.stack.pop();
        vm.eax.write(FOROF_DONE);
    }

    /** Jumps when eax holds the for-of done sentinel. */
    public static void jdone(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if (vm.eax.read() == FOROF_DONE) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    /** Pushes an iterator for the value in eax (destructuring array patterns). */
    public static void destr_iter_init(Machine vm, String param, String param_extend) throws Exception {
        vm.stack.push(OpUtils.getIterator(vm, vm.eax.read()));
    }

    /**
     * Yields the next iterator value in eax (undefined once exhausted; the
     * iterator stays on the stack until destr_iter_rest/done).
     */
    public static void destr_iter_next(Machine vm, String param, String param_extend) throws Exception {
        Object iterator = vm.stack.peek(0);
        vm.destrDone = false;
        if (iterator instanceof java.util.Iterator) {
            java.util.Iterator it = (java.util.Iterator) iterator;
            if (it.hasNext()) {
                vm.eax.write(it.next());
            } else {
                vm.destrDone = true;
                vm.eax.write(Undefined.value);
            }
            return;
        }
        Object method = OpUtils.getMember(vm, iterator, "next");
        if (method == null || method == Undefined.value) {
            vm.destrDone = true;
            vm.eax.write(Undefined.value);
            return;
        }
        Object result = OpUtils.callValue(vm, method, iterator, new Object[0]);
        if (result instanceof NgsObject && !OpUtils.testValue(((NgsObject) result).get("done"))) {
            vm.eax.write(((NgsObject) result).get("value"));
        } else {
            vm.destrDone = true;
            vm.eax.write(Undefined.value);
        }
    }

    /** Collects the remaining iterator values into an array (rest pattern). */
    public static void destr_iter_rest(Machine vm, String param, String param_extend) throws Exception {
        Object iterator = vm.stack.pop();
        NgsArray arr = new NgsArray((NgsObject) vm.env.read());
        arr.setPrototype(vm.arrayPrototype);
        int i = 0;
        long budget = vm.runBudget;
        long n = 0;
        boolean done = false;
        if (iterator instanceof java.util.Iterator) {
            java.util.Iterator it = (java.util.Iterator) iterator;
            while (it.hasNext()) {
                if (budget >= 0 && ++n > budget) {
                    throw new NgScriptRuntimeException(vm,
                            "instruction budget exceeded while destructuring rest");
                }
                arr.set("" + i++, it.next());
            }
            done = true;
        } else {
            while (true) {
                if (budget >= 0 && ++n > budget) {
                    throw new NgScriptRuntimeException(vm,
                            "instruction budget exceeded while destructuring rest");
                }
                Object method = OpUtils.getMember(vm, iterator, "next");
                Object result = OpUtils.callValue(vm, method, iterator, new Object[0]);
                if (result instanceof NgsObject && !OpUtils.testValue(((NgsObject) result).get("done"))) {
                    arr.set("" + i++, ((NgsObject) result).get("value"));
                } else {
                    done = true;
                    break;
                }
            }
        }
        // a rest pattern exhausts the iterator: IteratorClose applies only
        // when the iteration is abandoned early (done here means completed)
        if (!done) {
            closeIterator(vm, iterator);
        }
        vm.eax.write(arr);
    }

    /** Drops the iterator left on the stack by destr_iter_init. */
    public static void destr_iter_done(Machine vm, String param, String param_extend) {
        Object iterator = vm.stack.pop();
        if (!vm.destrDone) {
            // the iterator is not exhausted: ES IteratorClose calls return()
            closeIterator(vm, iterator);
        }
    }

    /** Closes an iterator via its return() method (ES IteratorClose). */
    private static void closeIterator(Machine vm, Object iterator) {
        if (iterator == null || !(iterator instanceof NgsObject)) {
            return;
        }
        try {
            Object ret = ((NgsObject) iterator).get("return");
            if (ret instanceof Invokable || ret instanceof ScriptFunction) {
                OpUtils.callValue(vm, ret, iterator, new Object[0]);
            }
        } catch (Throwable ignored) {
            // IteratorClose's return() throwing is swallowed by the spec
        }
    }

    /**
     * Collects the remaining own keys of the object at the stack top (the
     * ones not yet destructured) into a fresh object; the result is the
     * rest pattern's value. The object itself stays on the stack (the
     * caller pops it). {@code param} lists the already-consumed static
     * keys, followed by {@code ;N} where N is the number of computed keys
     * pushed above the object (popped here).
     */
    public static void destr_obj_rest(Machine vm, String param, String param_extend) {
        String staticPart = param;
        int computed = 0;
        int semi = param.indexOf(';');
        if (semi >= 0) {
            staticPart = param.substring(0, semi);
            try {
                computed = Integer.parseInt(param.substring(semi + 1));
            } catch (NumberFormatException ignored) {
                // malformed: no computed keys
            }
        }
        java.util.Set<String> consumed = new java.util.HashSet<>();
        if (staticPart != null && !staticPart.isEmpty()) {
            for (String k : staticPart.split(",")) {
                consumed.add(k);
            }
        }
        // pop the computed keys (pushed after the object, in source order)
        for (int i = 0; i < computed; i++) {
            Object k = vm.stack.pop();
            if (k != null) {
                consumed.add(k.toString());
            }
        }
        Object obj = vm.stack.peek(0);
        NgsObject rest = new NgsObject((NgsObject) vm.env.read());
        rest.setPrototype(vm.objectPrototype);
        if (obj instanceof NgsObject) {
            for (String key : ((NgsObject) obj).keys()) {
                if (!consumed.contains(key)) {
                    rest.set(key, ((NgsObject) obj).get(key));
                }
            }
        } else if (obj instanceof NgsArray) {
            NgsArray arr = (NgsArray) obj;
            for (int i = 0; i < arr.length(); i++) {
                String key = "" + i;
                if (!consumed.contains(key)) {
                    rest.set(key, arr.get(key));
                }
            }
        }
        vm.eax.write(rest);
    }

    /**
     * Defines an accessor method on the object at the stack bottom of the
     * method-definition frame: stack [proto, keyRef, fn]; the function in
     * eax becomes the getter or setter for key on proto. The keyRef and fn
     * slots are popped (the proto stays for the next method).
     */
    public static void accessor_define(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object fn = vm.eax.read();
        // stack [proto, keyString]; both popped (the proto stays on the
        // stack below for the next method definition)
        Object keyObj = vm.stack.pop();
        Object proto = vm.stack.pop();
        String key = keyObj == null ? "undefined" : keyObj.toString();
        if (proto instanceof NgsObject) {
            NgsObject p = (NgsObject) proto;
            Object getter = p.getAccessor(key, "get");
            Object setter = p.getAccessor(key, "set");
            if ("get".equals(param)) {
                p.defineAccessor(key, fn, setter);
            } else {
                p.defineAccessor(key, getter, fn);
            }
        } else {
            // host function (class constructor): static accessors live in
            // hostProps as an accessor-backed reference so member reads and
            // writes go through the getter/setter
            java.util.Map<String, MemRef> props = vm.hostProps.computeIfAbsent(proto,
                    k -> new java.util.HashMap<>());
            MemRef slot = props.get(key);
            Object getter = slot instanceof HostAccessorRef
                    ? ((HostAccessorRef) slot).getter : null;
            Object setter = slot instanceof HostAccessorRef
                    ? ((HostAccessorRef) slot).setter : null;
            if ("get".equals(param)) {
                getter = fn;
            } else {
                setter = fn;
            }
            props.put(key, new HostAccessorRef(getter, setter, proto, vm));
        }
        vm.eax.write(proto);
    }

    /**
     * An accessor-backed member reference on a host function (class static
     * getter/setter): reads invoke the getter, writes invoke the setter,
     * both with the constructor as the receiver.
     */
    private static class HostAccessorRef extends MemRef {
        final Object getter;
        final Object setter;
        final Object owner;
        final Machine vm;

        HostAccessorRef(Object getter, Object setter, Object owner, Machine vm) {
            super(null);
            this.getter = getter;
            this.setter = setter;
            this.owner = owner;
            this.vm = vm;
        }

        @Override
        public Object read() {
            if (getter == null) {
                return Undefined.value;
            }
            try {
                return OpUtils.callValue(vm, getter, owner, new Object[0]);
            } catch (Exception e) {
                throw e instanceof RuntimeException
                        ? (RuntimeException) e
                        : new RuntimeException(e.getMessage() == null
                                ? e.toString() : e.getMessage());
            }
        }

        @Override
        public void write(Object v) {
            if (setter == null) {
                throw new RuntimeException("Cannot set property without a setter");
            }
            try {
                OpUtils.callValue(vm, setter, owner, new Object[]{v});
            } catch (Exception e) {
                throw e instanceof RuntimeException
                        ? (RuntimeException) e
                        : new RuntimeException(e.getMessage() == null
                                ? e.toString() : e.getMessage());
            }
        }
    }

    /**
     * Binds {@code this} for a class static field initializer: the
     * constructor object in eax becomes the current this binding (ES static
     * fields run with the constructor as the receiver).
     */
    public static void set_this(Machine vm, String param, String param_extend) {
        vm.thisRef.write(vm.eax.read());
    }

    /** Saves the current this binding (static field initializer scope). */
    public static void push_this(Machine vm, String param, String param_extend) {
        vm.pushSavedThis();
    }

    /** Restores the this binding saved by push_this. */
    public static void pop_this(Machine vm, String param, String param_extend) {
        vm.popSavedThis();
    }

    /**
     * A code-point iterator for strings (ES iterates strings by Unicode code
     * point, combining surrogate pairs).
     */
    public static class CodePointIterator implements java.util.Iterator<Object> {

        private final String s;
        private int index;

        public CodePointIterator(String s) {
            this.s = s;
        }

        @Override
        public boolean hasNext() {
            return index < s.length();
        }

        @Override
        public Object next() {
            int cp = s.codePointAt(index);
            index += Character.charCount(cp);
            return new String(Character.toChars(cp));
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Advances a for-in loop: yields the next key in eax, or a falsy value
     * (0) when the iteration is done (and pops the iteration state).
     */
    public static void forin_next(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        int i = (Integer) vm.stack.pop();
        String[] keys = (String[]) vm.stack.peek(0);
        if (i < keys.length) {
            vm.stack.push(i + 1);
            vm.eax.write(keys[i]);
        } else {
            vm.stack.pop();
            vm.eax.write(0);
        }
    }

    /**
     * Reads {@code %superproto} safely: undeclared (object literal
     * methods, which have no class binding) yields undefined instead of
     * throwing "not declared".
     */
    public static void super_proto(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        try {
            vm.eax.write(vm.lookup("%superproto").read());
        } catch (NgScriptRuntimeException e) {
            if (e.getMessage() != null && e.getMessage().endsWith(" is not declared")) {
                vm.eax.write(Undefined.value);
                return;
            }
            throw e;
        }
    }

    /**
     * Resolves a {@code super} member: eax holds %superproto (bound by
     * class definitions) or undefined. With a bound superclass prototype
     * the member is looked up on it (class methods); otherwise the member
     * is resolved on the dynamic [[Prototype]] chain of {@code this}
     * (object literal methods, whose home object is the literal itself).
     */
    public static void super_ref(Machine vm, String param, String param_extend) {
        Object sp = vm.eax.read();
        if (sp instanceof NgsObject) {
            MemRef ref = findInProtoChain((NgsObject) sp, param);
            vm.eax.write(ref != null ? ref : new MemRef(Undefined.value));
            return;
        }
        Object t = vm.thisRef.read();
        if (t instanceof NgsObject) {
            MemRef ref = findInProtoChain(((NgsObject) t).getPrototype(), param);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        vm.eax.write(new MemRef(Undefined.value));
    }

    private static MemRef findInProtoChain(NgsObject p, String name) {
        while (p != null) {
            MemRef ref = p.getRef(name);
            if (ref != null) {
                return ref;
            }
            p = p.getPrototype();
        }
        return null;
    }

    public static void typeof(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object obj = vm.eax.read();
        //common types
        if (obj == Undefined.value) {
            vm.eax.write("undefined");
            return;
        }
        if (obj == null) {
            vm.eax.write("object");
            return;
        }
        if (obj instanceof Integer) {
            vm.eax.write("number");
            return;
        }
        if (obj instanceof Double) {
            vm.eax.write("number");
            return;
        }
        if (obj instanceof String) {
            vm.eax.write("string");
            return;
        }
        if (obj instanceof Boolean) {
            vm.eax.write("boolean");
            return;
        }
        if (obj instanceof ScriptFunction) {
            vm.eax.write("function");
            return;
        }
        if (obj instanceof org.ngscript.value.SymbolValue) {
            vm.eax.write("symbol");
            return;
        }
        if (obj instanceof org.ngscript.value.BigIntValue) {
            vm.eax.write("bigint");
            return;
        }
        if (obj instanceof Invokable) {
            vm.eax.write("function");
            return;
        }
        if (obj instanceof NgsObject || obj instanceof NgsArray || obj instanceof RegExpValue) {
            // ES: objects report "object" (foreign Java classes keep their
            // qualified name for the interop extension)
            vm.eax.write("object");
            return;
        }
        if (obj instanceof Throwable) {
            // thrown Java exceptions surface as ordinary error objects
            vm.eax.write("object");
            return;
        }
        if (obj.getClass().isAnonymousClass()) {
            vm.eax.write(obj.getClass().getSuperclass().getName());
        } else if (TYPE_OF_SHORT_NAMES.contains(obj.getClass())) {
            vm.eax.write(obj.getClass().getSimpleName());
        } else {
            vm.eax.write(obj.getClass().getName());
        }
    }

    public static void test(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef m = vm.lookup(param);
        if (m.read() == null) {
            vm.eax.write(0);
        } else {
            vm.eax.write(1);
        }
    }

    public static void clear(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef m = vm.lookup(param);
        m.write(Undefined.value);
    }

    public static void clear_null(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef m = vm.lookup(param);
        m.write(null);
    }

    /**
     * Resolves or creates an OWN member reference: unlike {@link #member_ref}
     * the prototype chain is not consulted, so defining a method on a class
     * prototype never overwrites an inherited one (ES assignment creates own
     * properties).
     */
    public static void own_member_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        String member = (String) objs[1];
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            MemRef ref = env.getRef(member);
            if (ref == null) {
                ref = new MemRef(Undefined.value);
                env.putRef(member, ref);
            }
            vm.eax.write(ref);
            return;
        }
        // host objects fall back to the shared hostProps map
        java.util.Map<String, MemRef> created = vm.hostProps.computeIfAbsent(objs[0],
                k -> new java.util.HashMap<>());
        MemRef slot = created.get(member);
        if (slot == null) {
            slot = new MemRef(Undefined.value);
            created.put(member, slot);
        }
        vm.eax.write(slot);
    }

    /**
     * Binds {@code %superclass} and {@code %superproto} in the active frame
     * from the superclass constructor in eax (used by class definitions so
     * derived methods and constructors can reach super).
     */
    public static void class_bind_super(Machine vm, String param, String param_extend) {
        Object sc = vm.eax.read();
        NgsObject frame = (NgsObject) vm.env.read();
        frame.putRef("%superclass", new MemRef(sc));
        if (sc instanceof ScriptFunction) {
            frame.putRef("%superproto", new MemRef(((ScriptFunction) sc).getPrototypeObject()));
        }
        if (System.getProperty("dbg.super") != null) {
            System.err.println("BIND_SUPER frame@" + System.identityHashCode(frame)
                    + " isGlobal=" + (frame == org.ngscript.runtime.Machine.globalThis)
                    + " parentRootIsGlobal=" + (frame.rootFrame() == org.ngscript.runtime.Machine.globalThis)
                    + " super=" + sc.getClass().getSimpleName());
        }
    }

    /**
     * Links the constructor's prototype to the superclass prototype (ES
     * extends); the stack holds the superclass constructor below the derived
     * constructor (both popped), the derived constructor stays in eax.
     */
    public static void proto_extend(Machine vm, String param, String param_extend) {
        Object ctor = vm.stack.pop();
        Object sc = vm.stack.pop();
        if (ctor instanceof ScriptFunction && sc instanceof ScriptFunction) {
            ((ScriptFunction) ctor).getPrototypeObject()
                    .setPrototype(((ScriptFunction) sc).getPrototypeObject());
        }
    }

    /** Replaces the function in eax with its prototype object. */
    public static void func_proto(Machine vm, String param, String param_extend) {
        Object f = vm.eax.read();
        if (f instanceof ScriptFunction) {
            vm.eax.write(((ScriptFunction) f).getPrototypeObject());
        }
    }

    public static void new_op(Machine vm, String param, String param_extend) {
        // ES proxy [[Construct]]: the call path leaves the stack untouched
        // ([env, args[], func, receiver] from the top), so inspect the
        // function slot before popping anything.
        Object consPeek = vm.stack.size() > 2 ? vm.stack.peek(2) : null;
        if (consPeek instanceof ProxyValue) {
            ProxyValue proxy = (ProxyValue) consPeek;
            if (!proxy.callable) {
                throw new NgScriptRuntimeException(vm, "proxy target is not a constructor");
            }
            Object[] ctorArgs = vm.stack.size() > 1
                    ? (Object[]) vm.stack.peek(1) : new Object[0];
            NgsObject oldEnv = (NgsObject) vm.stack.peek(0);
            vm.stack.pop(); //env
            vm.stack.pop(); //args
            vm.stack.pop(); //func
            vm.stack.pop(); //receiver
            vm.env.write(oldEnv);
            try {
                vm.eax.write(proxy.proxyConstruct(vm, ctorArgs));
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
            return;
        }
        //identify native object and script object
        Object newObject = vm.env.read();
        NgsObject oldEnv;
        Object cons;
        if (newObject == null) {
            //native constructor: stack (top first): instance, env, args, func, receiver
            newObject = vm.stack.pop();
            oldEnv = (NgsObject) vm.stack.pop();
            vm.stack.pop(); //params
            cons = vm.stack.pop();
        } else {
            //script constructor: stack (top first): env, args, func, receiver
            oldEnv = (NgsObject) vm.stack.pop();
            vm.stack.pop(); //params
            cons = vm.stack.pop();
        }
        vm.stack.pop(); //receiver
        if (cons instanceof Invokable && !(cons instanceof ScriptFunction)) {
            // builtin constructors (Object/Array/Map/Set): the call already
            // produced the instance in eax; just restore the environment
            vm.env.write(oldEnv);
            return;
        }
        if (cons instanceof ScriptFunction) {
            ScriptFunction ctor = (ScriptFunction) cons;
            if (newObject instanceof NgsObject) {
                NgsObject instance = (NgsObject) newObject;
                instance.setConstructor(ctor);
                // link the instance to the constructor's prototype (ES [[Construct]])
                instance.setPrototype(ctor.getPrototypeObject());
                // a script constructor's frame is the instance: bind this
                vm.thisRef.write(instance);
            }
        }

        vm.eax.write(newObject);
        vm.env.write(oldEnv);
    }
}
