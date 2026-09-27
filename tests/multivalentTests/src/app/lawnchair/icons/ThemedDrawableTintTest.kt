package app.lawnchair.icons

import org.junit.Assert.assertNull
import org.junit.Test

class ThemedDrawableTintTest {
    @Test
    fun missingThemedDrawableIsSkipped() {
        assertNull(tintDrawableIfAvailable(null, 0xff123456.toInt()))
    }
}
