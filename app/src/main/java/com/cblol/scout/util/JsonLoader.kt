package com.cblol.scout.util

import android.content.Context
import com.cblol.scout.data.SnapshotData
import com.google.gson.Gson

object JsonLoader {
    fun loadSnapshot(context: Context): SnapshotData {
        val json = context.assets.open("cblol_jogadores.json")
            .bufferedReader()
            .use { it.readText() }
        return Gson().fromJson(json, SnapshotData::class.java)
    }
}
