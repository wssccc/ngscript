/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.vm.strcuture.NativeClosure;
import ngscript.vm.strcuture.VmClosure;
import ngscript.vm.strcuture.VmMemRef;
import ngscript.vm.strcuture.undefined;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public final class Coroutine extends VmClosure {

    LinkedList<Object> args = new LinkedList<Object>();
    int eip;
    String status = "suspended";

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

    void setupCoroutine(VmClosure ref) {
        //setup eip
        eip = ref.vm.labels.get(ref.func_label);
        //args.addAll(Arrays.asList(objs));
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
        //gen stubs for coroutine

    }

    public void resume() throws WscVMException {
        if ("returned".equals(status)) {
            throw new WscVMException(this.vm, "coroutine is already returned");
        }

        ScopeHash outenv = (ScopeHash) vm.stack.pop();
        vm.stack.pop();
        vm.stack.pop();//clear frame for resume
        //callstack
        vm.callstack.push(this);
        //frame for call coroutine body
        vm.stack.push(vm.eip); //return real addr(rip)
        vm.stack.push(args);  //args
        vm.stack.push(this);  //callee, for hook
        vm.stack.push(outenv);//
        vm.stack.push(vm.labels.get("coroutine_return_hook")); //hook return addr
        //switch context
        vm.env.write(this.closure_env);
        vm.eip = eip;
    }

    public Object yield(Object retVal) {
        /*
         save eip 
         3 is the offset from native call to the next instruction after call frame
         when calling back, we should not touch the pops for yield
         */
        eip = vm.eip + 3;
        vm.callstack.pop();
        //
        vm.stack.pop();
        vm.stack.pop();
        vm.stack.pop(); //pop 3 stack to clear yield() frame

        //adjust stack to normal 
        vm.stack.pop(); //pop hook in resume frame
        Integer rip = (Integer) vm.stack.get(vm.stack.size() - 4); //get rip
        vm.stack.remove(vm.stack.size() - 4); //remove rip
        vm.eax.write(retVal); //write return val, dumb
        /*
         when jumping back, we'll meet the pops for resume call,
         the pops for resume will be used to clear the frame of calling coroutine body
         */
        vm.eip = rip;
        return retVal;
    }

    public Object yield() {
        return yield(undefined.value);
    }

    public String status() {
        return status;
    }

    public void returned() {
        this.status = "returned";
    }
}
