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
 * A {@code super.name} member access in a class method: looks the member up
 * on the superclass prototype with the current instance as receiver.
 * The {@code super[expr]} computed form carries the key expression in
 * {@link #key} ({@code name} is null then).
 *
 * @author wssccc
 */
public class SuperMemberExpr extends Expr {

    public final String name;

    /** The computed key expression for {@code super[expr]} (null for super.name). */
    public final Expr key;

    public SuperMemberExpr(String name, int line) {
        this(name, null, line);
    }

    public SuperMemberExpr(String name, Expr key, int line) {
        this.name = name;
        this.key = key;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return key != null ? "super[" + key + "]" : name;
    }
}
