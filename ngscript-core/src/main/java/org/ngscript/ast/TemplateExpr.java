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
 * A template literal: {@code `a${x}b`}. {@link #chunks} holds the cooked text
 * parts (one more than {@link #exprs}); {@link #rawChunks} the unprocessed
 * source text used by tagged templates (String.raw).
 *
 * @author wssccc
 */
public class TemplateExpr extends Expr {

    public final List<String> chunks;
    public final List<String> rawChunks;
    public final List<Expr> exprs;

    public TemplateExpr(List<String> chunks, List<String> rawChunks, List<Expr> exprs, int line) {
        this.chunks = chunks;
        this.rawChunks = rawChunks;
        this.exprs = exprs;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, (Object[]) exprs.toArray(new Expr[0]));
    }
}
