package com.mummyludo.app.storage

import com.mummyludo.app.game.*
import org.json.JSONArray
import org.json.JSONObject

object GameStateCodec {
    fun encode(state: GameState): String {
        val root = JSONObject()

        val seatsArray = JSONArray()
        state.seats.forEach { seat ->
            val seatObj = JSONObject().apply {
                put("color", seat.color.name)
                put("type", seat.type.name)
                put("name", seat.name)
            }
            seatsArray.put(seatObj)
        }
        root.put("seats", seatsArray)

        val tokensArray = JSONArray()
        state.tokens.forEach { token ->
            val tokenObj = JSONObject().apply {
                put("color", token.color.name)
                put("index", token.index)
                put("progress", token.progress)
            }
            tokensArray.put(tokenObj)
        }
        root.put("tokens", tokensArray)

        val pairsArray = JSONArray()
        state.pairs.forEach { pairsArray.put(it) }
        root.put("pairs", pairsArray)

        root.put("activeSeat", state.activeSeat)
        root.put("pendingBonus", state.pendingBonus)
        root.put("consecutiveSixes", state.consecutiveSixes)

        val diceBankArray = JSONArray()
        state.diceBank.forEach { diceBankArray.put(it) }
        root.put("diceBank", diceBankArray)

        root.put("awaitingRoll", state.awaitingRoll)
        if (state.roll != null) {
            root.put("roll", state.roll)
        } else {
            root.put("roll", JSONObject.NULL)
        }

        val rankingArray = JSONArray()
        state.ranking.forEach { rankingArray.put(it.name) }
        root.put("ranking", rankingArray)

        root.put("turn", state.turn)
        root.put("message", state.message)
        root.put("completed", state.completed)

        return root.toString()
    }

    fun decode(jsonString: String): GameState? = runCatching {
        val root = JSONObject(jsonString)

        val seatsArray = root.getJSONArray("seats")
        val seats = mutableListOf<Seat>()
        for (i in 0 until seatsArray.length()) {
            val obj = seatsArray.getJSONObject(i)
            val color = LudoColor.valueOf(obj.getString("color"))
            val type = PlayerType.valueOf(obj.getString("type"))
            val name = obj.getString("name")
            seats.add(Seat(color, type, name))
        }
        require(seats.size in 2..4)

        val tokensArray = root.getJSONArray("tokens")
        val tokens = mutableListOf<Token>()
        for (i in 0 until tokensArray.length()) {
            val obj = tokensArray.getJSONObject(i)
            val color = LudoColor.valueOf(obj.getString("color"))
            val index = obj.getInt("index")
            val progress = obj.getInt("progress")
            require(progress in YARD..FINISHED)
            tokens.add(Token(color, index, progress))
        }
        require(tokens.size == seats.size * 4)

        val pairsArray = root.optJSONArray("pairs") ?: JSONArray()
        val pairs = mutableSetOf<String>()
        for (i in 0 until pairsArray.length()) {
            pairs.add(pairsArray.getString(i))
        }

        val activeSeat = root.getInt("activeSeat")
        require(activeSeat in seats.indices)

        val pendingBonus = root.getInt("pendingBonus")
        val consecutiveSixes = root.getInt("consecutiveSixes")

        val diceBankArray = root.optJSONArray("diceBank") ?: JSONArray()
        val diceBank = mutableListOf<Int>()
        for (i in 0 until diceBankArray.length()) {
            val value = diceBankArray.getInt(i)
            require(value in 1..6)
            diceBank.add(value)
        }

        val awaitingRoll = root.getBoolean("awaitingRoll")
        val roll = if (root.isNull("roll")) null else root.getInt("roll").also { require(it in 1..6) }

        val rankingArray = root.optJSONArray("ranking") ?: JSONArray()
        val ranking = mutableListOf<LudoColor>()
        for (i in 0 until rankingArray.length()) {
            ranking.add(LudoColor.valueOf(rankingArray.getString(i)))
        }

        val turn = root.getInt("turn")
        val message = root.optString("message", "Roll the dice")
        val completed = root.optBoolean("completed", false)

        GameState(
            seats = seats,
            tokens = tokens,
            pairs = pairs,
            activeSeat = activeSeat,
            pendingBonus = pendingBonus,
            consecutiveSixes = consecutiveSixes,
            diceBank = diceBank,
            awaitingRoll = awaitingRoll,
            roll = roll,
            ranking = ranking,
            turn = turn,
            message = message,
            completed = completed,
        )
    }.getOrNull()
}
