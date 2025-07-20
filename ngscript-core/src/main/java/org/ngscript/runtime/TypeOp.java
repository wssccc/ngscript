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

package org.ngscript.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author wssccc
 */
public class TypeOp {

    public static final int OP_ADD = '+';
    public static final int OP_SUB = '-';
    public static final int OP_MUL = '*';
    public static final int OP_DIV = '/';
    public static final int OP_EQ = '=';
    public static final int OP_MOD = '%';

    public static Object eval(int op, Object o1, Object o2) throws NgScriptRuntimeException {
        return typeOp(op, o1, o2);
    }

    public static Object eval2(String op, Object o1, Object o2) throws NgScriptRuntimeException {
        try {
            Object val = _eval(op, o1, o2, false);
            return val;
        } catch (NoSuchMethodException ex) {
            try {
                Object val = _eval(op, o2, o1, true);
                return val;
            } catch (NoSuchMethodException ex1) {
                //return o1.toString() + o2.toString();
                Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
        throw new RuntimeException("cannot  " + op + " " + o1 + " " + o2);
    }

    private static Object typeOp(int opc, Object o1, Object o2) {
        switch (opc) {
            case '+':
                // ES: if either operand (after ToPrimitive) is a string, concat
                if (o1 instanceof String || o2 instanceof String) {
                    return OpUtils.toStringValue(o1) + OpUtils.toStringValue(o2);
                }
                return OpUtils.normalize(OpUtils.toNumber(o1) + OpUtils.toNumber(o2));
            case '-':
                return OpUtils.normalize(OpUtils.toNumber(o1) - OpUtils.toNumber(o2));
            case '*':
                return OpUtils.normalize(OpUtils.toNumber(o1) * OpUtils.toNumber(o2));
            case '/':
                // ES division always has double semantics: 10 / 4 == 2.5
                return OpUtils.normalize(OpUtils.toNumber(o1) / OpUtils.toNumber(o2));
            case '%':
                return OpUtils.normalize(OpUtils.toNumber(o1) % OpUtils.toNumber(o2));
            case '=':
                return OpUtils.abstractEquals(o1, o2);
            default:
                throw new RuntimeException("unsupported operator " + opc);
        }
    }

    //deprecated just for profiling
    private static Object _eval(String op, Object o1, Object o2, boolean reverse) throws NoSuchMethodException, NgScriptRuntimeException {
        Method m = reflectMethod(op, o1, o2);
        try {
            return m.invoke(null, o1.getClass().cast(o1), o2.getClass().cast(o2), reverse);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static Method reflectMethod(String op, Object o1, Object o2) throws NoSuchMethodException, NgScriptRuntimeException {
        String className1 = o1.getClass().getSimpleName();
        String className2 = o2.getClass().getSimpleName();

        try {
            Method m = TypeOp.class.getMethod(op + className1 + className2, o1.getClass(), o2.getClass(), boolean.class);
            return m;
        } catch (SecurityException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        }
        throw new RuntimeException("no proper function to " + op + o1.getClass() + " " + o2.getClass());
    }

    public static String addIntegerString(Integer obj1, String obj2, boolean reverse) {
        if (reverse) {
            return obj2 + obj1;
        } else {
            return obj1 + obj2;
        }
    }

    public static String addDoubleString(Double obj1, String obj2, boolean reverse) {
        if (reverse) {
            return obj2 + obj1;
        } else {
            return obj1 + obj2;
        }
    }

    public static String addStringString(String obj1, String obj2, boolean reverse) {
        if (reverse) {
            return obj2 + obj1;
        } else {
            return obj1 + obj2;
        }
    }

    public static Integer addIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return obj2 + obj1;
    }

    public static Double addIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        return obj2 + obj1;
    }

    public static Double addDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        return obj2 + obj1;
    }

    public static Integer mulIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return obj2 * obj1;
    }

    public static Double mulIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        return obj2 * obj1;
    }

    public static Double mulDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        return obj2 * obj1;
    }

    public static Integer eqIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public static Integer eqDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public static Integer eqIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public static Integer modIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return obj1 % obj2;
    }

    public static Integer eqIntegerString(Integer obj1, String obj2str, boolean reverse) {
        int obj2 = Integer.parseInt(obj2str);
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public static Integer eqDoubleString(Double obj1, String obj2str, boolean reverse) {
        double obj2 = Double.parseDouble(obj2str);
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public static Integer eqStringString(String obj1, String obj2str, boolean reverse) {
        return obj1.equals(obj2str) ? 1 : 0;
    }

    public static Integer subIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        if (reverse) {
            return obj2 - obj1;
        } else {
            return obj1 - obj2;
        }
    }

    public static Double subIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 - obj1;
        } else {
            return obj1 - obj2;
        }
    }

    public static Double subDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 - obj1;
        } else {
            return obj1 - obj2;
        }
    }

    public static Integer divIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        if (reverse) {
            return obj2 / obj1;
        } else {
            return obj1 / obj2;
        }
    }

    public static Double divIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 / obj1;
        } else {
            return obj1 / obj2;
        }
    }

    public static Double divDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 / obj1;
        } else {
            return obj1 / obj2;
        }
    }
}
