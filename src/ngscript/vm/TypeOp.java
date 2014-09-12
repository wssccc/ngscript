/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class TypeOp {

    WscVM vm;

    public TypeOp(WscVM vm) {
        this.vm = vm;
    }

    public Object eval(String op, Object o1, Object o2) throws WscVMException {
        try {
            Object val = _eval(op, o1, o2, false);

            return val;
        } catch (NoSuchMethodException ex) {
            try {
                Object val = _eval(op, o2, o1, true);
                return val;
            } catch (NoSuchMethodException ex1) {
                return o1.toString() + o2.toString();
                //Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
        //throw new WscVMException(vm, "cannot  " + op + " " + o1 + " " + o2);
    }

    private Object _eval(String op, Object o1, Object o2, boolean reverse) throws NoSuchMethodException, WscVMException {
        Method m = reflectMethod(op, o1, o2);
        try {
            return m.invoke(this, o1.getClass().cast(o1), o2.getClass().cast(o2), reverse);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private Method reflectMethod(String op, Object o1, Object o2) throws NoSuchMethodException, WscVMException {
        String className1 = o1.getClass().getSimpleName();
        String className2 = o2.getClass().getSimpleName();

        try {
            Method m = TypeOp.class.getMethod(op + className1 + className2, o1.getClass(), o2.getClass(), boolean.class);
            return m;
        } catch (SecurityException ex) {
            Logger.getLogger(TypeOp.class.getName()).log(Level.SEVERE, null, ex);
        }
        throw new WscVMException(vm, "no proper function to " + op + o1.getClass() + " " + o2.getClass());
    }

    public String addIntegerString(Integer obj1, String obj2, boolean reverse) {
        if (reverse) {
            return obj2 + obj1;
        } else {
            return obj1 + obj2;
        }
    }

    public String addDoubleString(Double obj1, String obj2, boolean reverse) {
        if (reverse) {
            return obj2 + obj1;
        } else {
            return obj1 + obj2;
        }
    }

    public String addStringString(Double obj1, String obj2, boolean reverse) {
        if (reverse) {
            return obj2 + obj1;
        } else {
            return obj1 + obj2;
        }
    }

    public Integer addIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return obj2 + obj1;
    }

    public Double addIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        return obj2 + obj1;
    }

    public Double addDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        return obj2 + obj1;
    }

    public Integer mulIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return obj2 * obj1;
    }

    public Double mulIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        return obj2 * obj1;
    }

    public Double mulDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        return obj2 * obj1;
    }

    public Integer eqIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public Integer eqDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public Integer eqIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public Integer modIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        return obj1 % obj2;
    }

    public Integer eqIntegerString(Integer obj1, String obj2str, boolean reverse) {
        int obj2 = Integer.parseInt(obj2str);
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public Integer eqDoubleString(Double obj1, String obj2str, boolean reverse) {
        double obj2 = Double.parseDouble(obj2str);
        return Math.abs(obj1 - obj2) < Double.MIN_VALUE ? 1 : 0;
    }

    public Integer eqStringString(String obj1, String obj2str, boolean reverse) {
        return obj1.equals(obj2str) ? 1 : 0;
    }

    public Integer subIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        if (reverse) {
            return obj2 - obj1;
        } else {
            return obj1 - obj2;
        }
    }

    public Double subIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 - obj1;
        } else {
            return obj1 - obj2;
        }
    }

    public Double subDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 - obj1;
        } else {
            return obj1 - obj2;
        }
    }

    public Integer divIntegerInteger(Integer obj1, Integer obj2, boolean reverse) {
        if (reverse) {
            return obj2 / obj1;
        } else {
            return obj1 / obj2;
        }
    }

    public Double divIntegerDouble(Integer obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 / obj1;
        } else {
            return obj1 / obj2;
        }
    }

    public Double divDoubleDouble(Double obj1, Double obj2, boolean reverse) {
        if (reverse) {
            return obj2 / obj1;
        } else {
            return obj1 / obj2;
        }
    }
}
