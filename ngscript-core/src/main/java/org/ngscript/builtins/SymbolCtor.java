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

import org.ngscript.runtime.Machine;
import org.ngscript.value.Invokable;
import org.ngscript.value.SymbolValue;

/**
 * The Symbol builtin: {@code Symbol(description)} creates a unique symbol;
 * the well-known symbols are exposed as fields ({@code Symbol.iterator}
 * etc.).
 *
 * @author wssccc
 */
public class SymbolCtor implements Invokable {

    public final SymbolValue iterator = new SymbolValue("@@iterator", "Symbol.iterator");
    public final SymbolValue toStringTag = new SymbolValue("@@toStringTag", "Symbol.toStringTag");
    public final SymbolValue hasInstance = new SymbolValue("@@hasInstance", "Symbol.hasInstance");
    public final SymbolValue species = new SymbolValue("@@species", "Symbol.species");
    public final SymbolValue toPrimitive = new SymbolValue("@@toPrimitive", "Symbol.toPrimitive");

    @Override
    public void invoke(Machine vm, Object[] args) throws Exception {
        String desc = null;
        if (args != null && args.length > 0 && args[0] != null) {
            desc = args[0].toString();
        }
        vm.eax.write(new SymbolValue(null, desc));
    }
}
