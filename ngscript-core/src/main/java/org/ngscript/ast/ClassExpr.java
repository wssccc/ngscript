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

import java.util.Collections;
import java.util.List;

/**
 * A class declaration or expression: {@code class C extends B { ... }}.
 * {@link #name} is null for anonymous class expressions; the name is bound
 * as a block-scoped (TDZ) binding, so {@code class C extends C} throws.
 *
 * @author wssccc
 */
public class ClassExpr extends Expr {

    /** A single class method definition. */
    public static class MethodDef {
        public final String name;
        public final Params params;
        public final Block body;
        public final boolean isStatic;
        public final boolean isGetter;
        public final boolean isSetter;
        public final boolean isCtor;
        public final boolean generator;
        public final boolean async;
        public final org.ngscript.ast.Expr computedKey;

        public MethodDef(String name, Params params, Block body, boolean isStatic,
                         boolean isGetter, boolean isSetter, boolean isCtor) {
            this(name, params, body, isStatic, isGetter, isSetter, isCtor, false, null);
        }

        public MethodDef(String name, Params params, Block body, boolean isStatic,
                         boolean isGetter, boolean isSetter, boolean isCtor,
                         boolean generator, org.ngscript.ast.Expr computedKey) {
            this(name, params, body, isStatic, isGetter, isSetter, isCtor,
                    generator, false, computedKey);
        }

        public MethodDef(String name, Params params, Block body, boolean isStatic,
                         boolean isGetter, boolean isSetter, boolean isCtor,
                         boolean generator, boolean async,
                         org.ngscript.ast.Expr computedKey) {
            this.name = name;
            this.params = params;
            this.body = body;
            this.isStatic = isStatic;
            this.isGetter = isGetter;
            this.isSetter = isSetter;
            this.isCtor = isCtor;
            this.generator = generator;
            this.async = async;
            this.computedKey = computedKey;
        }
    }

    public final String name;
    public final Expr superClass;
    public final List<MethodDef> methods;

    /**
     * A class field declaration: {@code x = init}, {@code static y = init}
     * or a computed key {@code [expr] = init}. {@link #initializer} is null
     * for a field without an initializer (initialized to undefined).
     */
    public static class FieldDef {
        public final String name;              // null when computedKey != null
        public final boolean isStatic;
        public final Expr computedKey;
        public final Expr initializer;

        public FieldDef(String name, boolean isStatic, Expr computedKey,
                        Expr initializer) {
            this.name = name;
            this.isStatic = isStatic;
            this.computedKey = computedKey;
            this.initializer = initializer;
        }
    }

    public final List<FieldDef> fields;

    public ClassExpr(String name, Expr superClass, List<MethodDef> methods, int line) {
        this(name, superClass, methods, Collections.<FieldDef>emptyList(), line);
    }

    public ClassExpr(String name, Expr superClass, List<MethodDef> methods,
                     List<FieldDef> fields, int line) {
        this.name = name;
        this.superClass = superClass;
        this.methods = methods;
        this.fields = fields;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, superClass);
    }
}
