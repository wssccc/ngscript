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
 * An object literal: {@code {key: value, ...}}. {@link #items} holds the
 * elements in source order: {@link Kvp} pairs and {@link SpreadExpr}
 * operands... actually raw {@link Expr} spread sources, marked by
 * {@link #isSpread} parity is lost, so spreads are wrapped as
 * {@link SpreadExpr} nodes (a bare expression with spread=true).
 *
 * @author wssccc
 */
public class ObjectLiteral extends Expr {

    /** The elements in source order: Kvp or Expr (spread source). */
    public final List<Object> items;

    public ObjectLiteral(List<Object> items, int line) {
        this.items = items;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, items);
    }
}
