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

import org.ngscript.Configuration;
import org.ngscript.parseroid.parser.AstNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author wssccc
 */
public class Compiler {

    private static final Set<String> BINARY_OP = new HashSet<>(Arrays.asList(
            "bit_xor", "bit_or", "bit_and", "eq", "neq", "lt", "gt", "le", "ge",
            "veq", "vneq", "mul", "mod", "div", "add", "sub"
    ));

    Configuration configuration;

    Assembler assembler = new Assembler();
    Scanner scanner;
    int printedLines;

    Deque<String> continueLabels = new ArrayDeque<>();
    Deque<String> breakLabels = new ArrayDeque<>();
    Deque<String> finallyLabels = new ArrayDeque<>();

    public Compiler(Configuration configuration) {
        this.configuration = configuration;
    }

    public List<Instruction> compileCode(AstNode ast, String sourceCode) throws CompilerException {
        assembler.instructions.clear();
        try (Scanner sc = new Scanner(sourceCode)) {
            this.scanner = sc;
            printedLines = 0;
            compile(ast);
            if (configuration.isGenerateDebugInfo()) {
                while (sc.hasNextLine()) {
                    assembler.emit("//", sc.nextLine(), "" + printedLines);
                }
            }
        }
        return assembler.instructions;
    }

    private void generateDebugInfo(AstNode ast) {
        if (configuration.isGenerateDebugInfo()) {
            if (printedLines != ast.token.line) {
                while (printedLines < ast.token.line) {
                    String line = scanner.hasNextLine() ? scanner.nextLine() : "no line";
                    assembler.emit("//", line, "" + printedLines);
                    ++printedLines;
                }
            }
        }
    }

    private void compile(AstNode ast) throws CompilerException {
        if (ast == null) {
            return;
        }
        generateDebugInfo(ast);
        try {
            Method m = this.getClass().getDeclaredMethod("compile_" + ast.token.type, AstNode.class);
            m.invoke(this, ast);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof CompilerException) {
                throw (CompilerException) e.getCause();
            } else {
                throw new RuntimeException(e);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void compile_program(AstNode ast) throws CompilerException {
        for (AstNode content : ast.contents) {
            compile(content);
        }
    }

    private void compile_import_statement(AstNode ast) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < ast.contents.size(); i++) {
            sb.append(ast.contents.get(i).token.value);
        }
        assembler.emit("import_", sb.toString());
    }

    private void compile_expr(AstNode ast) throws CompilerException {
        compile_expr(ast, false);
    }

    private void compile_expr(AstNode ast, boolean byref) throws CompilerException {
        generateDebugInfo(ast);
        String type = ast.token.type;
        if (ast.token.type.equals("expr")) {
            _compile_expr_opr(ast, byref);
        } else if (ast.token.type.equals("NULL")) {
        } else {
            throw new CompilerException(this, "manual dispatch error " + ast);
        }
    }

