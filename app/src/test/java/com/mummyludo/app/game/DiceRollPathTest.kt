package com.mummyludo.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollPathTest {
    @Test
    fun defaultPathHasFinitePositionsAndRotationThroughoutRoll() {
        val path = DiceRollPath()

        assertEquals(24f, path.x(0f), 0.0001f)
        assertEquals(70f, path.y(0f), 0.0001f)
        assertEquals(0f, path.rotation(0f), 0.0001f)
        assertEquals(234f, path.x(1f), 0.0001f)
        assertEquals(88f, path.y(1f), 0.0001f)
        assertEquals(720f, path.rotation(1f), 0.0001f)

        (0..20).forEach { tick ->
            val progress = tick / 20f
            assertTrue(path.x(progress).isFinite())
            assertTrue(path.y(progress).isFinite())
            assertTrue(path.rotation(progress).isFinite())
        }
        assertTrue(path.y(0.5f) < (path.startY + path.endY) / 2f)
    }

    @Test
    fun randomPathKeepsEveryParameterInItsConfiguredRange() {
        val paths = List(200) { DiceRollPath.random() }

        assertTrue(paths.any { it.startX < it.endX })
        assertTrue(paths.any { it.startX > it.endX })
        paths.forEach { path ->
            val leftToRight = path.startX < path.endX
            assertTrue(path.startX in if (leftToRight) 8f..61f else 190f..251f)
            assertTrue(path.endX in if (leftToRight) 160f..257f else 12f..111f)
            assertTrue(path.startY in 38f..155f)
            assertTrue(path.endY in 34f..165f)
            assertTrue(path.arc in 34f..85f)
            assertTrue(path.wobble in 6f..23f)
            assertTrue(path.phase >= 0f)
            assertTrue(path.phase < Math.PI.toFloat() * 2f)
        }
    }

    @Test
    fun negativeAndPastEndProgressExtrapolateInsteadOfSilentlyClamping() {
        val path = DiceRollPath(startX = 10f, startY = 20f, endX = 30f, endY = 40f, arc = 0f, wobble = 0f)

        assertEquals(-10f, path.x(-1f), 0.0001f)
        assertEquals(0f, path.y(-1f), 0.0001f)
        assertEquals(-720f, path.rotation(-1f), 0.0001f)
        assertEquals(50f, path.x(2f), 0.0001f)
        assertEquals(60f, path.y(2f), 0.0001f)
        assertEquals(1440f, path.rotation(2f), 0.0001f)
    }

    @Test
    fun nonFiniteProgressDoesNotProduceAValidBoardPosition() {
        val path = DiceRollPath()

        assertFalse(path.x(Float.NaN).isFinite())
        assertFalse(path.y(Float.NaN).isFinite())
        assertFalse(path.rotation(Float.NaN).isFinite())
        assertFalse(path.x(Float.POSITIVE_INFINITY).isFinite())
        assertFalse(path.y(Float.NEGATIVE_INFINITY).isFinite())
        assertFalse(path.rotation(Float.POSITIVE_INFINITY).isFinite())
    }
}
