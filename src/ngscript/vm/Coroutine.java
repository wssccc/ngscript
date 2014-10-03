/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.vm.structure.NativeClosure;
import ngscript.vm.structure.VmClosure;
import ngscript.vm.structure.VmMemRef;
import ngscript.vm.structure.undefined;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public final class Coroutine extends VmClosure {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_RETURNED = "returned";

    LinkedList<Object> args = new LinkedList<Object>();
    Context context;
    private boolean running = false;//only a flag

    public Coroutine(VmClosure closure) {
        super(closure.func_label, new ScopeHash(closure.closure_env), closure.vm);
        setupCoroutine(closure);
    }

    public Coroutine(VmClosure closure, Object... args) {
        super(closure.func_label, new ScopeHash(closure.closure_env), closure.vm);
        //setup params
        this.args.addAll(Arrays.asList(args));
        setupCoroutine(closure);
    }

    public void push(Object obj) {
        args.add(obj);
    }

    final void setupCoroutine(VmClosure ref) {
        context = new Context(vm);
        context.stack = new Stack<Object>();

        //frame for call coroutine body
        context.stack.push(args);  //args
        context.stack.push(this);  //callee, for hook
        context.stack.push(null);//no need to know outter env
        context.stack.push(-1); //return address is a halt instruction

        context.stack_size = context.stack.size();

        //setup eip
        context.eip = ref.vm.labels.get(ref.func_label);
        context.env = this.closure_env;
        //args.addAll(Arrays.asList(objs));

        //gen stubs for coroutine
        try {
            closure_env.put("resume", new VmMemRef(new NativeClosure(this, this.getClass().getMethod("resume"))));
            ArrayList<Method> yields = new ArrayList<Method>();
            yields.add(this.getClass().getMethod("yield", Object.class));
            yields.add(this.getClass().getMethod("yield"));

            closure_env.put("yield", new VmMemRef(new NativeClosure(this, yields)));
            closure_env.put("status", new VmMemRef(new NativeClosure(this, this.getClass().getMethod("status"))));
            closure_env.put("push", new VmMemRef(new NativeClosure(this, this.getClass().getMethod("push", Object.class))));
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(Coroutine.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(Coroutine.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void resume() throws WscVMException {
        if (!STATUS_SUSPENDED.equals(this.status())) {
            throw new WscVMException(this.vm, "coroutine is not suspended");
        }
        running = true;
        ScopeHash outenv = (ScopeHash) vm.stack.peek();
        //switch context
        Context saved = new Context(vm);
        vm.contextStack.push(saved);
        context.restore(vm);
//        vm.stack.pop();
//        vm.stack.pop();//clear frame for resume
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
         when jumping back, we'll meet the pops for resume call,
         the pops for resume will be used to clear the frame of calling coroutine body
         */

        Context saved = vm.contextStack.pop();
        saved.restore(vm);
        vm.eax.write(retVal); //write return val, dumb
        running = false;
        return retVal;
    }

    public Object yield() {
        return yield(undefined.value);
    }

    public String status() {
        //see if context halts
        if (context.eip == -1) {
            return STATUS_RETURNED;
        } else if (running) {
            return STATUS_RUNNING;
        } else {
            return STATUS_SUSPENDED;
        }
    }
}
