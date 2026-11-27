package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.util.CallerImages
import org.junit.Assert.assertEquals
import org.junit.Test

class CallerImagesTest {
    @Test fun smallImagesAreNotSampled() {
        assertEquals(1, CallerImages.sampleSize(96, 96, 512))
        assertEquals(1, CallerImages.sampleSize(1080, 1920, 1920))
    }

    @Test fun cameraPhotosAreSampledButStayAtLeastTheLimit() {
        // A 12 MP photo for a 720 px caller photo: 4000 / 4 = 1000, 4000 / 8 = 500 would be too small.
        assertEquals(4, CallerImages.sampleSize(4000, 3000, 720))
        assertEquals(2, CallerImages.sampleSize(3000, 4000, 1920))
    }

    @Test fun slotsKeepContactsApart() {
        assertEquals("global-photo", CallerImages.slot(null, "photo"))
        assertEquals("contact-12-background", CallerImages.slot(12, "background"))
    }
}
