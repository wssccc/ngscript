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

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.ngscript.interop.JavaMethod;
import org.ngscript.value.Invokable;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * Call-related opcodes: closure creation, function calls, native imports and
 * machine-state save/restore for exception handling.
 *
 * @author wssccc
 */
public class OpCall {

    /**
     * The receiver sentinel pushed by new-expression calls (so String
     * receivers like {@code 'x'.charAt(0)} are not mistaken for it).
     */
    public static final Object NEW_RECEIVER = new Object();

    public static void new_closure(Machine vm, String param, String param_extend) {
        ScriptFunction closure = new ScriptFunction(param, (NgsObject) vm.env.read(), vm);
        closure.instructions = vm.instructions;
        // closures created inside a replace-style execution (eval /
        // CompiledScript) are dispatched by their own instruction array
        closure.evalClosure = vm.execNesting > 0;
        if (param_extend != null && param_extend.contains("|")) {
            // the exit label rides along: "<marker>|exit:<label>"
            int bar = param_extend.indexOf('|');
            String marker = param_extend.substring(0, bar);
            String exitPart = param_extend.substring(bar + 1);
            if (exitPart.startsWith("exit:")) {
                closure.functionExitLabel = exitPart.substring(5);
            }
            param_extend = marker;
        }
        if ("1".equals(param_extend)) {
            // arrow function: capture the lexical this binding
            closure.arrow = true;
            closure.capturedThis = vm.thisRef.read();
        } else if ("2".equals(param_extend)) {
            // generator function: calls create a Generator iterator
            closure.generator = true;
        } else if ("3".equals(param_extend)) {
            // async function: calls create a Promise
            closure.async = true;
        } else if ("4".equals(param_extend)) {
            // async arrow: lexical this + async
            closure.arrow = true;
            closure.capturedThis = vm.thisRef.read();
            closure.async = true;
        } else if ("5".equals(param_extend)) {
            // async generator method: generator + async
            closure.generator = true;
            closure.async = true;
        }
        vm.eax.write(closure);
    }

    /**
     * Sets the {@code callee} property on the arguments object in eax (the
     * currently executing function, from the call stack).
     */
    public static void bind_callee(Machine vm, String param, String param_extend) {
        Object argsObj = vm.eax.read();
        if (argsObj instanceof NgsObject && !vm.callstack.isEmpty()) {
            ((NgsObject) argsObj).set("callee", vm.callstack.peek());
        }
    }

    /**
     * Sets the function metadata (name/length) on the function value in eax;
     * emitted right after new_closure or static_func.
     */
    public static void set_func_meta(Machine vm, String param, String param_extend) {
        Object obj = vm.eax.read();
        if (obj instanceof ScriptFunction) {
            ScriptFunction f = (ScriptFunction) obj;
            f.params = Integer.parseInt(param);
            if (param_extend != null && !param_extend.isEmpty()) {
                f.name = param_extend;
            }
        }
    }

    public static void import_(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        String[] split = param.split("\\.");
        String shortName = split[split.length - 1];
        vm.imported.put(shortName, param);
    }

