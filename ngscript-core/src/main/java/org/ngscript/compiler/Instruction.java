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

package org.ngscript.compiler;


/**
 * @author wssccc
 */
public class Instruction {

    public String op;
    public String param;
    public String paramExt;

    public Instruction(String op) {
        this.op = op;
    }

    public Instruction(String op, String param) {
        this.op = op;
        this.param = param;
    }

    public Instruction(String op, String param, String paramExt) {
        this.op = op;
        this.param = param;
        this.paramExt = paramExt;
    }

    @Override
    public String toString() {
        if ("//".equals(op)) {
            return op + ' ' + (param == null ? "" : param) + "\n";
        } else {
            return String.format("%-15s%-30s", op, (param == null ? "" : param) + (paramExt == null ? "" : "," + paramExt)) + "\n";
        }

    }

}
