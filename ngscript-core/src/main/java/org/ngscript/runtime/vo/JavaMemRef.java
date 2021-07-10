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

package org.ngscript.runtime.vo;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * @author wssccc
 */
@Slf4j
public class JavaMemRef extends VmMemRef {

    private Object obj;
    private Field field;

    public JavaMemRef(Object obj, Field field) {
        this.obj = obj;
        this.field = field;
    }

    @Override
    public Object read() {
        try {
            return field.get(obj);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            log.error("error read " + field.getName() + " of " + obj.getClass().getName(), ex);
        }
        return null;
    }

    @Override
    public void write(Object v) {
        try {
            field.set(obj, v);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            log.error("error write " + field.getName() + " of " + obj.getClass().getName(), ex);
        }
    }

}
