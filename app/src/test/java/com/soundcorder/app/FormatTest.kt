package com.soundcorder.app

import com.soundcorder.app.ui.formatDuration
import com.soundcorder.app.ui.formatSize
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatDuration_underOneHour_hasNoHourField() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:09", formatDuration(9_000L))
        assertEquals("1:07", formatDuration(67_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
    }

    @Test
    fun formatDuration_oneHourAndUp_hasHourField() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:01:01", formatDuration(3_661_000L))
    }

    @Test
    fun formatDuration_negativeClampsToZero() {
        assertEquals("0:00", formatDuration(-5_000L))
    }

    @Test
    fun formatSize_scalesFromKbToMb() {
        assertEquals("0 KB", formatSize(0L))
        assertEquals("2 KB", formatSize(2_048L))
        assertEquals("5.0 MB", formatSize(5L * 1024 * 1024))
    }
}
