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

package org.ngscript.ast;

/**
 * A declaration expression: {@code var x} or {@code val x}.
 *
 * @author wssccc
 */
public class VarDeclExpr extends Expr {

    public final String name;
    public final boolean isVal;

    public VarDeclExpr(String name, boolean isVal, int line) {
        this.name = name;
        this.isVal = isVal;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return super.toString(indent) + " name=" + name + " isVal=" + isVal;
    }
}
