/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime;

import org.ngscript.runtime.vo.FunctionDefinition;
import org.ngscript.runtime.vo.JavaMethod;
import org.ngscript.runtime.vo.VmMemRef;
import org.ngscript.runtime.vo.undefined;
import org.ngscript.utils.FastStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public final class Coroutine extends FunctionDefinition {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_RETURNED = "returned";

    Object[] args;
    Context context;
    private boolean running = false;

    public Coroutine(FunctionDefinition closure) {
        super(closure.functionLable, new Environment(closure.closure_env), closure.vm);
        setupCoroutine(closure);
    }

    public Coroutine(FunctionDefinition closure, Object... args) {
        super(closure.functionLable, new Environment(closure.closure_env), closure.vm);
        //setup params
        this.args = args;
        setupCoroutine(closure);
    }

    public void invoke(Object... obj) {
        args = obj;
    }

    final void setupCoroutine(FunctionDefinition ref) {
        context = new Context(vm);
        context.stack = new FastStack<>(32);

        //frame for call coroutine body
        context.stack.push(args);  //args
        context.stack.push(this);  //callee, for hook
        context.stack.push(null);//no need to know outter env
        context.stack.push(-1); //return address is a halt instruction

        context.stackSize = context.stack.size();

        //setup eip
        context.eip = ref.vm.labels.get(ref.functionLable);
        context.env = this.closure_env;
        //args.addAll(Arrays.asList(objs));

        //gen stubs for coroutine
        try {
            closure_env.data.put("resume", new VmMemRef(new JavaMethod(this, this.getClass().getMethod("resume"))));
            ArrayList<Method> yields = new ArrayList<Method>();
            yields.add(this.getClass().getMethod("yield", Object.class));
            yields.add(this.getClass().getMethod("yield"));

            closure_env.data.put("yield", new VmMemRef(new JavaMethod(this, yields)));
            closure_env.data.put("status", new VmMemRef(new JavaMethod(this, this.getClass().getMethod("status"))));
            closure_env.data.put("invoke", new VmMemRef(new JavaMethod(this, this.getClass().getMethod("invoke", Object[].class))));
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(Coroutine.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(Coroutine.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void resume() throws VmRuntimeException {
        if (!STATUS_SUSPENDED.equals(this.status())) {
            throw new VmRuntimeException(this.vm, "coroutine is not suspended");
        }
        running = true;
        Environment outenv = (Environment) vm.stack.peek();
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
        vm.stack.pop();
        vm.stack.pop();
        vm.stack.pop(); //pop 3 stack to clear yield() frame

        //adjust stack to normal 
        context.save(vm);
        /*
         save eip 
         3 is the offset from native call to the next instruction after call frame
         when calling back, we should not touch the pops for yield
         */
        context.eip = vm.eip + 3;
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
        return yield(undefined.value);
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
