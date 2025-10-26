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
 * A function expression: {@code function(params) { body }} or the arrow form
 * {@code (params) => { body }} / {@code x => expr}. Exactly one of
 * {@link #body} and {@link #bodyExpr} is non-null for arrows with expression
 * bodies; function expressions always use {@link #body}.
 *
 * @author wssccc
 */
public class LambdaExpr extends Expr {

    public final String name;
    public final Params params;
    public final Block body;
    public final Expr bodyExpr;
    public final boolean isArrow;

    /**
     * Whether this is a generator function ({@code function* ...}): calling
     * it returns an iterator instead of running the body.
     */
    public boolean generator;

    /**
     * Whether this is an async function ({@code async function ...} /
     * {@code async () => ...}): calling it returns a Promise and the body
     * may contain await expressions.
     */
    public boolean async;

    public LambdaExpr(Params params, Block body, int line) {
        this(null, params, body, line);
    }

    /**
     * @param name the optional name of a named function expression, or null
     */
    public LambdaExpr(String name, Params params, Block body, int line) {
        this(name, params, null, body, line, false);
    }

    /**
     * Creates an arrow function; exactly one of {@code bodyExpr}/{@code body}
     * is non-null (expression vs block body).
     */
    public LambdaExpr(Params params, Expr bodyExpr, Block body, int line, boolean isArrow) {
        this(null, params, bodyExpr, body, line, isArrow);
    }

    public LambdaExpr(String name, Params params, Expr bodyExpr, Block body, int line, boolean isArrow) {
        this.name = name;
        this.params = params;
        this.bodyExpr = bodyExpr;
        this.body = body;
        this.isArrow = isArrow;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, body != null ? (AstNode) body : bodyExpr);
    }
}
