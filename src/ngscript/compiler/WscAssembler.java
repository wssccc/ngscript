package ngscript.compiler;

/*
 *  wssccc all rights reserved
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import ngscript.common.Instruction;
import parseroid.parser.AstNode;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscAssembler {

    ArrayList<Instruction> instructions = new ArrayList<Instruction>();
    HashMap<String, Integer> labels = new HashMap<String, Integer>();
    String namespace;

    public WscAssembler(String namespace) {
        this.namespace = namespace;
    }

    public String label(String name, AstNode ast) {
        String label_name = ast.token.type + "_$" + ast.token.line + "_" + name;
        int i;
        if (labels.containsKey(label_name)) {
            //search for distinct label
            i = labels.get(label_name) + 1;
        } else {
            i = 0;
        }
        labels.put(label_name, i);
        return label_name + (i == 0 ? "" : i) + '#' + namespace;
    }

    public void emit(String opr, String param, String param_extend) {
        instructions.add(new Instruction(opr, param, param_extend));
    }

    public void emit(String opr, String param) {
        instructions.add(new Instruction(opr, param));
    }

    public void emit(String opr) {
        instructions.add(new Instruction(opr));
    }

    public static Instruction parseLine(String instruction) {
        int sep = instruction.indexOf(' ');
        if (sep == -1) {
            return new Instruction(instruction);
        } else {
            String op = instruction.substring(0, sep);
            String params = instruction.substring(sep + 1);

            return new Instruction(op, params);

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
        for (Instruction instruction : instructions) {
            if (!instruction.op.equals("//")) {

                builder.append(String.format("%08d", i + offset));
                builder.append('\t');

                builder.append(instruction.toString());
                builder.append("\n");
            } else {
                builder.append("//------------------------------------------------");
                builder.append("\n");
                builder.append(instruction.toString());
                builder.append("\n");
                builder.append("\n");
            }

            ++i;
        }
        return builder.toString();
    }

}
