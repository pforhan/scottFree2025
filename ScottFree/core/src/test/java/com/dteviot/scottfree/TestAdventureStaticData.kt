package com.dteviot.scottfree

import org.junit.Assert.assertEquals
import org.junit.Test

class TestAdventureStaticData {
  @Test fun testMatchWord() {
    val words = arrayOf<String?>("ANY", "NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN")

    val data = AdventureStaticData(
      1, 1,
      7, 1, 1,
      1, 1,
      1, 1, 1
    )

    assertEquals(1, data.MatchWord("North", words).toLong())
    assertEquals(3, data.MatchWord("east", words).toLong())
    assertEquals(5, data.MatchWord("UP", words).toLong())
    assertEquals(AdventureGame.AnyWord.toLong(), data.MatchWord("unknown", words).toLong())
  }
}
