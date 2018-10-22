package org.ngscript.common;

/**
 * @author wssccc
 */
public enum OpCode {
    COMMENT,
    ADD,
    CLEAR,
    LT,
    POP,
    PEEK,
    TEST,
    EQ,
    DEREF,
    DEC,
    MOV_EXCEPTION_EAX,
    MOV_EAX_EXCEPTION,
    STRING,
    PUSH_ENV,
    MEMBER_REF,
    ARRAY_NEW,
    MOV_EAX,
    NEG,
    PUSH_EIP,
    SET_VAR,
    OBJECT_NEW,
    ARRAY_REF,
    STATIC_FUNC,
    TYPEOF,
    IMPORT_,
    PUSH_EAX,
    MOV,
    DEQUEUE,
    POP_EAX,
    JMP,
    INTEGER,
    INC,
    LE,
    POP_ENV,
    GE,
    POST_DEC,
    JNZ,
    GT,
    POST_INC,
    JZ,
    NEW_CLOSURE,
    DOUBLE_,
    LABEL,
    UNDEFINED,
    ASSIGN,
    BIT_XOR,
    MOD,
    DIV,
    BIT_OR,
    BIT_AND,
    MUL,
    RET,
    VEQ,
    NEW_QUEUE,
    CALL,
    SUB,
    NEQ,
    VNEQ,
    CLEAR_CALL_STACK,
    SAVE_MACHINE_STATE,
    RESTORE_MACHINE_STATE,
    DROP_MACHINE_STATE,
    NEW_OP,
    CLEAR_NULL,


}