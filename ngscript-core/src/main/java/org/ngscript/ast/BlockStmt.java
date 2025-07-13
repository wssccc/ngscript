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
 * A bare block used as a statement: {@code { ... }}.
 *
 * @author wssccc
 */
public class BlockStmt extends Stmt {

    public final Block block;

    public BlockStmt(Block block, int line) {
        this.block = block;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, block);
    }
}
