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

package org.ngscript.parseroid.grammar;

import java.io.Serializable;

/**
 * @author wssccc
 */
public class Production implements Serializable {

    public Symbol sym;
    public Symbol[] produces;
    public int id;

    public Production(Symbol sym, Symbol[] produces, int id) {
        this.sym = sym;
        this.produces = produces;
        this.id = id;
    }

    public boolean almostEquals(Production other) {
        if (this.sym != other.sym) {
            return false;
        }
        if (other.produces.length != this.produces.length) {
            return false;
        }
        for (int i = 0; i < produces.length; i++) {
            if (other.produces[i] != this.produces[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sym.identifier);
        sb.append("->");
        for (Symbol produce : produces) {
            sb.append(produce.identifier);
        }
        return sb.toString();
    }

}
