package org.ngscript.compiler;

/*
 *  wssccc all rights reserved
 */
import org.ngscript.common.Instruction;
import org.ngscript.common.OpCode;
import org.ngscript.parseroid.parser.AstNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscAssembler {

    ArrayList<Instruction> instructions = new ArrayList<Instruction>();
    HashMap<String, Integer> labels = new HashMap<String, Integer>();

    public String label(String name, AstNode ast) {
        String label_name = ast.token.type + "_$" + ast.token.line_no + "_" + name;
        int i;
        if (labels.containsKey(label_name)) {
            //search for distinct label
            i = labels.get(label_name) + 1;
        } else {
            i = 0;
        }
        labels.put(label_name, i);
        return label_name + (i == 0 ? "" : i);
    }

    public void emit(String opr, String param, String param_extend) {
        instructions.add(new Instruction(OpCode.valueOf(opr.toUpperCase()), param, param_extend));
    }

    public void emit(String opr, String param) {
        instructions.add(new Instruction(OpCode.valueOf(opr.toUpperCase()), param));
    }

    public void emit(String opr) {
        instructions.add(new Instruction(OpCode.valueOf(opr.toUpperCase())));
    }

    public static Instruction parseLine(String instruction) {
        int sep = instruction.indexOf(' ');
        if (sep == -1) {
            return new Instruction(OpCode.valueOf(instruction));
        } else {
            String op = instruction.substring(0, sep);
            String params = instruction.substring(sep + 1);

            return new Instruction(OpCode.valueOf(op), params);

        }
    }

    public static ArrayList<Instruction> toInstructions(String asm) {
        ArrayList<Instruction> instructions = new ArrayList<Instruction>();
        Scanner sc = new Scanner(asm);
        while (sc.hasNextLine()) {
            Instruction i = WscAssembler.parseLine(sc.nextLine());
            instructions.add(i);
        }
        return instructions;
    }

    public String getInfoString(int offset) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        int type = 0;
        for (Instruction instruction : instructions) {
            if (!instruction.op.equals("//")) {
                if (type == 1) {
                    builder.append("//------------------------------------------------");
                    builder.append("\n");
                }
                type = 0;
                builder.append(String.format("%08d", i + offset));
                builder.append('\t');
                builder.append(instruction.toString());
                builder.append("\n");
            } else {
                if (type == 0) {
                    builder.append("//------------------------------------------------");
                    builder.append("\n");
                }
                type = 1;
                builder.append(instruction.toString());
                builder.append("\n");
            }

            ++i;
        }
        return builder.toString();
    }

}