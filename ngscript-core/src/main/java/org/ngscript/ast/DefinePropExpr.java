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
 * A property definition on an object: {@code obj.key = value} with
 * define semantics (CreateDataProperty) — the prototype chain is not
 * consulted, so a prototype accessor or inherited property is shadowed by
 * the new own data property. Used by class field initializers, which must
 * define the field on the instance even when the prototype has a
 * same-named accessor.
 */
public class DefinePropExpr extends Expr {

    public final Expr object;
    /** The computed key expression, or null for a static {@link #name}. */
    public final Expr key;
    public final String name;
    public final Expr value;

    /**
     * When >= 0, the computed key is not re-evaluated here: it was computed
     * at class definition time (ES evaluates field names then, only the
     * initializer runs in the constructor) and is read from the class's
     * {@code %fieldKeys} array by this index.
     */
    public final int keyIndex;

    public DefinePropExpr(Expr object, String name, Expr value, int line) {
        this(object, null, name, value, line, -1);
    }

    public DefinePropExpr(Expr object, Expr key, String name, Expr value, int line) {
        this(object, key, name, value, line, -1);
    }

    public DefinePropExpr(Expr object, Expr key, String name, Expr value,
                          int line, int keyIndex) {
        this.object = object;
        this.key = key;
        this.name = name;
        this.value = value;
        this.keyIndex = keyIndex;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, object, key, name, value);
    }
}
