package com.cblol.scout.util

import android.content.Context
import com.cblol.scout.data.SnapshotData
import com.google.gson.Gson

/**
 * Carrega o snapshot do CBLOL (times + jogadores) do JSON em assets.
 *
 * Aplica [ChampionPoolRepository.attachAll] aos jogadores, garantindo
 * que cada um tenha um champion pool (mains) atribuído — usado pelo
 * motor de simulação para conceder bônus de força quando o jogador
 * picka um dos seus mains.
 */
object JsonLoader {
    fun loadSnapshot(context: Context): SnapshotData {
        val json = context.assets.open("cblol_jogadores.json")
            .bufferedReader()
            .use { it.readText() }
        val raw = Gson().fromJson(json, SnapshotData::class.java)
        return raw.copy(jogadores = ChampionPoolRepository.attachAll(raw.jogadores))
    }
}
