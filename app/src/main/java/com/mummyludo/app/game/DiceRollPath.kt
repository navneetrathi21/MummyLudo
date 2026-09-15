package com.mummyludo.app.game

import kotlin.random.Random

data class DiceRollPath(
    val startX: Float = 24f,
    val startY: Float = 70f,
    val endX: Float = 234f,
    val endY: Float = 88f,
    val arc: Float = 45f,
    val wobble: Float = 12f,
    val phase: Float = 0f,
) {
    fun x(progress: Float): Float {
        val drift = kotlin.math.sin(progress * Math.PI.toFloat() * 4f + phase) * wobble
        return startX + (endX - startX) * progress + drift
    }

    fun y(progress: Float): Float {
        val hop = kotlin.math.sin(progress * Math.PI.toFloat()) * arc
        val jitter = kotlin.math.sin(progress * Math.PI.toFloat() * 9f + phase) * wobble * .45f
        return startY + (endY - startY) * progress - hop + jitter
    }

    fun rotation(progress: Float): Float =
        (progress * 720f) + kotlin.math.sin(progress * Math.PI.toFloat() * 8f + phase) * 18f

    companion object {
        fun random(): DiceRollPath {
            val fromLeft = Random.nextBoolean()
            val startX = if (fromLeft) Random.nextInt(8, 62).toFloat() else Random.nextInt(190, 252).toFloat()
            val endX = if (fromLeft) Random.nextInt(160, 258).toFloat() else Random.nextInt(12, 112).toFloat()
            return DiceRollPath(
                startX = startX,
                startY = Random.nextInt(38, 156).toFloat(),
                endX = endX,
                endY = Random.nextInt(34, 166).toFloat(),
                arc = Random.nextInt(34, 86).toFloat(),
                wobble = Random.nextInt(6, 24).toFloat(),
                phase = Random.nextFloat() * Math.PI.toFloat() * 2f,
            )
        }
    }
}
