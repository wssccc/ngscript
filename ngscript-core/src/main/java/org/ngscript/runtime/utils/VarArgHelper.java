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

package org.ngscript.runtime.utils;

/**
 * @author wssccc
 */
public class VarArgHelper {

    public static Object[] packVarArgs(Object[] args) {
        int nonVarsCount = args.length - 1;
        Object[] newArgs = new Object[nonVarsCount + 1];
        Object[] varArgs = new Object[args.length - nonVarsCount];
        System.arraycopy(args, nonVarsCount, varArgs, 0, varArgs.length);
        System.arraycopy(args, 0, newArgs, 0, newArgs.length);
        newArgs[newArgs.length - 1] = varArgs;
        return newArgs;
    }
}
