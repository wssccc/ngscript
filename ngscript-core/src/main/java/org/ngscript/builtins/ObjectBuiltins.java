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

import java.util.Map;

import org.ngscript.runtime.Environment;
import org.ngscript.runtime.Machine;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;

/**
 * Built-in {@code Object} factory: creates a fresh object scope.
 *
 * @author wssccc
 */
public class ObjectBuiltins {

    static void register(Map<String, MemRef> map) {
        map.put("Object", new MemRef(new CreateEnv()));
    }

    static class CreateEnv implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            //prepare env
            Environment env = new Environment((Environment) vm.env.read());
            vm.env.write(env);
        }
    }
}
