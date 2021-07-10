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

package org.ngscript.utils;

/**
 * @author wssccc
 */
public class FastStackTest {

    public static void main(String[] args) {
        FastStack<Integer> fastStack = new FastStack<>(32);
        fastStack.push(1);
        fastStack.push(2);
        fastStack.pop();
        fastStack.push(3);
        fastStack.push(4);
        fastStack.push(5);
        fastStack.push(6);
        fastStack.push(7);
        fastStack.add(8);
        System.out.println(fastStack);
    }

}