    public static void call(Machine vm, String param, String param_extend) throws Exception {
        //stack layout: [receiver, args[], func, env]
        Object callee = vm.stack.peek(2);
        Object[] args = (Object[]) vm.stack.peek(1);
        Object receiver = vm.stack.size() > 3 ? vm.stack.peek(3) : null;

        if (callee instanceof ProxyValue) {
            if (receiver == NEW_RECEIVER) {
                // new proxy(...): leave the stack untouched for new_op,
                // which runs the construct trap
                return;
            }
            // ES proxy [[Call]]: consult the apply trap when present
            ProxyValue proxy = (ProxyValue) callee;
            if (!proxy.callable) {
                throw new NgScriptRuntimeException(vm, "proxy target is not callable");
            }
            Object thisArg = (receiver == null)
                    ? Undefined.value : receiver;
            try {
                vm.eax.write(proxy.proxyApply(vm, thisArg, args));
            } catch (RuntimeException e) {
                // keep the original type: a TypeError (IllegalArgumentException)
                // must survive for `instanceof TypeError` checks
                throw e;
            } catch (Exception e) {
                throw new NgScriptRuntimeException(vm, e.getMessage() == null
                        ? e.toString() : e.getMessage());
            }
            return;
        }
        if (callee instanceof ScriptFunction) {
            ScriptFunction c = (ScriptFunction) callee;
            if (c.placeholder) {
                // generic Function.prototype call/apply/bind: the runtime
                // this binding is the target function; the current receiver
                // is that target (bound via the method call)
                Object target = receiver;
                try {
                    if ("bind".equals(c.callMode)) {
                        Object thisArg = args.length > 0 ? args[0] : null;
                        Object[] bound = new Object[Math.max(0, args.length - 1)];
                        System.arraycopy(args, 1, bound, 0, bound.length);
                        ScriptFunction bf = new ScriptFunction(null,
                                (NgsObject) vm.env.read(), vm);
                        bf.name = target instanceof ScriptFunction
                                ? ((ScriptFunction) target).name : null;
                        bf.boundTarget = target;
                        bf.boundThis = (thisArg == null || thisArg == Undefined.value)
                                ? Undefined.value : thisArg;
                        bf.boundArgs = bound;
                        vm.eax.write(bf);
                    } else if ("apply".equals(c.callMode)) {
                        Object thisArg = args.length > 0 ? args[0] : null;
                        Object recv = (thisArg == null || thisArg == Undefined.value)
                                ? Undefined.value : thisArg;
                        Object[] callArgs = new Object[0];
                        if (args.length > 1 && args[1] instanceof NgsArray) {
                            NgsArray a = (NgsArray) args[1];
                            callArgs = new Object[a.length()];
                            for (int i = 0; i < callArgs.length; i++) {
                                callArgs[i] = a.get("" + i);
                            }
                        }
                        vm.eax.write(OpUtils.callValue(vm, target, recv, callArgs));
                    } else {
                        // call: args[0] is the thisArg, the rest are arguments
                        Object thisArg = args.length > 0 ? args[0] : null;
                        Object recv = (thisArg == null || thisArg == Undefined.value)
                                ? Undefined.value : thisArg;
                        Object[] callArgs = new Object[Math.max(0, args.length - 1)];
                        System.arraycopy(args, 1, callArgs, 0, callArgs.length);
                        vm.eax.write(OpUtils.callValue(vm, target, recv, callArgs));
                    }
                } catch (RuntimeException e) {
                    // keep the original type: a TypeError (IllegalArgumentException)
                    // must survive for `instanceof TypeError` checks
                    throw e;
                } catch (Exception e) {
                    throw new NgScriptRuntimeException(vm, e.getMessage() == null
                            ? e.toString() : e.getMessage());
                }
                return;
            }
            if (c.boundTarget != null) {
                // bound function: fix the this binding and prepend the bound
                // arguments, then delegate to the target
                Object[] all = new Object[(c.boundArgs == null ? 0 : c.boundArgs.length) + args.length];
                if (c.boundArgs != null) {
                    System.arraycopy(c.boundArgs, 0, all, 0, c.boundArgs.length);
                }
                System.arraycopy(args, 0, all, c.boundArgs == null ? 0 : c.boundArgs.length, args.length);
                Object target = c.boundTarget;
                if (target instanceof ScriptFunction) {
                    // replace the current call frame (top-down offsets):
                    // [receiver=3, func=2, args=1, env=0]
                    vm.stack.setFromTop(1, all);
                    vm.stack.setFromTop(2, target);
                    vm.stack.setFromTop(3, c.boundThis);
                    call(vm, param, param_extend);
                    return;
                }
                if (target instanceof Invokable) {
                    Object savedThis = vm.thisRef.read();
                    vm.thisRef.write(c.boundThis);
                    try {
                        ((Invokable) target).invoke(vm, all);
                    } finally {
                        vm.thisRef.write(savedThis);
                    }
                    return;
                }
                throw new NgScriptRuntimeException(vm, "bound target is not callable");
            }
            if (c.generator) {
                if (receiver == NEW_RECEIVER) {
                    // generators are not constructible
                    throw new IllegalArgumentException(
                            (c.name == null ? "" : c.name + " ") + "is not a constructor");
                }
                // a generator call binds parameters synchronously (ES:
                // FunctionDeclarationInstantiation at call time), then
                // returns an iterator without running the body
                Generator g = new Generator(vm, c, args,
                        receiver == NEW_RECEIVER ? null : receiver);
                g.bindParams();
                vm.eax.write(g);
                return;
            }
            if (c.async) {
                if (receiver == NEW_RECEIVER) {
                    // async functions are not constructible
                    throw new IllegalArgumentException(
                            (c.name == null ? "" : c.name + " ") + "is not a constructor");
                }
                // an async call returns a promise and runs the body
                // synchronously until the first await
                try {
                    AsyncContext ctx = new AsyncContext(vm, c, args);
                    ctx.start();
                    vm.eax.write(ctx.promise);
                } catch (RuntimeException e) {
                    // keep the original type: a TypeError (IllegalArgumentException)
                    // must survive for `instanceof TypeError` checks
                    throw e;
                } catch (Exception e) {
                    throw new NgScriptRuntimeException(vm, e.getMessage() == null
                            ? e.toString() : e.getMessage());
                }
                return;
            }
            vm.callstack.push(c);
            c.savedThis = vm.thisRef.read();
            //prepare env
            NgsObject env = new NgsObject((NgsObject) c.environment);
            env.functionScope = true;
            vm.env.write(env);
            //bind this: a method receiver, the constructor frame for "new"
            //(the frame is the instance, per the new_op invariant), else undefined
            if (receiver == NEW_RECEIVER) {
                if (c.arrow) {
                    // arrows are not constructible
                    throw new IllegalArgumentException(
                            (c.name == null ? "" : c.name + " ") + "is not a constructor");
                }
                vm.thisRef.write(env);
                // link the prototype now so the constructor body itself can
                // check `this instanceof Ctor` (new_op re-links idempotently)
                env.setConstructor(c);
                env.setPrototype(c.getPrototypeObject());
                // ES [[Construct]]: new.target is the constructor
                vm.newTarget = c;
            } else if (c.arrow) {
                // arrows keep the this binding captured at creation time
                // (and inherit new.target lexically: the register is
                // untouched, so the value of the surrounding context stays)
                vm.thisRef.write(c.capturedThis != null ? c.capturedThis : Undefined.value);
            } else if (receiver != null) {
                vm.thisRef.write(receiver);
                // a plain call has no new.target
                vm.newTarget = null;
            } else {
                // plain call: this is the global object in sloppy mode,
                // undefined in strict mode
                vm.thisRef.write(c.strict ? Undefined.value : Machine.globalThis);
                vm.newTarget = null;
            }
            Integer nipObj = vm.labels.get(c.functionLabel);
            if ((c.evalClosure && c.instructions != null
                    && c.instructions != vm.instructions) || nipObj == null) {
                // a closure whose body lives in another instruction array
                // (created inside an eval / CompiledScript execution whose
                // execution has ended, or a script function called from
                // eval code whose label is not in the eval array): run the
                // body in a nested loop against its own array. Plain
                // (merged/append) scripts keep their labels in the current
                // array and take the normal path; the eval closure marker
                // avoids label-name collisions (every eval source restarts
                // line numbers at 1, so label names repeat across arrays).
                try {
                    vm.eax.write(vm.callClosureSync(
                            c, args, receiver, c.instructions, env));
                } catch (RuntimeException e) {
                    if (e instanceof ClosureBudgetExceeded) {
                        throw (ClosureBudgetExceeded) e;
                    }
                    // keep the original exception: a TypeError
                    // (IllegalArgumentException) or a thrown script value
                    // must survive for `instanceof` checks and JS catch
                    throw e;
                } catch (Exception e) {
                    Object jsError = (e instanceof NgScriptRuntimeException)
                            ? ((NgScriptRuntimeException) e).getJsError() : null;
                    throw new NgScriptRuntimeException(vm, e.getMessage() == null
                            ? e.toString() : e.getMessage(), jsError != null ? jsError : e);
                }
                return;
            }
            int nip = nipObj;
            vm.stack.push(vm.getEip());
            vm.setEip(nip);
            return;
        }

        //call native things
        if (callee instanceof Invokable) {
            // native callables (builtins, array methods) see the receiver as this
            Object savedThis = vm.thisRef.read();
            if (receiver != null && receiver != NEW_RECEIVER) {
                vm.thisRef.write(receiver);
            }
            try {
                ((Invokable) callee).invoke(vm, args);
            } catch (Exception ex) {                Object e = ex.getCause();
                if (e == null) {
                    e = ex;
                }
                vm.exception.write(e);
                restore_machine_state(vm, null, null);
            }
            // a direct-eval marker applies to this call only: clear it so a
            // reassigned eval (or a later plain call) cannot leak the flag
            vm.evalDirectFlag = false;
            vm.thisRef.write(savedThis);
            return;
        }
        if (callee instanceof JavaMethod) {
            JavaMethod closure = (JavaMethod) callee;
            closure.invoke(vm, args);
            return;
        }

        //constructor
        if (callee instanceof Class) {
            Class cls = (Class) callee;
            Class[] types = vm.getParamTypes(1);
            Constructor[] conses = cls.getConstructors();
            Constructor properCons = null;
            if (conses.length == 1) {
                properCons = conses[0];
            } else {
                for (Constructor cons : conses) {
                    if (TypeCheckUtils.typeAcceptable(types, cons.getParameterTypes())) {
                        properCons = cons;
                        break;
                    }
                }
            }
            if (properCons == null) {
                throw new NgScriptRuntimeException(vm, "no constructor found for " + Arrays.toString(types));
            }
            //adjust args
            if (properCons.isVarArgs()) {
                Class[] argTypes = properCons.getParameterTypes();
                int nonVarsCount = argTypes.length - 1;
                Object[] newargs = new Object[nonVarsCount + 1];
                Object[] varargs = new Object[args.length - nonVarsCount];
                System.arraycopy(args, nonVarsCount, varargs, 0, varargs.length);
                System.arraycopy(args, 0, newargs, 0, newargs.length);
                newargs[newargs.length - 1] = varargs;
                args = newargs;
            }
            //prepare env
            Object instance = properCons.newInstance(args);
            if (receiver == NEW_RECEIVER) {
                // new: the instance stays on the stack for new_op
                vm.stack.push(instance);
                vm.env.write(null);
            } else {
                // plain call: the result goes to eax and the call frame is
                // left for the caller's cleanup pops (no extra push — the
                // stack would otherwise be unbalanced)
                vm.eax.write(instance);
            }
            return;
        }
            throw new IllegalArgumentException("unexpected callee " + callee
                    + " (" + (callee == null ? "null" : callee.getClass()) + ")"
                    + " eip=" + vm.getEip()
                    + " stack=" + vm.stack.size()
                    + " top0=" + (vm.stack.size() > 0 ? vm.stack.peek(0) : "empty")
                    + " top1=" + (vm.stack.size() > 1 ? vm.stack.peek(1) : "empty")
                    + " top3=" + (vm.stack.size() > 3 ? vm.stack.peek(3) : "empty"));
    }