    private void _compile_expr_opr(AstNode ast, boolean byref) throws CompilerException {
        ArrayList<AstNode> child = ast.contents;
        String header = child.get(0).token.type;
        switch (header) {
            case "assign":
                compile_expr(child.get(1), true);
                assembler.emit("push_eax");
                compile_expr(child.get(2));
                assembler.emit("assign");
                break;
            case "integer":
            case "string":
                assembler.emit(header, child.get(0).token.value);
                break;
            case "double":
                assembler.emit("double_", child.get(0).token.value);
                break;
            case "dot":
                compile_expr(child.get(1));
                assembler.emit("push_eax");
                assembler.emit("string", child.get(2).token.value);
                assembler.emit("member_ref");
                if (!byref) {
                    assembler.emit("deref", "%eax");
                }
                break;
            case "new":
                _compile_funcall(ast);
                assembler.emit("new_op");
                break;
            case "lambda":
                compile_lambda(ast);
                break;
            case "funcall":
                _compile_funcall(ast);
                assembler.emit("pop_env");
                assembler.emit("pop");//pop params
                assembler.emit("pop");//pop function body
                break;
            case "inc":
                compile_expr(child.get(1), true); //param1
                assembler.emit("inc");
                break;
            case "post_inc":
                compile_expr(child.get(1), true); //param1
                assembler.emit("post_inc");
                break;
            case "dec":
                compile_expr(child.get(1), true); //param1
                assembler.emit("dec");
                break;
            case "post_dec":
                compile_expr(child.get(1), true); //param1
                assembler.emit("post_dec");
                break;
            case "undefined":
                assembler.emit("undefined");
                break;
            case "ident":
                if (!byref) {
                    assembler.emit("deref", child.get(0).token.value);
                } else {
                    assembler.emit("mov_eax", child.get(0).token.value);
                }
                break;
            case "var":
                if (!child.get(1).token.type.equals("ident")) {
                    throw new CompilerException(this, "var statement expect an ident");
                }
                assembler.emit("clear", "%eax");
                assembler.emit("set", child.get(1).token.value);
                if (!byref) {
                    assembler.emit("deref", child.get(1).token.value);
                } else {
                    assembler.emit("mov_eax", child.get(1).token.value);
                }
                break;
            case "val":
                if (!child.get(1).token.type.equals("ident")) {
                    throw new CompilerException(this, "val statement expect an ident");
                }
                assembler.emit("clear", "%eax");
                assembler.emit("val", child.get(1).token.value);
                if (!byref) {
                    assembler.emit("deref", child.get(1).token.value);
                } else {
                    assembler.emit("mov_eax", child.get(1).token.value);
                }
                break;
            case "array":
                makeParam2(child);
                assembler.emit("array_ref");
                if (!byref) {
                    assembler.emit("deref", "%eax");
                }
                break;
            case "typeof":
                compile_expr(child.get(1)); //param1
                assembler.emit("typeof");
                break;
            case "null":
                assembler.emit("clear_null", "%eax");
                break;
            case "true":
                assembler.emit("bool", "true");
                break;
            case "false":
                assembler.emit("bool", "false");
                break;
            case "neg":
                compile_expr(child.get(1)); //param1
                assembler.emit("neg");
                break;
            case "cond":
                String falseLabel = assembler.label("falsePart", ast);
                String exitLabel = assembler.label("exitPart", ast);

                compile_expr(child.get(1));
                assembler.emit("jz", falseLabel);
                compile_expr(child.get(2));
                assembler.emit("jmp", exitLabel);
                assembler.emit("label", falseLabel);
                compile_expr(child.get(3));
                assembler.emit("label", exitLabel);
                break;
            case "or": {
                String exit = assembler.label("exit_or", ast);
                compile_expr(child.get(1));
                assembler.emit("jnz", exit);
                compile_expr(child.get(2));
                assembler.emit("label", exit);
                break;
            }
            case "and": {
                String exit = assembler.label("exit_and", ast);
                compile_expr(child.get(1));
                assembler.emit("jz", exit);
                compile_expr(child.get(2));
                assembler.emit("label", exit);
                break;
            }
            case "array_new":
                for (int i = 0; i < child.get(1).contents.size(); i++) {
                    compile_expr(child.get(1).contents.get(i));
                    assembler.emit("push_eax");
                }
                assembler.emit("array_new", child.get(1).contents.size() + "");
                break;
            case "object_new":
                for (int i = 0; i < child.get(1).contents.size(); i++) {
                    assembler.emit("string", child.get(1).contents.get(i).contents.get(0).token.value);
                    assembler.emit("push_eax");
                    compile_expr(child.get(1).contents.get(i).contents.get(1));
                    assembler.emit("push_eax");
                }
                assembler.emit("object_new", child.get(1).contents.size() + "");
                break;
            default:
                if (BINARY_OP.contains(header)) {
                    makeParam2(child);
                    assembler.emit(header);
                } else {
                    throw new CompilerException(this, "Unknown expression " + child);
                }

        }
    }

    void makeParam2(List<AstNode> child) throws CompilerException {
        compile_expr(child.get(1)); //param1
        assembler.emit("push_eax");
        compile_expr(child.get(2)); //param2
    }

    private void compile_function_decl(AstNode ast) throws CompilerException {
        String enter = _compile_function_body(ast);
        assembler.instructions.add(0, new Instruction("static_func", ast.contents.get(0).token.value, enter));
    }

