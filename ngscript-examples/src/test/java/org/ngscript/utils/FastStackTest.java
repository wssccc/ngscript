package org.ngscript.utils;

/**
 * @author wssccc
 */
public class FastStackTest {

    public static void main(String[] args) {
        FastStack<Integer> fastStack = new FastStack<>(32);
        fastStack.push(1);
        fastStack.push(2);
        fastStack.pop();
        fastStack.push(3);
        fastStack.push(4);
        fastStack.push(5);
        fastStack.push(6);
        fastStack.push(7);
        fastStack.add(8);
        System.out.println(fastStack);
    }

}