    public static void save_machine_state(Machine vm, String param, String param_extend) {
        Context ms = new Context(vm);
        vm.machineStateStack.push(ms);
        // the finally block's clear_call_stack trims the call stack to the
        // level at try entry; the marker must be established here (the
        // machine-level field is otherwise only updated by Context.restore,
        // so a normal try completion would trim to a stale level — often 0 —
        // popping the enclosing function's own callstack entry and making
        // its `ret` crash with "Stack is empty")
        vm.callStackSize = ms.callStackSize;
    }

    public static void drop_machine_state(Machine vm, String param, String param_extend) {
        vm.machineStateStack.pop();
    }

    public static void restore_machine_state(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if (vm.machineStateStack.isEmpty()) {
            Object ex = vm.exception.read();
            if (ex instanceof NgScriptRuntimeException) {
                throw (NgScriptRuntimeException) ex;
            } else if (ex instanceof RuntimeException) {
                // let the original exception through: catch clauses see the
                // real TypeError/RangeError/... (`e.message`, `instanceof`)
                throw (RuntimeException) ex;
            } else if (ex instanceof Error) {
                throw (Error) ex;
            } else if (ex instanceof Throwable) {
                throw new NgScriptRuntimeException((Throwable) ex);
            } else {
                if (Machine.TRACE) {
                    System.err.println("[restore-throw] exception=" + ex
                            + " class=" + (ex == null ? "null" : ex.getClass().getName()));
                }
                throw new NgScriptRuntimeException(vm, describeThrownValue(ex), ex);
            }
        } else {
            Context ms = vm.machineStateStack.pop();
            ms.restore(vm);
        }
    }

