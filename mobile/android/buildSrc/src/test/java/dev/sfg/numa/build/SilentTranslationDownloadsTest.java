package dev.sfg.numa.build;

import android.app.DownloadManager;
import org.junit.Test;
import static org.junit.Assert.*;
import org.objectweb.asm.*;

public class SilentTranslationDownloadsTest {
    @Test public void excludesGeneratedResourcesAndUnrelatedLibraries() {
        assertFalse(SilentTranslationDownloads.shouldInstrumentClass("com.google.mlkit.nl.translate.R"));
        assertFalse(SilentTranslationDownloads.shouldInstrumentClass("com.google.mlkit.nl.translate.R$raw"));
        assertFalse(SilentTranslationDownloads.shouldInstrumentClass("com.google.mlkit.nl.translate.BuildConfig"));
        assertFalse(SilentTranslationDownloads.shouldInstrumentClass("com.example.Download"));
        assertTrue(SilentTranslationDownloads.shouldInstrumentClass("com.google.mlkit.nl.translate.internal.zzc"));
    }

    @Test public void hidesOnlyTheNotificationAndKeepsTheDownloadRequest() throws Exception {
        ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = SilentTranslationDownloads.instrument(output);
        visitor.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Download", null, "java/lang/Object", null);
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(Landroid/app/DownloadManager;Landroid/app/DownloadManager$Request;)J", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/DownloadManager", "enqueue",
                "(Landroid/app/DownloadManager$Request;)J", false);
        method.visitInsn(Opcodes.LRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        visitor.visitEnd();
        Class<?> download = new Generated().load(output.toByteArray());
        DownloadManager manager = new DownloadManager();
        DownloadManager.Request request = new DownloadManager.Request();
        Object id = download.getMethod("run", DownloadManager.class, DownloadManager.Request.class)
                .invoke(null, manager, request);
        assertEquals(7L, id);
        assertSame(request, manager.received);
        assertEquals(2, request.visibility);
        assertTrue(request.wifiOnly);
    }

    private static class Generated extends ClassLoader {
        Generated() { super(SilentTranslationDownloadsTest.class.getClassLoader()); }
        Class<?> load(byte[] bytes) { return defineClass("Download", bytes, 0, bytes.length); }
    }
}
