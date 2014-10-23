package org.ngscript.common;

/*
 *  wssccc all rights reserved
 */
/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Instruction {

    public String op;
    public String param;
    public String param_extend;

    public Instruction(String op) {
        this.op = op;
    }

    public Instruction(String op, String param) {
        this.op = op;
        this.param = param;
    }

    public Instruction(String op, String param, String param_entend) {
        this.op = op;
        this.param = param;
        this.param_extend = param_entend;
    }

    @Override
    public String toString() {
        if (op.equals("//")) {
            return op + ' ' + (param == null ? "" : param);
        } else {
            //return op + (op.length() > 8 ? '\t' : "\t\t") + (param == null ? "" : param) + (param_extend == null ? "" : "," + param_extend);
            return String.format("%-15s%-30s", op, (param == null ? "" : param) + (param_extend == null ? "" : "," + param_extend));
        }

    }

}
