package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import com.cblol.scout.domain.usecase.MatchResultData

/** Constrói o Intent para MatchResultActivity a partir de um MatchResultData. */
fun MatchResultData.toResultIntent(context: Context): Intent =
    Intent(context, MatchResultActivity::class.java).apply {
        putExtra(MatchResultActivity.EXTRA_HOME_NAME,    homeName)
        putExtra(MatchResultActivity.EXTRA_AWAY_NAME,    awayName)
        putExtra(MatchResultActivity.EXTRA_HOME_ID,      homeTeamId)
        putExtra(MatchResultActivity.EXTRA_AWAY_ID,      awayTeamId)
        putExtra(MatchResultActivity.EXTRA_HOME_SCORE,   homeScore)
        putExtra(MatchResultActivity.EXTRA_AWAY_SCORE,   awayScore)
        putExtra(MatchResultActivity.EXTRA_WINNER_ID,    winnerId)
        putExtra(MatchResultActivity.EXTRA_MANAGER_ID,   managerId)
        putExtra(MatchResultActivity.EXTRA_HOME_KILLS,   homeKills)
        putExtra(MatchResultActivity.EXTRA_AWAY_KILLS,   awayKills)
        putExtra(MatchResultActivity.EXTRA_HOME_TOWERS,  homeTowers)
        putExtra(MatchResultActivity.EXTRA_AWAY_TOWERS,  awayTowers)
        putExtra(MatchResultActivity.EXTRA_HOME_DRAGONS, homeDragons)
        putExtra(MatchResultActivity.EXTRA_AWAY_DRAGONS, awayDragons)
        putExtra(MatchResultActivity.EXTRA_HOME_BARONS,  homeBarons)
        putExtra(MatchResultActivity.EXTRA_AWAY_BARONS,  awayBarons)
        putExtra(MatchResultActivity.EXTRA_PRIZE,        prize)
    }
