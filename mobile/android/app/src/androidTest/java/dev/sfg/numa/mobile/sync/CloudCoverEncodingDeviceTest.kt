package dev.sfg.numa.mobile.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudCoverEncodingDeviceTest {
    @Test fun convertsPngAndJpegWithoutBlockingOnADefaultZeroSample() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(android.app.Application::class.java, context.applicationContext.javaClass)
        for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG)) {
            val file = File(context.cacheDir, "cloud-cover-regression-${format.name}.cover")
            val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.CYAN)
                file.outputStream().use { assertTrue(bitmap.compress(format, 90, it)) }
                val encoded = encodeCloudCover(file)
                assertEquals("RIFF", String(encoded, 0, 4, Charsets.US_ASCII))
                assertEquals("WEBP", String(encoded, 8, 4, Charsets.US_ASCII))
                file.writeBytes(encoded)
                assertArrayEquals(encoded, encodeCloudCover(file))
                val decoded = checkNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))
                assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
                decoded.recycle()
            } finally { bitmap.recycle(); file.delete() }
        }
    }
}
