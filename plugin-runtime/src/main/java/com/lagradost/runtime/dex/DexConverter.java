package com.lagradost.runtime.dex;

import com.googlecode.d2j.Method;
import com.googlecode.d2j.converter.IR2JConverter;
import com.googlecode.d2j.dex.ClassVisitorFactory;
import com.googlecode.d2j.dex.Dex2Asm.ClzCtx;
import com.googlecode.d2j.dex.DexExceptionHandler;
import com.googlecode.d2j.dex.ExDex2Asm;
import com.googlecode.d2j.dex.LambadaNameSafeClassAdapter;
import com.googlecode.d2j.node.DexAnnotationNode;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.DexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.ts.AggTransformer;
import com.googlecode.dex2jar.ir.ts.CleanLabel;
import com.googlecode.dex2jar.ir.ts.DeadCodeTransformer;
import com.googlecode.dex2jar.ir.ts.ExceptionHandlerTrim;
import com.googlecode.dex2jar.ir.ts.MultiArrayTransformer;
import com.googlecode.dex2jar.ir.ts.NewTransformer;
import com.googlecode.dex2jar.ir.ts.NpeTransformer;
import com.googlecode.dex2jar.ir.ts.RemoveConstantFromSSA;
import com.googlecode.dex2jar.ir.ts.RemoveLocalFromSSA;
import com.googlecode.dex2jar.ir.ts.Transformer;
import com.googlecode.dex2jar.ir.ts.TypeTransformer;
import com.googlecode.dex2jar.ir.ts.VoidInvokeTransformer;
import com.googlecode.dex2jar.ir.ts.ZeroTransformer;
import com.googlecode.dex2jar.ir.ts.array.FillArrayTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converts Dalvik bytecode (a .dex, or an extension/apk zip containing classes*.dex) into a jar of
 * JVM classes, so Android extensions can be loaded by a normal class loader.
 * <p>
 * Uses dex2jar's reader, IR and optimizer (the same passes as the d2j-dex2jar command), with the
 * SSA destruction and register allocation passes replaced by memory efficient equivalents.
 */
public final class DexConverter {

    /** Bump when the conversion output changes, to invalidate cached jars */
    public static final int VERSION = 6;

    /** Test switch: aggressive register allocation for every method, not only oversized ones */
    public static final boolean FORCE_AGGRESSIVE = Boolean.getBoolean("cloudstream.dex.aggressive");

    /** JVM limit for the code of one method */
    private static final int MAX_CODE_SIZE = 65535;

    private DexConverter() {
    }

    public static final class Result {
        /** Methods that could not be translated; they throw an error when called */
        public final List<String> failedMethods = Collections.synchronizedList(new ArrayList<>());
        public final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        /** Methods over the JVM code size limit that fit after aggressive register allocation */
        public final List<String> compactedMethods = Collections.synchronizedList(new ArrayList<>());
        public int classCount;
        public long millis;

        public boolean isClean() {
            return failedMethods.isEmpty() && errors.isEmpty();
        }
    }

    /**
     * Immutable metadata describing the enclosing method of a local or anonymous class per JVMS §4.7.7.
     */
    private static final class EnclosingMethod {
        final String owner;
        final String name;
        final String desc;

        EnclosingMethod(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }
    }

