package org.ngscript.common;

/*
 *  wssccc all rights reserved
 */
/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Instruction {

    public OpCode op;
    public String param;
    public String paramExtended;

    public Instruction(OpCode op) {
        this.op = op;
    }

    public Instruction(OpCode op, String param) {
        this.op = op;
        this.param = param;
    }

    public Instruction(OpCode op, String param, String paramExtended) {
        this.op = op;
        this.param = param;
        this.paramExtended = paramExtended;
    }

    @Override
    public String toString() {
        if (op.equals("//")) {
            return op.name() + ' ' + (param == null ? "" : param);
        } else {
            return String.format("%-15s%-30s", op, (param == null ? "" : param) + (paramExtended == null ? "" : "," + paramExtended));
        }

    }

}
