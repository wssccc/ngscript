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
 * Optional chaining segments: {@code a?.b}, {@code a?.[i]} and
 * {@code a?.(...)}. Each evaluates to undefined (without accessing the
 * member / calling the function) when the base value is null or undefined.
 * Longer chains ({@code a?.b.c}) keep their ordinary MemberExpr/CallExpr
 * wrappers; the compiler flattens and compiles them as one short-circuit
 * chain.
 *
 * @author wssccc
 */
public abstract class OptionalChainExpr extends Expr {

    /** The expression the optional segment is applied to. */
    public final Expr object;

    protected OptionalChainExpr(Expr object, int line) {
        this.object = object;
        this.line = line;
    }

    /** {@code a?.b}. */
    public static class Member extends OptionalChainExpr {
        public final String name;

        public Member(Expr object, String name, int line) {
            super(object, line);
            this.name = name;
        }

        @Override
        protected String toString(int indent) {
            return childrenToString(indent, object, name);
        }
    }

    /** {@code a?.[i]}. */
    public static class Index extends OptionalChainExpr {
        public final Expr index;

        public Index(Expr object, Expr index, int line) {
            super(object, line);
            this.index = index;
        }

        @Override
        protected String toString(int indent) {
            return childrenToString(indent, object, index);
        }
    }

    /** {@code a?.(args)} (an optional call of the base). */
    public static class Call extends OptionalChainExpr {
        public final List<Expr> args;

        public Call(Expr object, List<Expr> args, int line) {
            super(object, line);
            this.args = args;
        }

        @Override
        protected String toString(int indent) {
            return childrenToString(indent, object);
        }
    }
}
