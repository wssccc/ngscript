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

package org.ngscript.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that {@link MethodTypeMap} cache keys isolate entries per
 * declaring class and per parameter type combination.
 *
 * @author wssccc
 */
public class MethodTypeMapTest {

    private MethodTypeMap map;

    @Before
    public void setUp() {
        map = MethodTypeMap.INSTANCE;
    }

    private List<Method> overloads(Class<?> cls, String name) {
        List<Method> result = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name)) {
                result.add(m);
            }
        }
        return result;
    }

    @Test
    public void sameNameSameTypesOnDifferentClassesDoNotShareEntries() {
        List<Method> mathMax = overloads(Math.class, "max");
        List<Method> strictMathMax = overloads(StrictMath.class, "max");
        assertTrue("Math.max overloads should exist", mathMax.size() >= 2);
        assertTrue("StrictMath.max overloads should exist", strictMathMax.size() >= 2);

        Class[] intArgs = new Class[]{Integer.class, Integer.class};
        Method fromMath = map.getProperMethod("max", mathMax, intArgs);
        Method fromStrict = map.getProperMethod("max", strictMathMax, intArgs);

        assertNotNull(fromMath);
        assertNotNull(fromStrict);
        assertEquals("math result should be declared by Math", Math.class, fromMath.getDeclaringClass());
        assertEquals("strict result should be declared by StrictMath", StrictMath.class, fromStrict.getDeclaringClass());
    }

    @Test
    public void differentParameterTypesDoNotShareEntries() {
        List<Method> mathMax = overloads(Math.class, "max");

        Class[] intArgs = new Class[]{Integer.class, Integer.class};
        Class[] doubleArgs = new Class[]{Double.class, Double.class};
        Class[] mixedArgs = new Class[]{Integer.class, Double.class};

        Method m1 = map.getProperMethod("max", mathMax, intArgs);
        Method m2 = map.getProperMethod("max", mathMax, doubleArgs);
        Method m3 = map.getProperMethod("max", mathMax, mixedArgs);
        assertNotNull(m1);
        assertNotNull(m2);
        assertNotNull(m3);

        // repeated resolution with identical arguments must be stable
        assertEquals("repeated int resolution should be stable", m1, map.getProperMethod("max", mathMax, intArgs));
        assertEquals("repeated double resolution should be stable", m2, map.getProperMethod("max", mathMax, doubleArgs));
        assertEquals("repeated mixed resolution should be stable", m3, map.getProperMethod("max", mathMax, mixedArgs));

        // resolution order must not leak across argument-type combinations:
        // resolving double args first must not change the int-args resolution
        MethodTypeMap fresh = MethodTypeMap.INSTANCE;
        Class[] freshIntArgs = new Class[]{Integer.class, Integer.class};
        Method intFirst = fresh.getProperMethod("max", mathMax, freshIntArgs);
        fresh.getProperMethod("max", mathMax, new Class[]{Double.class, Double.class});
        assertEquals("first-match should be independent of other resolutions", intFirst,
                fresh.getProperMethod("max", mathMax, new Class[]{Integer.class, Integer.class}));
    }

    @Test
    public void singleOverloadIsReturnedWithoutCaching() {
        List<Method> parseInt = overloads(Integer.class, "parseInt");
        assertTrue("Integer.parseInt overloads should exist", parseInt.size() >= 1);

        Method m = map.getProperMethod("parseInt", parseInt, new Class[]{String.class});
        assertNotNull(m);
        assertEquals(Integer.class, m.getDeclaringClass());
    }
}
