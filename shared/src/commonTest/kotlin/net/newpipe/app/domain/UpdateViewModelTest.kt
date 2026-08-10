package net.newpipe.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateViewModelTest {
    @Test
    fun detectsNewerReleaseTags() {
        assertTrue(UpdateViewModel.isNewerVersion("v1.1.0", "1.0.0"))
        assertTrue(UpdateViewModel.isNewerVersion("1.0.1", "v1.0.0"))
    }

    @Test
    fun rejectsCurrentOrOlderReleaseTags() {
        assertFalse(UpdateViewModel.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(UpdateViewModel.isNewerVersion("v0.9.9", "1.0.0"))
        assertFalse(UpdateViewModel.isNewerVersion("v1.0.1-beta", "1.0.1"))
    }
}
