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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ngscript.interop.JavaMethod;
import org.ngscript.util.FastStack;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * @author wssccc
 */
public final class Coroutine extends ScriptFunction {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_RETURNED = "returned";

    Object[] args;
    Context context;
    private boolean running = false;

    public Coroutine(ScriptFunction closure) {
        super(closure.functionLabel, new NgsObject(closure.environment), closure.vm);
        setupCoroutine(closure);
    }

    public Coroutine(ScriptFunction closure, Object... args) {
        super(closure.functionLabel, new NgsObject(closure.environment), closure.vm);
        //setup params
        this.args = args;
        setupCoroutine(closure);
    }

    public void invoke(Object... obj) {
        args = obj;
    }

    final void setupCoroutine(ScriptFunction ref) {
        context = new Context(vm);
        context.stack = new FastStack<>(32);

        //frame for call coroutine body (new call layout):
        //stack (bottom first): receiver, func, args, env, retAddr
        context.stack.push(null);   //receiver
        context.stack.push(this);   //func, for hook
        context.stack.push(args);   //args
        context.stack.push(null);   //env
        context.stack.push(-1); //return address is a halt instruction

        context.stackSize = context.stack.size();

        //setup eip
        context.eip = ref.vm.labels.get(ref.functionLabel);
        context.env = this.environment;
        //args.addAll(Arrays.asList(objs));

        //gen stubs for coroutine
        try {
            environment.set("resume", new JavaMethod(this, this.getClass().getMethod("resume")));
            ArrayList<Method> yields = new ArrayList<Method>();
            yields.add(this.getClass().getMethod("yield", Object.class));
            yields.add(this.getClass().getMethod("yield"));

            environment.set("yield", new JavaMethod(this, yields));
            environment.set("status", new JavaMethod(this, this.getClass().getMethod("status")));
            environment.set("invoke", new JavaMethod(this, this.getClass().getMethod("invoke", Object[].class)));
        } catch (NoSuchMethodException | SecurityException ex) {
            Logger.getLogger(Coroutine.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void resume() throws NgScriptRuntimeException {
        if (!STATUS_SUSPENDED.equals(this.status())) {
            throw new NgScriptRuntimeException(this.vm, "coroutine is not suspended");
        }
        running = true;
        // default result when the coroutine body finishes without yielding
        vm.eax.write(Undefined.value);
        //switch context
        Context saved = new Context(vm);
        vm.contextStack.push(saved);
        context.restore(vm);
//        runtime.stack.pop();
//        runtime.stack.pop();//clear frame for resume
        //callstack
        vm.callstack.push(this);
    }

    public Object yield(Object retVal) {

        vm.callstack.pop();
        //
        //clear the yield() call frame: [receiver, func, args[], env]
        //(yield is a JavaMethod so the frame carries no return address)
        vm.stack.pop(); //env
        vm.stack.pop(); //args[]
        vm.stack.pop(); //func
        vm.stack.pop(); //receiver

        //adjust stack to normal 
        context.save(vm);
        /*
         save eip 
         4 is the offset from native call to the next instruction after call frame
         (the caller's cleanup pops: pop_env, pop, pop, pop)
         when calling back, we should not touch the pops for yield
         */
        context.eip = vm.getEip() + 4;
        /*
         when jumping back, will meet the pops for resume call,
         the pops for resume will be used to clear the frame of calling coroutine body
         */

        Context saved = vm.contextStack.pop();
        saved.restore(vm);
        //write return val, dumb
        vm.eax.write(retVal);
        running = false;
        return retVal;
    }

    public Object yield() {
        return yield(Undefined.value);
    }

    public String status() {
        if (context.eip == -1) {
            return STATUS_RETURNED;
        } else if (running) {
            return STATUS_RUNNING;
        } else {
            return STATUS_SUSPENDED;
        }
    }
}
