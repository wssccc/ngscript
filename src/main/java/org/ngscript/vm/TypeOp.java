/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class TypeOp {

    public static final int OP_ADD = '+';
    public static final int OP_SUB = '-';
    public static final int OP_MUL = '*';
    public static final int OP_DIV = '/';
    public static final int OP_EQ = '=';
    public static final int OP_MOD = '%';

    public static Object eval(int op, Object o1, Object o2) throws WscVMException {
        return typeOp(op, o1, o2);
    }

    public static Object eval2(String op, Object o1, Object o2) throws WscVMException {
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
        int type1 = getType(o1);
        int type2 = getType(o2);

        switch ((type1 << 2) + type2) {
            case 5:
                return opInteger(opc, (Integer) o1, (Integer) o2);
            case 6:
                return opDouble(opc, (Integer) o1, (Double) o2);
            case 9:
                return opDouble(opc, (Double) o1, (Integer) o2);
            case 10:
                return opDouble(opc, (Double) o1, (Double) o2);
            case 7:
            case 11:
            case 15:
            case 14:
            case 13:
                return opString(opc, o1, o2);
            default:
                return opString(opc, o1, o2);
        }
    }

    private static int opInteger(int op, int a1, int a2) {
        switch (op) {
            case '+':
                return a1 + a2;
            case '-':
                return a1 - a2;
            case '*':
                return a1 * a2;
            case '/':
                return a1 / a2;
            case '=':
                return a1 == a2 ? 1 : 0;
            case '%':
                return a1 % a2;
            default:
                throw new RuntimeException("unsupported operator " + op);
        }
    }

    private static double opDouble(int op, double a1, double a2) {
        switch (op) {
            case '+':
                return a1 + a2;
            case '-':
                return a1 - a2;
            case '*':
                return a1 * a2;
            case '/':
                return a1 / a2;
            case '=':
                return Math.abs(a1 - a2) < Double.MIN_NORMAL ? 1 : 0;
            case '%':
                return a1 % a2;
            default:
                throw new RuntimeException("unsupported operator " + op);
        }
    }

    private static String opString(int op, Object a1, Object a2) {
        switch (op) {
            case '+':
                return a1.toString() + a2.toString();
            case '=':
                return a1.toString().equals(a2.toString()) ? "1" : null;
            default:
                throw new RuntimeException("unsupported operator " + op);
        }
    }

    private static int getType(Object obj) {
        if (obj instanceof Integer) {
            return 1;
        }
        if (obj instanceof Double) {
            return 2;
        }
        if (obj instanceof String) {
            return 3;
        }
        return 0;
    }

    //deprecated just for profiling
    private static Object _eval(String op, Object o1, Object o2, boolean reverse) throws NoSuchMethodException, WscVMException {
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

    private static Method reflectMethod(String op, Object o1, Object o2) throws NoSuchMethodException, WscVMException {
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
