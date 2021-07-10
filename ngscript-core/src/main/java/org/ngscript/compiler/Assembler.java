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

package org.ngscript.compiler;

import org.ngscript.parseroid.parser.AstNode;

import java.util.*;

/**
 * @author wssccc
 */
public class Assembler {

    List<Instruction> instructions = new ArrayList<>();
    Map<String, Integer> labels = new HashMap<>();

    public void doOptimize() {
        this.instructions = optimize(instructions);
    }

    public List<Instruction> optimize(List<Instruction> ins2) {
        ins2.add(new Instruction("", "", ""));
        List<Instruction> ins = new ArrayList<>();
        for (int i = 0; i < ins2.size() - 1; i++) {
            Instruction instruction = ins2.get(i);
            Instruction next = ins2.get(i + 1);
            if ("push_eax".equals(next.op)) {
                instruction.op += "_pe";
                ++i;
            }
            ins.add(instruction);
        }
        return ins;
    }

    public String label(String name, AstNode ast) {
        //TODO: use relocation
        String labelName = ast.token.type + "_$" + ast.token.line + "_" + name;
        int i;
        if (labels.containsKey(labelName)) {
            //search for distinct label
            i = labels.get(labelName) + 1;
        } else {
            i = 0;
        }
        labels.put(labelName, i);
        return labelName + (i == 0 ? "" : i);
    }

    public void emit(String opr, String param, String paramEx) {
        instructions.add(new Instruction(opr, param, paramEx));
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
            Instruction i = Assembler.parseLine(sc.nextLine());
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
            } else {
                if (type == 0) {
                    builder.append("//------------------------------------------------");
                    builder.append("\n");
                }
                type = 1;
                builder.append(instruction.toString());
            }

            ++i;
        }
        return builder.toString();
    }

}
