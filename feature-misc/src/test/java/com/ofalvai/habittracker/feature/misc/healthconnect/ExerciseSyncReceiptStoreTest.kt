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

    @Test fun zeroResultSyncAndLargeValidCountsRoundTrip() {
        val noChanges = ExerciseSyncReceipt(
            finishedAtEpochMillis = 1L, applied = 0, unchanged = 0, failed = 0
        )
        val largeCounts = ExerciseSyncReceipt(
            finishedAtEpochMillis = Long.MAX_VALUE,
            applied = Int.MAX_VALUE, unchanged = Int.MAX_VALUE, failed = Int.MAX_VALUE
        )
        listOf(noChanges, largeCounts).forEach { receipt ->
            assertEquals(receipt, decodeExerciseSyncReceipt(encodeExerciseSyncReceipt(receipt)))
        }
    }

    @Test fun invalidOrLegacyReceiptsAreIgnoredSafely() {
        listOf(
            null, "", "v0|1795270000123|1|2|0",
            "v1|garbage|1|2|0", "v1|0|1|2|0",
            "v1|1795270000123|-1|2|0",
            "v1|1795270000123|1|2",
            "v1|1795270000123|1|2|0|extra",
            "v1|1795270000123|2147483648|2|0",
            "v1|9223372036854775808|1|2|0",
            "v1|-1|1|2|0",
            "v1|1795270000123|1|-1|0",
            "v1|1795270000123|1|2|-1",
            "v1|1795270000123|1.5|2|0"
        ).forEach { assertNull(decodeExerciseSyncReceipt(it)) }
    }
}