    private String _compile_function_body(AstNode ast) throws CompilerException {
        String exit = assembler.label("func_exit", ast);
        String enter = assembler.label("func_enter", ast);
        assembler.emit("jmp", exit);
        assembler.emit("label", enter);

        AstNode params = ast.getNode("param_list");

        ArrayList<AstNode> contents = params.contents;
        for (int i = 0; i < contents.size(); i++) {
            AstNode content = contents.get(i);
            assembler.emit("pickarg", i + "");
            assembler.emit("set", content.token.value, "%eax");
        }

        compile(ast.getNode("statements"));
        compile(ast.getNode("statement"));
        //default return void
        assembler.emit("clear", "%eax");
        assembler.emit("ret");
        assembler.emit("label", exit);
        return enter;
    }

    private void compile_lambda(AstNode ast) throws CompilerException {
        String enter = _compile_function_body(ast);
        assembler.emit("new_closure", enter);
    }

    private void compile_statements(AstNode ast) throws CompilerException {
        compile_program(ast);
    }

    private void compile_statement(AstNode ast) throws CompilerException {
        compile_program(ast);
    }

    void _compile_nullable_expr(AstNode ast) throws CompilerException {
        if (ast.contents.size() == 1) {
            if (ast.contents.get(0).token.type.equals("NULL")) {
                assembler.emit("integer", "1");
                return;
            }
        }
        compile_expr(ast);
    }

