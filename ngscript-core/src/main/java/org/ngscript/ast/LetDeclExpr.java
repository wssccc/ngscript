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

import java.util.List;

/**
 * A let/const declaration: {@code let a, b = 1;} or {@code const c = 2;}.
 * Also used as the target of for-in/of loops and as the init clause of a
 * C-style for statement. {@link #inits} is parallel to {@link #names}; a null
 * element means the binding initializes to undefined (const requires an
 * initializer, enforced by the parser).
 *
 * <p>Destructuring declarators ({@code let [a, b] = arr;}) use the parallel
 * {@link #patterns} list (non-null) with one element per declarator.</p>
 *
 * @author wssccc
 */
public class LetDeclExpr extends Expr {

    public final List<String> names;
    public final List<Expr> inits;
    public final List<Pattern> patterns;
    public final boolean isConst;

    public LetDeclExpr(List<String> names, List<Expr> inits, boolean isConst, int line) {
        this(names, inits, null, isConst, line);
    }

    public LetDeclExpr(List<String> names, List<Expr> inits, List<Pattern> patterns,
                       boolean isConst, int line) {
        this.names = names;
        this.inits = inits;
        this.patterns = patterns;
        this.isConst = isConst;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, (Object[]) inits.toArray(new Expr[0]));
    }
}
