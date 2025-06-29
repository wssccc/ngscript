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

import org.ngscript.value.Undefined;

/**
 * Constant opcodes: literal values.
 *
 * @author wssccc
 */
public class OpConstant {

    public static void integer(Machine vm, String param, String param_extend) {
        vm.eax.write(Integer.parseInt(param));
    }

    public static void bool(Machine vm, String param, String param_extend) {
        vm.eax.write(Boolean.parseBoolean(param));
    }

    public static void double_(Machine vm, String param, String param_extend) {
        vm.eax.write(Double.parseDouble(param));
    }

    public static void undefined(Machine vm, String param, String param_extend) {
        vm.eax.write(Undefined.value);
    }

    public static void string(Machine vm, String param, String param_extend) {
        String str = param;
        if (str.startsWith("\"") && str.endsWith("\"")) {
            //quote string
            str = str.substring(1, str.length() - 1);
        }
        str = str.replace("\\n", "\n");
        vm.eax.write(str);
    }
}
