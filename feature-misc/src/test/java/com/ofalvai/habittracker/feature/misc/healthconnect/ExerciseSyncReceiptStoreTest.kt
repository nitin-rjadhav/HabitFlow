package com.ofalvai.habittracker.feature.misc.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseSyncReceiptStoreTest {
    @Test fun roundTripPreservesMetadata() {
        val receipt = ExerciseSyncReceipt(
            finishedAtEpochMillis = 1795270000123L,
            applied = 2, unchanged = 3, failed = 1
        )
        assertEquals(receipt, decodeExerciseSyncReceipt(encodeExerciseSyncReceipt(receipt)))
    }

    @Test fun invalidOrLegacyReceiptsAreIgnoredSafely() {
        listOf(
            null, "", "v0|1795270000123|1|2|0",
            "v1|garbage|1|2|0", "v1|0|1|2|0",
            "v1|1795270000123|-1|2|0",
            "v1|1795270000123|1|2",
            "v1|1795270000123|1|2|0|extra"
        ).forEach { assertNull(decodeExerciseSyncReceipt(it)) }
    }
}