    private void compile_for_block(AstNode ast) throws CompilerException {
        String testLable = assembler.label("test", ast);
        String continueLabel = assembler.label("continue", ast);
        String breakLabel = assembler.label("break", ast);
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        _compile_nullable_expr(ast.contents.get(0));
        assembler.emit("label", testLable);
        _compile_nullable_expr(ast.contents.get(1));
        assembler.emit("jz", breakLabel);
        compile_statements(ast.contents.get(3));
        assembler.emit("label", continueLabel);
        _compile_nullable_expr(ast.contents.get(2));
        assembler.emit("jmp", testLable);
        assembler.emit("label", breakLabel);
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compile_while_block(AstNode ast) throws CompilerException {
        String testLable = assembler.label("test", ast);
        String continueLabel = assembler.label("continue", ast);
        String breakLabel = assembler.label("break", ast);
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);

        assembler.emit("label", testLable);
        compile_expr(ast.contents.get(0));
        assembler.emit("jz", breakLabel);
        compile_statements(ast.contents.get(1));
        assembler.emit("label", continueLabel);
        assembler.emit("jmp", testLable);
        assembler.emit("label", breakLabel);
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compile_switch_block(AstNode ast) throws CompilerException {
        String breakLabel = assembler.label("break", ast);
        breakLabels.push(breakLabel);
        compile_expr(ast.contents.get(0));
        assembler.emit("push_eax");

        String nextBody = null;
        for (int i = 0; i < ast.contents.get(1).contents.size() - 1; i++) {
            String nextLabel = assembler.label("case_" + i, ast);

            compile_expr(ast.contents.get(1).contents.get(i).contents.get(0));
            assembler.emit("eq");
            assembler.emit("jz", nextLabel);
            if (nextBody != null) {
                assembler.emit("label", nextBody);
            }
            nextBody = assembler.label("body_" + i, ast);
            compile_statements(ast.contents.get(1).contents.get(i).contents.get(1));
            assembler.emit("jmp", nextBody);
            assembler.emit("label", nextLabel);
        }
        if (ast.contents.size() == 3) {
            //has default block
            if (nextBody != null) {
                assembler.emit("label", nextBody);
            }
            compile_statements(ast.contents.get(2).contents.get(0));
        }
        breakLabels.pop();
        assembler.emit("label", breakLabel);
    }

    private void compile_try_block(AstNode ast) throws CompilerException {
        String catchLabel = assembler.label("catch", ast);
        String finallyLabel = assembler.label("finally", ast);
        String exitLabel = assembler.label("exit", ast);
        finallyLabels.push(finallyLabel);
        assembler.emit("save_machine_state");
        assembler.emit("test", "%exception");
        assembler.emit("jnz", catchLabel);
        compile_statements(ast.contents.get(0));
        assembler.emit("drop_machine_state");
        assembler.emit("push_eip");
        assembler.emit("jmp", finallyLabel);
        assembler.emit("jmp", exitLabel);
        assembler.emit("label", catchLabel);
        assembler.emit("mov_eax_exception");
        assembler.emit("set", ast.contents.get(1).token.value);
        assembler.emit("clear", "%exception");
        compile_statements(ast.contents.get(2));
        assembler.emit("push_eip");
        assembler.emit("jmp", finallyLabel);
        assembler.emit("jmp", exitLabel);
        assembler.emit("label", finallyLabel);
        if (ast.contents.size() == 4) {
            compile_statements(ast.contents.get(3));
        }
        assembler.emit("clear_call_stack");
        assembler.emit("pop_eax");
        assembler.emit("jmp", "offset", "1");
        assembler.emit("label", exitLabel);
        finallyLabels.pop();
    }

    private void compile_throw_exception(AstNode ast) throws CompilerException {
        compile_expr(ast.contents.get(0));
        assembler.emit("mov_exception_eax");
        assembler.emit("restore_machine_state");
    }

    private void compile_if_block(AstNode ast) throws CompilerException {
        String exitLable = assembler.label("exit", ast);

        compile_expr(ast.contents.get(0));
        assembler.emit("jz", exitLable);
        compile_statements(ast.contents.get(1));

        assembler.emit("label", exitLable);
    }

    private void compile_if_else_block(AstNode ast) throws CompilerException {
        String exitLabel = assembler.label("exit", ast);
        String elseLabel = assembler.label("else", ast);
        compile_expr(ast.contents.get(0));
        assembler.emit("jz", elseLabel);
        compile_statements(ast.contents.get(1));
        assembler.emit("jmp", exitLabel);
        assembler.emit("label", elseLabel);
        compile_statements(ast.contents.get(2));
        assembler.emit("label", exitLabel);
    }

    private void compile_break(AstNode ast) throws CompilerException {
        if (breakLabels.isEmpty()) {
            throw new CompilerException(this, "break without loop");
        }
        assembler.emit("jmp", breakLabels.peek());
    }

    private void compile_continue(AstNode ast) throws CompilerException {
        if (breakLabels.isEmpty()) {
            throw new CompilerException(this, "continue without loop");
        }
        assembler.emit("jmp", continueLabels.peek());
    }

    private void compile_hooked_break(AstNode ast) throws CompilerException {
        _compile_call_finally();
        compile_break(ast);
    }

    private void compile_hooked_continue(AstNode ast) throws CompilerException {
        _compile_call_finally();
        compile_continue(ast);
    }

    private void compile_NULL(AstNode ast) {
    }

    private void compile_return_val(AstNode ast) throws CompilerException {
        compile_expr(ast.contents.get(0));
        assembler.emit("ret");
    }

    private void compile_return_void(AstNode ast) {
        assembler.emit("clear", "%eax");
        assembler.emit("ret");
    }

    private void _compile_call_finally() {
        assembler.emit("push_eip");
        assembler.emit("jmp", finallyLabels.peek());
    }

    private void compile_hooked_return_val(AstNode ast) throws CompilerException {
        _compile_call_finally();
        compile_return_val(ast);
    }

    private void compile_hooked_return_void(AstNode ast) {
        _compile_call_finally();
        compile_return_void(ast);
    }

    private void _compile_funcall(AstNode ast) throws CompilerException {
        //params
        int paramCount = 0;
        ArrayList<AstNode> contents = ast.contents.get(2).contents;
        for (int i = contents.size() - 1; i >= 0; i--) {
            AstNode content = contents.get(i);
            if (!content.token.type.equals("NULL")) {
                ++paramCount;
                compile_expr(content);
                assembler.emit("push_eax");
            }
        }
        assembler.emit("packargs", paramCount + "");
        assembler.emit("push_eax");
        //function body
        compile_expr(ast.contents.get(1));
        assembler.emit("push_eax");

        assembler.emit("push_env");
        assembler.emit("call");
    }
}
