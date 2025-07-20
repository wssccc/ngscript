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

import java.io.PrintWriter;
import java.util.Map;

import org.ngscript.runtime.Coroutine;
import org.ngscript.value.MemRef;

/**
 * Registry of ngscript built-in functions and objects. Individual built-ins
 * live in their own per-domain classes (see {@link ConsoleBuiltins},
 * {@link AssertBuiltins}, {@link ObjectBuiltins}); this class wires them into
 * the global environment map.
 *
 * @author wssccc
 */
public class Builtins {

    public static void register(Map<String, MemRef> map, PrintWriter out) {
        ConsoleBuiltins.register(map, out);
        AssertBuiltins.register(map);
        ObjectBuiltins.register(map);
        map.put("Coroutine", new MemRef(Coroutine.class));
        // ES global value properties
        map.put("NaN", new MemRef(Double.NaN));
        map.put("Infinity", new MemRef(Double.POSITIVE_INFINITY));
    }
}