    /**
     * Scans Dalvik annotations for local classes (classes declared within functions):
     * maps internal class name -> enclosing function signature.
     * Restores EnclosingMethod and normalizes InnerClasses omitted by standard dex2jar.
     */
    private static Map<String, EnclosingMethod> scanLocalClasses(DexFileNode dexFile) {
        if (dexFile.clzs == null || dexFile.clzs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, EnclosingMethod> localClasses = new HashMap<>();
        for (DexClassNode classNode : dexFile.clzs) {
            if (classNode.anns == null) continue;
            Method enclosing = null;
            boolean isNamedInner = false;
            for (DexAnnotationNode ann : classNode.anns) {
                if (ann.items == null) continue;
                if ("Ldalvik/annotation/EnclosingMethod;".equals(ann.type)) {
                    for (DexAnnotationNode.Item item : ann.items) {
                        if ("value".equals(item.name) && item.value instanceof Method) {
                            enclosing = (Method) item.value;
                        }
                    }
                } else if ("Ldalvik/annotation/InnerClass;".equals(ann.type)) {
                    for (DexAnnotationNode.Item item : ann.items) {
                        if ("name".equals(item.name) && item.value != null) {
                            isNamedInner = true;
                        }
                    }
                }
            }
            if (enclosing != null && isNamedInner) {
                String className = cleanDescriptor(classNode.className);
                String owner = cleanDescriptor(enclosing.getOwner());
                localClasses.put(className, new EnclosingMethod(owner, enclosing.getName(), enclosing.getDesc()));
            }
        }
        return localClasses;
    }

    private static String cleanDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) return "";
        return desc.startsWith("L") && desc.endsWith(";") ? desc.substring(1, desc.length() - 1) : desc;
    }

    /**
     * ASM adapter that ensures compliance with JVMS §4.7.6 (InnerClasses) and §4.7.7 (EnclosingMethod).
     */
    private static final class LocalClassAttributeAdapter extends ClassVisitor {
        private final Map<String, EnclosingMethod> localClasses;

        LocalClassAttributeAdapter(ClassVisitor downstream, Map<String, EnclosingMethod> localClasses) {
            super(Opcodes.ASM9, downstream);
            this.localClasses = localClasses;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            EnclosingMethod enc = localClasses.get(name);
            if (enc != null) {
                super.visitOuterClass(enc.owner, enc.name, enc.desc);
            }
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // JVMS §4.7.6: For local classes, outer_class_info_index must be zero (outerName = null)
            String targetOuter = localClasses.containsKey(name) ? null : outerName;
            super.visitInnerClass(name, targetOuter, innerName, access);
        }
    }

    public static Result convert(File input, File outJar) throws IOException {
        return convert(Files.readAllBytes(input.toPath()), outJar);
    }

    /**
     * @param dexOrZip a dex file, or a zip (cs3/apk/jar) containing classes.dex, classes2.dex, ...
     */
    public static Result convert(byte[] dexOrZip, File outJar) throws IOException {
        return convert(dexOrZip, outJar, null);
    }

    /**
     * @param extraEntries files copied into the jar as is (the non dex content of the container, so
     *                     class loader resources such as manifest.json work like on Android)
     */
    public static Result convert(byte[] dexOrZip, File outJar, Map<String, byte[]> extraEntries) throws IOException {
        long start = System.currentTimeMillis();
        final Result result = new Result();
        final Map<String, byte[]> classes = new LinkedHashMap<>();

        BaseDexFileReader reader = MultiDexFileReader.open(dexOrZip);
        DexFileNode fileNode = new DexFileNode();
        try {
            // Names are kept verbatim: ART accepts "-" in names and Kotlin relies on it (e.g. Result.constructor-impl)
            reader.accept(fileNode, DexFileReader.SKIP_DEBUG | DexFileReader.IGNORE_READ_EXCEPTION | DexFileReader.DONT_SANITIZE_NAMES);
        } catch (Exception ex) {
            result.errors.add("read: " + ex);
        }

        final Map<String, EnclosingMethod> localClasses = scanLocalClasses(fileNode);

        ClassVisitorFactory cvf = name -> {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            final LambadaNameSafeClassAdapter rca = new LambadaNameSafeClassAdapter(cw, true);
            ClassVisitor target = localClasses.isEmpty() ? rca : new LocalClassAttributeAdapter(rca, localClasses);
            return new ClassVisitor(Opcodes.ASM9, target) {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    String className = rca.getClassName();
                    try {
                        classes.put(className, cw.toByteArray());
                    } catch (Exception ex) {
                        result.errors.add("class " + className + ": " + ex);
                    }
                }
            };
        };

        DexExceptionHandler handler = new DexExceptionHandler() {
            @Override
            public void handleFileException(Exception e) {
                result.errors.add("file: " + e);
            }

            @Override
            public void handleMethodTranslateException(Method method, DexMethodNode methodNode, MethodVisitor mv,
                                                       Exception e) {
                String where = method.getOwner() + "->" + method.getName() + method.getDesc();
                result.failedMethods.add(where + " : " + e);
                String msg = "CloudStream desktop could not translate " + where + ": " + e;
                if (msg.length() > 4000) {
                    msg = msg.substring(0, 4000);
                }
                mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(msg);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>",
                        "(Ljava/lang/String;)V", false);
                mv.visitInsn(Opcodes.ATHROW);
            }
        };

        new ExDex2Asm(handler) {
            /** register allocation mode of the method being translated */
            private boolean aggressive = FORCE_AGGRESSIVE;
            private final Transformer cleanLabel = new CleanLabel();
            private final Transformer deadCode = new DeadCodeTransformer();
            private final Transformer removeLocal = new RemoveLocalFromSSA();
            private final Transformer removeConst = new RemoveConstantFromSSA();
            private final Transformer zero = new ZeroTransformer();
            private final NpeTransformer npe = new NpeTransformer();
            private final Transformer newT = new NewTransformer();
            private final Transformer fillArray = new FillArrayTransformer();
            private final Transformer agg = new AggTransformer();
            private final Transformer multiArray = new MultiArrayTransformer();
            private final Transformer voidInvoke = new VoidInvokeTransformer();
            private final Transformer type = new TypeTransformer();
            private final Transformer trimEx = new ExceptionHandlerTrim();

            @Override
            public void optimize(IrMethod irMethod) {
                cleanLabel.transform(irMethod);
                deadCode.transform(irMethod);
                removeLocal.transform(irMethod);
                removeConst.transform(irMethod);
                zero.transform(irMethod);
                if (npe.transformReportChanged(irMethod)) {
                    deadCode.transform(irMethod);
                    removeLocal.transform(irMethod);
                    removeConst.transform(irMethod);
                }
                newT.transform(irMethod);
                fillArray.transform(irMethod);
                agg.transform(irMethod);
                multiArray.transform(irMethod);
                voidInvoke.transform(irMethod);
                // https://github.com/pxb1988/dex2jar/issues/477 dead code found in unssa, clean up
                deadCode.transform(irMethod);
                removeLocal.transform(irMethod);
                removeConst.transform(irMethod);
                type.transform(irMethod);
                new LowMemUnSSATransformer().transform(irMethod);
                new LowMemRegAssignTransformer(aggressive).transform(irMethod);
                trimEx.transform(irMethod);
            }

            /**
             * Same as ExDex2Asm, except that a method over the JVM's 64KB code limit is translated again
             * with aggressive copy coalescing (Android has no such limit, dex code is much denser).
             */
            @Override
            public void convertCode(DexMethodNode methodNode, MethodVisitor mv, ClzCtx clzCtx) {
                MethodNode mn = translate(methodNode, clzCtx, FORCE_AGGRESSIVE);
                int size = codeSize(mn);
                if (size > MAX_CODE_SIZE && !FORCE_AGGRESSIVE) {
                    MethodNode compact = translate(methodNode, clzCtx, true);
                    int compactSize = codeSize(compact);
                    String where = methodNode.method.getOwner() + "->" + methodNode.method.getName()
                            + methodNode.method.getDesc();
                    if (compactSize <= MAX_CODE_SIZE) {
                        result.compactedMethods.add(where + " : " + size + " -> " + compactSize + " bytes");
                        mn = compact;
                        size = compactSize;
                    } else {
                        size = compactSize;
                    }
                }
                if (size > MAX_CODE_SIZE) {
                    mn = new MethodNode(Opcodes.ASM9, methodNode.access, methodNode.method.getName(),
                            methodNode.method.getDesc(), null, null);
                    exceptionHandler.handleMethodTranslateException(methodNode.method, methodNode, mn,
                            new RuntimeException("method code is too large for the JVM (" + size + " bytes)"));
                }
                mn.accept(mv);
            }

            private MethodNode translate(DexMethodNode methodNode, ClzCtx clzCtx, boolean aggressiveMode) {
                MethodNode mn = new MethodNode(Opcodes.ASM9, methodNode.access, methodNode.method.getName(),
                        methodNode.method.getDesc(), null, null);
                aggressive = aggressiveMode;
                try {
                    IrMethod irMethod = dex2ir(methodNode);
                    optimize(irMethod);
                    ir2j(irMethod, mn, clzCtx);
                } catch (Exception ex) {
                    mn.instructions.clear();
                    mn.tryCatchBlocks.clear();
                    exceptionHandler.handleMethodTranslateException(methodNode.method, methodNode, mn, ex);
                } finally {
                    aggressive = FORCE_AGGRESSIVE;
                }
                return mn;
            }

            @Override
            public void ir2j(IrMethod irMethod, MethodVisitor mv, ClzCtx clzCtx) {
                new IR2JConverter()
                        .optimizeSynchronized(false)
                        .clzCtx(clzCtx)
                        .ir(irMethod)
                        .asm(mv)
                        .convert();
            }
        }.convertDex(fileNode, cvf);

        File parent = outJar.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File tmp = new File(outJar.getPath() + ".tmp");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
            if (extraEntries != null) {
                for (Map.Entry<String, byte[]> e : extraEntries.entrySet()) {
                    String key = e.getKey();
                    if (key.endsWith(".class") && classes.containsKey(key.substring(0, key.length() - 6))) continue;
                    zos.putNextEntry(new ZipEntry(e.getKey()));
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            }
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey() + ".class"));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        try {
            Files.move(tmp.toPath(), outJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            try {
                tmp.delete();
            } catch (Throwable ignored) {}
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException(t);
            }
        }
        result.classCount = classes.size();
        result.millis = System.currentTimeMillis() - start;
        return result;
    }

    /**
     * Size of the method's code as the JVM will see it: written alone into a class, including the
     * goto_w expansion of long jumps, plus one byte per ldc that could become ldc_w in the real class.
     */
    static int codeSize(MethodNode mn) {
        if (mn.instructions.size() == 0) {
            return 0;
        }
        int ldc = 0;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) insn).cst;
                if (!(cst instanceof Long) && !(cst instanceof Double)) {
                    ldc++;
                }
            }
        }
        ClassWriter probe = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        probe.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "cloudstream/SizeProbe", null, "java/lang/Object", null);
        mn.accept(probe);
        probe.visitEnd();
        int size;
        try {
            size = codeLength(probe.toByteArray());
        } catch (MethodTooLargeException e) {
            size = Math.max(e.getCodeSize(), MAX_CODE_SIZE + 1);
        } catch (RuntimeException e) {
            size = 0; // not a size problem, the real class writer reports it
        } finally {
            // the labels were resolved against the probe, the real writer needs fresh ones
            mn.instructions.resetLabels();
        }
        return size + ldc;
    }

    /** code_length of the first method with code in a class file */
    private static int codeLength(byte[] b) {
        try {
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(b));
            in.skipBytes(8);
            int cpCount = in.readUnsignedShort();
            String[] utf = new String[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: utf[i] = in.readUTF(); break;
                    case 3: case 4: in.skipBytes(4); break;
                    case 5: case 6: in.skipBytes(8); i++; break;
                    case 7: case 8: case 16: case 19: case 20: in.skipBytes(2); break;
                    case 9: case 10: case 11: case 12: case 17: case 18: in.skipBytes(4); break;
                    case 15: in.skipBytes(3); break;
                    default: return 0;
                }
            }
            in.skipBytes(6);
            in.skipBytes(2 * in.readUnsignedShort()); // interfaces
            int fields = in.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                in.skipBytes(6);
                int ac = in.readUnsignedShort();
                for (int a = 0; a < ac; a++) {
                    in.skipBytes(2);
                    in.skipBytes(in.readInt());
                }
            }
            int methods = in.readUnsignedShort();
            for (int i = 0; i < methods; i++) {
                in.skipBytes(6);
                int ac = in.readUnsignedShort();
                for (int a = 0; a < ac; a++) {
                    String name = utf[in.readUnsignedShort()];
                    int len = in.readInt();
                    if ("Code".equals(name)) {
                        in.skipBytes(4);
                        return in.readInt();
                    }
                    in.skipBytes(len);
                }
            }
        } catch (IOException ignored) {
        }
        return 0;
    }

    /** Command line entry for testing: DexConverter in.(dex|cs3) out.jar */
    public static void main(String[] args) throws Exception {
        Result r = convert(new File(args[0]), new File(args[1]));
        System.out.println("classes=" + r.classCount + " time=" + r.millis + "ms failed=" + r.failedMethods.size()
                + " errors=" + r.errors.size() + " compacted=" + r.compactedMethods.size());
        for (String f : r.compactedMethods) {
            System.out.println("COMPACTED " + f);
        }
        for (String f : r.failedMethods) {
            System.out.println("FAILED " + f);
        }
        for (String f : r.errors) {
            System.out.println("ERROR " + f);
        }
    }
}
