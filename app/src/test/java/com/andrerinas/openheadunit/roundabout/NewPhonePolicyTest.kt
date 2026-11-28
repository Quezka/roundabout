package com.andrerinas.openheadunit.roundabout

import com.andrerinas.openheadunit.roundabout.system.NewPhonePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class NewPhonePolicyTest {
    @Test fun emptyListStaysEmpty() = assertEquals(emptySet<String>(), NewPhonePolicy.addTo(emptySet(), "AA:BB:CC:DD:EE:FF"))
    @Test fun addsToChosenList() = assertEquals(setOf("11:22:33:44:55:66", "AA:BB:CC:DD:EE:FF"),
        NewPhonePolicy.addTo(setOf("11:22:33:44:55:66"), "aa:bb:cc:dd:ee:ff"))
    @Test fun noDuplicateAcrossCase() = assertEquals(setOf("aa:bb:cc:dd:ee:ff"),
        NewPhonePolicy.addTo(setOf("aa:bb:cc:dd:ee:ff"), "AA:BB:CC:DD:EE:FF"))
}
