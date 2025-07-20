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

import java.util.ArrayList;

import org.ngscript.value.MemRef;

/**
 * Array opcodes: literal construction and indexed access.
 *
 * @author wssccc
 */
public class OpArray {

    public static void array_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        int length = Integer.parseInt(param);
        NgsArray list = new NgsArray((NgsObject) vm.env.read());
        list.setPrototype(vm.arrayPrototype);

        for (int i = 0; i < length; i++) {
            Object object = vm.stack.pop();
            list.set("" + i, object);
        }
        vm.eax.write(list);
    }

    public static void array_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            String member = "" + objs[1];
            MemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof ArrayList) {
            ArrayList env = (ArrayList) objs[0];
            int member = (int) Double.parseDouble("" + objs[1]);
            MemRef ref = (MemRef) env.get(member);
            vm.eax.write(ref);
            return;
        }
        throw new NgScriptRuntimeException(vm, "not an array object");
    }
}
