package dev.sfg.numa.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Applies DownloadManager's public silent-download setting only to ML Kit models. */
public final class SilentTranslationDownloads {
    private SilentTranslationDownloads() {}

    public static boolean shouldInstrumentClass(String name) {
        String prefix = "com.google.mlkit.nl.translate.";
        return name.startsWith(prefix) && !name.equals(prefix + "R")
                && !name.startsWith(prefix + "R$") && !name.endsWith(".BuildConfig");
    }

    public static ClassVisitor instrument(ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String descriptor, boolean isInterface) {
                        if (owner.equals("android/app/DownloadManager") && method.equals("enqueue")
                                && descriptor.equals("(Landroid/app/DownloadManager$Request;)J")) {
                            super.visitInsn(Opcodes.DUP);
                            super.visitInsn(Opcodes.ICONST_2); // DownloadManager.Request.VISIBILITY_HIDDEN
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    "android/app/DownloadManager$Request", "setNotificationVisibility",
                                    "(I)Landroid/app/DownloadManager$Request;", false);
                            super.visitInsn(Opcodes.POP);
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                    }
                };
            }
        };
    }
}
