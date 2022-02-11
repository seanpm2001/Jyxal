package io.github.seggan.jyxal.compiler;

import io.github.seggan.jyxal.antlr.VyxalBaseVisitor;
import io.github.seggan.jyxal.antlr.VyxalParser;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/*
Vars:
0: args
1: stack size
2: context variable
3+: for program use
 */
@SuppressWarnings("ConstantConditions")
public final class Compiler extends VyxalBaseVisitor<Void> implements Opcodes {

    private static final Pattern COMPLEX_SEPARATOR = Pattern.compile("°");

    final VyxalParser parser;
    final ClassWriter classWriter;
    private final MethodVisitor clinit;

    private final Set<String> variables = new HashSet<>();
    private final Set<String> contextVariables = new HashSet<>();

    private final Deque<MethodVisitorWrapper> callStack = new ArrayDeque<>();

    private int listCounter = 0;

    private Compiler(VyxalParser parser, ClassWriter classWriter, MethodVisitor clinit) {
        this.parser = parser;
        this.classWriter = classWriter;
        this.clinit = clinit;
    }

    public static byte[] compile(VyxalParser parser, String fileName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V17, ACC_PUBLIC + ACC_FINAL, "jyxal/Main", null, "java/lang/Object", null);

        // add the register
        cw.visitField(
            ACC_PRIVATE + ACC_STATIC + ACC_FINAL,
            "register",
            "Ljava/lang/Object;",
            null,
            null
        ).visitEnd();

        cw.visitSource(fileName, null);

        MethodVisitor mv = cw.visitMethod(
            ACC_STATIC,
            "<clinit>",
            "()V",
            null,
            null
        );
        mv.visitCode();

        mv.visitInsn(ACONST_NULL);
        mv.visitFieldInsn(PUTSTATIC, "jyxal/Main", "register", "Ljava/lang/Object;");
        mv.visitInsn(RETURN);

        Compiler compiler = new Compiler(parser, cw, mv);

