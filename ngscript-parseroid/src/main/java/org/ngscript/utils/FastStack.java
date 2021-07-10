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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author wssccc
 */
public class FastStack<T> {

    T[] elements;
    int size = 0;

    public FastStack(int initialCapacity) {
        elements = (T[]) new Object[initialCapacity];
    }

    public T peek() {
        return peek(0);
    }

    public T peek(int offset) {
        return elements[size - 1 - offset];
    }

    public void push(T e) {
        elements[size++] = e;
        if (size == elements.length) {
            doubleCapacity();
        }
    }

    public void add(T e) {
        push(e);
    }

    public T pop() {
        return elements[--size];
    }

    public void pop(int n) {
        size -= n;
    }

    public T get(int i) {
        return elements[i];
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }

    public List<T> last(int n) {
        T[] subElements = (T[]) new Object[n];
        System.arraycopy(elements, size - n, subElements, 0, subElements.length);
        return new ArrayList<>(Arrays.asList(subElements));
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void doubleCapacity() {
        T[] newElements = (T[]) new Object[elements.length << 1];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        elements = newElements;
    }

    @Override
    public String toString() {
        return Arrays.toString(elements);
    }
}
