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
 * A try/catch/finally statement; the finally branch is optional.
 *
 * @author wssccc
 */
public class TryStmt extends Stmt {

    public final Block tryBody;
    public final String catchVar;
    public final Block catchBody;
    public final Block finallyBody;

    public TryStmt(Block tryBody, String catchVar, Block catchBody, Block finallyBody, int line) {
        this.tryBody = tryBody;
        this.catchVar = catchVar;
        this.catchBody = catchBody;
        this.finallyBody = finallyBody;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, tryBody, "catchVar=" + catchVar, catchBody, finallyBody);
    }
}