    /**
     * Formats a thrown script value (a non-Throwable) for the exception that
     * escapes to the host: an object carrying name/message properties —
     * e.g. a thrown {@code Test262Error} or {@code Error()} — reports
     * {@code "Name: message"} instead of an opaque {@code NgsObject@hash},
     * which used to mask the actual assertion failure. No user code runs
     * (properties are read without triggering accessors).
     */
    private static String describeThrownValue(Object value) {
        if (value instanceof NgsObject) {
            Object name = ((NgsObject) value).plainGet("name");
            Object message = ((NgsObject) value).plainGet("message");
            boolean hasName = name != null && !(name instanceof Undefined);
            boolean hasMessage = message != null && !(message instanceof Undefined);
            if (hasName || hasMessage) {
                StringBuilder sb = new StringBuilder();
                if (hasName) {
                    sb.append(org.ngscript.runtime.OpUtils.toStringValue(name)).append(": ");
                }
                if (hasMessage) {
                    sb.append(org.ngscript.runtime.OpUtils.toStringValue(message));
                }
                return sb.toString();
            }
        }
        return String.valueOf(value);
    }

    /**
     * Unwraps a wrapped script value from an exception: an op that called
     * into script (iterator next(), getters, callbacks) re-throws a thrown
     * JS value as an {@link NgScriptRuntimeException} whose jsError IS the
     * script value. Every catch that writes into the machine's exception
     * register (runInner, generator loops, async resumes) must unwrap so
     * the enclosing JS catch binds the original object — assert.throws
     * checks {@code thrown.constructor === Ctor}.
     */
    public static Object unwrapThrown(Throwable ex) {
        if (ex instanceof NgScriptRuntimeException) {
            Object js = ((NgScriptRuntimeException) ex).getJsError();
            if (js != null && !(js instanceof Throwable)) {
                return js;
            }
        }
        return ex;
    }
}