        MethodVisitorWrapper main = new MethodVisitorWrapper(cw.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            "main",
            "([Ljava/lang/String;)V",
            null,
            null
        ), 1);
        compiler.callStack.push(main);
        main.visitCode();

        main.visitInsn(ACONST_NULL); // and this one is to keep asm from complaining cc: that dup insn

        compiler.visit(parser.file());

        // finish up clinit
        mv.visitInsn(RETURN);
        mv.visitMaxs(-1, -1); // auto-calculate stack size and number of locals
        mv.visitEnd();

        // finish up main
        Label end = new Label();
        main.visitVarInsn(ILOAD, main.getStackVar());
        main.visitJumpInsn(IFEQ, end);

        main.visitInsn(DUP); // keep the verifier from complaining

        main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        main.visitInsn(SWAP);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);

        main.visitLabel(end);
        main.visitInsn(RETURN);

        main.visitMaxs(-1, -1); // auto-calculate stack size and number of locals
        main.visitEnd();

        return cw.toByteArray();
    }

    @Override
    public Void visitInteger(VyxalParser.IntegerContext ctx) {
        MethodVisitorWrapper mv = callStack.peek();
        AsmHelper.addBigComplex(ctx.getText(), mv);
        return null;
    }

    @Override
    public Void visitComplex(VyxalParser.ComplexContext ctx) {
        MethodVisitorWrapper mv = callStack.peek();
        String[] parts = COMPLEX_SEPARATOR.split(ctx.getText());
        AsmHelper.addBigDecimal(parts[0], mv);
        AsmHelper.addBigDecimal(parts[1], mv);
        mv.visitMethodInsn(
            INVOKESTATIC,
            "runtime/math/BigComplex",
            "valueOf",
            "(Ljava/math/BigDecimal;Ljava/math/BigDecimal;)Lruntime/math/BigComplex;",
            false
        );
        AsmHelper.popOne(mv);

        return null;
    }

    @Override
    public Void visitNormal_string(VyxalParser.Normal_stringContext ctx) {
        MethodVisitorWrapper mv = callStack.peek();
        mv.visitLdcInsn(ctx.any_text().getText());
        AsmHelper.pushOne(mv);
        return null;
    }

    @Override
    public Void visitSingle_char_string(VyxalParser.Single_char_stringContext ctx) {
        MethodVisitorWrapper mv = callStack.peek();
        mv.visitLdcInsn(ctx.getText().substring(1));
        AsmHelper.pushOne(mv);
        return null;
    }

    @Override
    public Void visitDouble_char_string(VyxalParser.Double_char_stringContext ctx) {
        MethodVisitorWrapper mv = callStack.peek();
        mv.visitLdcInsn(ctx.getText().substring(1));
        AsmHelper.pushOne(mv);
        return null;
    }

    @Override
    public Void visitList(VyxalParser.ListContext ctx) {
        MethodVisitorWrapper method = callStack.peek();
        method.visitLdcInsn(ctx.program().size());
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");

        List<VyxalParser.ProgramContext> program = ctx.program();
        for (int i = 0; i < program.size(); i++) {
            VyxalParser.ProgramContext item = program.get(i);
            method.visitInsn(DUP);
            AsmHelper.selectNumberInsn(method, i);

            String methodName = "listInit$" + listCounter++;
            MethodVisitorWrapper mv = new MethodVisitorWrapper(classWriter.visitMethod(
                ACC_PRIVATE | ACC_STATIC,
                methodName,
                "()Ljava/lang/Object;",
                null,
                null
            ), 1);
            mv.visitCode();
            callStack.push(mv);
            visit(item);
            callStack.pop();
            mv.visitInsn(ARETURN);
            mv.visitMaxs(-1, -1); // auto-calculate stack size and number of locals
            mv.visitEnd();

            method.visitMethodInsn(
                INVOKESTATIC,
                "jyxal/Main",
                methodName,
                "()Ljava/lang/Object;",
                false
            );

            method.visitInsn(AASTORE);
        }

        method.visitMethodInsn(
            INVOKESTATIC,
            "runtime/list/JyxalList",
            "create",
            "([Ljava/lang/Object;)Lruntime/list/JyxalList;",
            false
        );
        AsmHelper.pushOne(method);

        return null;
    }

    @Override
    public Void visitVariable_assn(VyxalParser.Variable_assnContext ctx) {
        String name = ctx.variable().getText();
        if (!variables.contains(name)) {
            variables.add(name);

            classWriter.visitField(
                ACC_PRIVATE | ACC_STATIC,
                name,
                "Ljava/lang/Object;",
                null,
                null
            );

            AsmHelper.addBigDecimal("0", clinit);
            clinit.visitFieldInsn(
                PUTSTATIC,
                "jyxal/Main",
                name,
                "Ljava/lang/Object;"
            );
        }

        MethodVisitorWrapper mv = callStack.peek();
        if (ctx.ASSN_SIGN().getText().equals("→")) {
            // set
            mv.visitFieldInsn(
                PUTSTATIC,
                "jyxal/Main",
                name,
                "Ljava/lang/Object;"
            );
            AsmHelper.popOne(mv);
        } else {
            // get
            mv.visitFieldInsn(
                GETSTATIC,
                "jyxal/Main",
                name,
                "Ljava/lang/Object;"
            );
            AsmHelper.pushOne(mv);
        }

        return null;
    }

    @Override
    public Void visitElement(VyxalParser.ElementContext ctx) {
        String element = ctx.getText();
        if (ctx.MODIFIER() != null) {
            element = ctx.MODIFIER().getText() + element;
        }

        MethodVisitorWrapper mv = callStack.peek();
        Element.getByText(element).compile(classWriter, mv);

        return null;
    }

    @Override
    public Void visitWhile_loop(VyxalParser.While_loopContext ctx) {
        Label start = new Label();
        Label end = new Label();
        int childIndex = 0;

        MethodVisitorWrapper mv = callStack.peek();
        mv.visitLabel(start);
        if (ctx.program().size() > 1) {
            // we have a finite loop
            visit(ctx.program(0));
            childIndex = 1;
            mv.visitMethodInsn(
                INVOKESTATIC,
                "runtime/OtherMethods",
                "truthValue",
                "(Ljava/lang/Object;)Z",
                false
            );
            mv.visitJumpInsn(IFEQ, end);
        }

        System.out.println(childIndex);
        visit(ctx.program(childIndex));
        mv.visitJumpInsn(GOTO, start);
        mv.visitLabel(end);

        return null;
    }
}
