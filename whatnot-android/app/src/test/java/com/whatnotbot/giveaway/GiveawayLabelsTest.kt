package com.whatnotbot.giveaway

import org.junit.Assert.*
import org.junit.Test

class GiveawayLabelsTest {
    @Test fun normalizesAccessibleLabels() {
        assertTrue(GiveawayLabels.isEntry("  ENTER\n Giveaway  "))
        assertTrue(GiveawayLabels.isEntry("Follow and Enter"))
        assertTrue(GiveawayLabels.isEntered("You’re in the Giveaway"))
        assertTrue(GiveawayLabels.isBadge("GIVEAWAY"))
    }

    @Test fun rejectsUnrelatedAndAmbiguousText() {
        for (label in listOf("Enter", "Enter shipping address", "Do not enter giveaway", "Entered")) {
            assertFalse(label, GiveawayLabels.isEntry(label))
        }
        assertFalse(GiveawayLabels.isEntered("You're in"))
        assertFalse(GiveawayLabels.isBadge("Giveaway rules"))
        assertFalse(GiveawayLabels.isEntry(""))
    }
}
