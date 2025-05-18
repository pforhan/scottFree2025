package com.dteviot.scottfree

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class TestAdventureFileReader {
  @Test fun testFetchMultiLineString() {
    try {
      val inputStream = javaClass.getResourceAsStream("/testdata00.dat")
      val afr = AdventureFileReader(inputStream!!)
      assertEquals("", afr.fetchString())
      assertEquals("\n\n", afr.fetchString())
      assertEquals(
        "*I'm at the bottom of a very deep chasm. High above me is\na pair of ledges. One has a bricked up window across its face\nthe other faces a Throne-room",
        afr.fetchString()
      )

      assertEquals(
        "\nWelcome to Adventure number: 1 `ADVENTURELAND`.\nIn this Adventure you're to find *TREASURES* & store them away.\n\nTo see how well you're doing say: `SCORE`",
        afr.fetchString()
      )
      assertEquals(
        "\nCheck with your favorite computer dealer for the next Adventure\nprogram: PIRATE ADVENTURE. If they don't carry `ADVENTURE` have\nthem call: 1-305-862-6917 today!\n",
        afr.fetchString()
      )

      assertEquals("*Thick PERSIAN RUG*/RUG/", afr.fetchString())
      assertEquals(17, afr.fetchInt().toLong())
      assertEquals(
        "Sign: `magic word's AWAY! Look la...`\n(Rest of sign is missing!)",
        afr.fetchString()
      )
      assertEquals(18, afr.fetchInt().toLong())

      assertEquals("*STAR/STAR/", afr.fetchString())
      assertEquals(12, afr.fetchInt().toLong())
      assertEquals("Moat", afr.fetchString())
      assertEquals(1, afr.fetchInt().toLong())

      inputStream.close()
    } catch (e: IOException) {
      e.printStackTrace()
      fail("testFetchMultiLineString")
    }
  }
}
