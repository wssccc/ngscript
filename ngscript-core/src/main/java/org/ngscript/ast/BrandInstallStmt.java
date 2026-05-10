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
 * A marker statement emitted at the top of a class constructor (base
 * classes) or right after the leading {@code super()} call (derived
 * classes): installs the instance brand so private members become
 * accessible (ES: private names are not installed before super returns —
 * accessing them earlier is a TypeError).
 *
 * @author wssccc
 */
public class BrandInstallStmt extends Stmt {

    public BrandInstallStmt(int line) {
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return "brand install";
    }
}
