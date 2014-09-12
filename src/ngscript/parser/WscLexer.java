/*
 *  wssccc all rights reserved
 */
package ngscript.parser;

import Lexeroid.Lexer;
import Lexeroid.LexerBuilder;
import Lexeroid.Regex.DFA;
import Lexeroid.Regex.Regex;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public final class WscLexer extends Lexer {

    public WscLexer() {
        super(buildLexerDFA());
    }

    static synchronized DFA buildLexerDFA() {
        LexerBuilder builder = new LexerBuilder();
        builder.defineToken("block_comment", Regex.compile("/\\*(([^\\*])|(\\*[^/]))+?(\\*/)"), true);
        builder.defineToken("comment", Regex.compile("//[^\\n]+?"), true);
        builder.defineToken("integer",
                Regex.concat(Regex.digit(), Regex.many(Regex.digit()))
        );
        builder.defineToken("double",
                Regex.concat(
                        Regex.concat(Regex.digit(), Regex.many(Regex.digit())),
                        Regex.chr('.'),
                        Regex.concat(Regex.digit(), Regex.many(Regex.digit())))
        );
        builder.defineToken("blank",
                Regex.concat(
                        Regex.or(Regex.chr(' '), Regex.chr('\t'), Regex.chr('\r'), Regex.chr('\n')),
                        Regex.many(Regex.or(Regex.chr(' '), Regex.chr('\t'), Regex.chr('\r'), Regex.chr('\n')))
                ), true
        );

        builder.defineToken("dot",
                Regex.chr('.')
        );
        builder.defineToken("semicolon",
                Regex.chr(';')
        );
        builder.defineToken("lparen",
                Regex.chr('(')
        );
        builder.defineToken("rparen",
                Regex.chr(')')
        );
        builder.defineToken("lcurly",
                Regex.chr('{')
        );
        builder.defineToken("rcurly",
                Regex.chr('}')
        );
        builder.defineToken("lsqr",
                Regex.chr('[')
        );
        builder.defineToken("rsqr",
                Regex.chr(']')
        );
        builder.defineToken("assign",
                Regex.chr('=')
        );
        builder.defineToken("eq",
                Regex.string("==")
        );
        builder.defineToken("neq",
                Regex.string("!=")
        );
        builder.defineToken("gt",
                Regex.chr('>')
        );
        builder.defineToken("lt",
                Regex.chr('<')
        );
        builder.defineToken("ge",
                Regex.string(">=")
        );
        builder.defineToken("le",
                Regex.string("<=")
        );
        builder.defineToken("comma",
                Regex.chr(',')
        );
        builder.defineToken("or",
                Regex.string("||")
        );
        builder.defineToken("and",
                Regex.string("&&")
        );
        builder.defineToken("colon",
                Regex.chr(':')
        );
        builder.defineToken("question_mark",
                Regex.chr('?')
        );
        builder.defineToken("inc",
                Regex.string("++")
        );
        builder.defineToken("dec",
                Regex.string("--")
        );
        builder.defineToken("add",
                Regex.chr('+')
        );
        builder.defineToken("sub",
                Regex.chr('-')
        );
        builder.defineToken("mul",
                Regex.chr('*')
        );
        builder.defineToken("div",
                Regex.chr('/')
        );
        builder.defineToken("mod",
                Regex.chr('%')
        );

        builder.defineToken("not",
                Regex.chr('!')
        );

        builder.defineToken("string", Regex.compile("\"([^\"\\\\]|\\\\\"|\\\\\\\\|\\\\t|\\\\r|\\\\n)*\""));
        builder.defineToken("var", Regex.string("var"));
        builder.defineToken("true", Regex.string("true"));
        builder.defineToken("false", Regex.string("false"));
        builder.defineToken("null", Regex.string("null"));
        builder.defineToken("undefined", Regex.string("undefined"));
        builder.defineToken("import", Regex.string("import"));
        builder.defineToken("function", Regex.string("function"));
        builder.defineToken("new", Regex.string("new"));
        builder.defineToken("if", Regex.string("if"));
        builder.defineToken("return", Regex.string("return"));
        builder.defineToken("break", Regex.string("break"));
        builder.defineToken("continue", Regex.string("continue"));
        builder.defineToken("while", Regex.string("while"));
        builder.defineToken("switch", Regex.string("switch"));
        builder.defineToken("case", Regex.string("case"));
        builder.defineToken("default", Regex.string("default"));
        builder.defineToken("typeof", Regex.string("typeof"));
        builder.defineToken("try", Regex.string("try"));
        builder.defineToken("catch", Regex.string("catch"));
        builder.defineToken("finally", Regex.string("finally"));
        builder.defineToken("throw", Regex.string("throw"));
        builder.defineToken("for", Regex.string("for"));
        builder.defineToken("else", Regex.string("else"));
        builder.defineToken("ident", Regex.concat(
                Regex.or(Regex.letter(), Regex.chr('_')),
                Regex.many(Regex.or(Regex.or(Regex.letter(), Regex.chr('_')), Regex.digit()))
        ));
        return builder.buildDFA();
    }
}
