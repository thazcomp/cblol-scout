package com.cblol.scout.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.cblol.scout.R
import com.cblol.scout.data.Champion
import com.cblol.scout.data.TeamComposition
import com.cblol.scout.util.ChampionRepository
import com.cblol.scout.util.CompositionRepository

/**
 * Dialog de AJUDA do Pick & Ban acionado pelo botão "?".
 *
 * Sugere composições de time ao treinador. O comportamento depende do estado
 * do draft no momento em que o botão é tocado:
 *
 *  - **Já há picks do jogador** → mostra TODAS as composições que contêm pelo
 *    menos um dos campeões já pegos (não só as que atingiram o mínimo), de modo
 *    que logo no 1º pick o treinador já vê quais comps aquele campeão pode
 *    formar. Comps com campeão-chave banido são excluídas. Cada card mostra o
 *    progresso (% montada) e destaca os campeões já pegos vs. os que faltam.
 *
 *  - **Primeiro pick (nenhum pick ainda)** → mostra as composições mais fortes
 *    do meta que ainda são viáveis, ou seja, NÃO neutralizadas pelos
 *    banimentos atuais (via [CompositionRepository.compsNeutralizedBy]).
 *    Ordenadas por tier (S→A→B) e bônus de força.
 *
 * Cada card lista NOME + BÔNUS + TIER da composição e os campeões-chave com
 * NOME + IMAGEM (carregada via Glide, mesmo padrão do [PickSuggestionAdapter]).
 *
 * **SOLID**:
 *  - **SRP**: a Activity só dispara `show(...)`; toda a lógica de seleção de
 *    comps mora no [CompositionRepository] (já existente). Este dialog apenas
 *    *apresenta* — não recalcula bônus nem conhece regras de simulação.
 *  - **OCP**: novas comps entram pelo catálogo do repositório sem tocar aqui;
 *    novo tier = só mapear a cor em [tierColorRes].
 *  - **DIP**: depende de abstrações utilitárias ([CompositionRepository],
 *    [ChampionRepository]) e não de Activities concretas.
 */
object CompSuggestionDialog {

    /** Quantas composições no máximo listar no modo "primeiro pick" (mais fortes). */
    private const val TOP_COMPS_LIMIT = 6

    /** Altura máxima da lista scrollável, em dp (limita o tamanho do dialog). */
    private const val MAX_LIST_HEIGHT_DP = 420

    /**
     * Exibe o dialog de sugestão de composições.
     *
     * @param activity contexto para inflar e mostrar o dialog
     * @param picks    picks atuais do TIME DO JOGADOR (ids de campeão)
     * @param bans     todos os bans da partida (azul + vermelho), ids de campeão
     */
    fun show(activity: Activity, picks: List<String>, bans: List<String>) {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_comp_suggestions, null)

        val tvHeader = view.findViewById<TextView>(R.id.tv_comp_help_header)
        val tvEmpty  = view.findViewById<TextView>(R.id.tv_comp_help_empty)
        val llList   = view.findViewById<LinearLayout>(R.id.ll_comp_list)

        val hasPicks = picks.isNotEmpty()
        tvHeader.setText(
            if (hasPicks) R.string.comp_help_header_forming
            else R.string.comp_help_header_top
        )

        // Seleção das composições a exibir conforme o estado do draft.
        // Cada entrada carrega a comp + (opcionalmente) o progresso/bônus quando
        // derivada de picks já feitos.
        val entries: List<CompEntry> =
            if (hasPicks) formingComps(picks, bans)
            else topComps(bans)

        if (entries.isEmpty()) {
            llList.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            llList.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            entries.forEach { entry ->
                llList.addView(buildCompCard(activity, llList, entry))
            }
        }

        limitListHeight(activity, view)

        stylizedDialog(activity)
            .setTitle(R.string.comp_help_title)
            .setView(view)
            .setPositiveButton(R.string.comp_help_close, null)
            .show()
    }

    // ── Seleção de composições ──────────────────────────────────────────

    /**
     * Composições que incluem os campeões já escolhidos pelo jogador.
     *
     * Diferente de [CompositionRepository.analyzeAll] (que só retorna comps que
     * já atingiram o `minRequired`), aqui mostramos QUALQUER composição que
     * contenha pelo menos um dos picks atuais — assim, logo no primeiro pick,
     * o treinador já vê todas as comps que aquele campeão pode formar.
     *
     * Regras:
     *  - exclui comps com campeão-chave banido (não adianta sugerir comp morta);
     *  - conta quantos dos picks atuais entram em `requiredPicks` da comp;
     *  - ordena por nº de picks casados (mais montada primeiro), depois por
     *    tier (S→A→B) e bônus de força;
     *  - calcula a % montada e o bônus efetivo proporcional aos picks casados.
     */
    private fun formingComps(picks: List<String>, bans: List<String>): List<CompEntry> {
        val picksLower = picks.map { it.lowercase() }
        val bansLower  = bans.map { it.lowercase() }

        return CompositionRepository.all
            .mapNotNull { comp ->
                // Comp neutralizada por ban de campeão-chave: ignora.
                val keyBanned = comp.keyChampions.any { it.lowercase() in bansLower }
                if (keyBanned) return@mapNotNull null

                // Campeões já pegos que pertencem a esta comp.
                val required = comp.requiredPicks.map { it.lowercase() }
                val matchedLower = required.filter { it in picksLower }
                if (matchedLower.isEmpty()) return@mapNotNull null  // nenhum pick casa → fora

                // Nomes originais (preservando a grafia dos picks do jogador).
                val matchedOriginal = picks.filter { it.lowercase() in matchedLower }

                // % montada e bônus proporcional ao quanto da comp já temos.
                val percent = (matchedLower.size.toFloat() /
                    comp.requiredPicks.size * 100).toInt()
                val bonus = when {
                    matchedLower.size >= comp.requiredPicks.size -> comp.bonusStrength
                    matchedLower.size >= comp.minRequired        -> comp.bonusStrength / 2
                    else -> comp.bonusStrength * matchedLower.size / comp.requiredPicks.size
                }

                CompEntry(
                    composition  = comp,
                    bonus        = bonus,
                    percent      = percent,
                    matchedPicks = matchedOriginal,
                    matchedCount = matchedLower.size
                )
            }
            .sortedWith(
                compareByDescending<CompEntry> { it.matchedCount }
                    .thenByDescending { tierRank(it.composition.tier) }
                    .thenByDescending { it.composition.bonusStrength }
            )
            .take(TOP_COMPS_LIMIT)
    }

    /**
     * Composições mais fortes do meta para um primeiro pick, considerando os
     * banimentos: remove as comps neutralizadas (campeão-chave banido) e ordena
     * por tier (S→A→B) e então por bônus.
     */
    private fun topComps(bans: List<String>): List<CompEntry> {
        val neutralizedIds = CompositionRepository.compsNeutralizedBy(bans)
            .map { it.id }
            .toSet()

        return CompositionRepository.all
            .filter { it.id !in neutralizedIds }
            .sortedWith(
                compareByDescending<TeamComposition> { tierRank(it.tier) }
                    .thenByDescending { it.bonusStrength }
            )
            .take(TOP_COMPS_LIMIT)
            .map { comp ->
                CompEntry(
                    composition  = comp,
                    bonus        = comp.bonusStrength,
                    percent      = null,           // sem picks → sem progresso
                    matchedPicks = emptyList()
                )
            }
    }

    /** Rank numérico do tier para ordenação (S melhor que A melhor que B). */
    private fun tierRank(tier: String): Int = when (tier.uppercase()) {
        "S" -> 3; "A" -> 2; "B" -> 1; else -> 0
    }

    // ── Construção visual dos cards ─────────────────────────────────────

    /** Constrói o card de uma composição (infla item_comp_suggestion + preenche). */
    private fun buildCompCard(activity: Activity, parent: ViewGroup, entry: CompEntry): View {
        val comp = entry.composition
        val card = LayoutInflater.from(activity)
            .inflate(R.layout.item_comp_suggestion, parent, false)

        val tierColor = ContextCompat.getColor(activity, tierColorRes(comp.tier))

        // Barra lateral + badge de tier tingidos pela cor do tier
        card.findViewById<View>(R.id.view_comp_tier_bar).setBackgroundColor(tierColor)

        card.findViewById<TextView>(R.id.tv_comp_name).text = comp.name

        val tvTier = card.findViewById<TextView>(R.id.tv_comp_tier)
        tvTier.text = activity.getString(R.string.comp_help_tier_label, comp.tier)
        tvTier.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TIER_BADGE_CORNER_PX
            setColor(tierColor)
        }

        card.findViewById<TextView>(R.id.tv_comp_bonus).text =
            activity.getString(R.string.comp_help_bonus_label, entry.bonus)

        card.findViewById<TextView>(R.id.tv_comp_description).text = comp.description

        // Progresso (% montada) só aparece quando derivado de picks reais
        val tvProgress = card.findViewById<TextView>(R.id.tv_comp_progress)
        if (entry.percent != null) {
            tvProgress.visibility = View.VISIBLE
            tvProgress.text = activity.getString(R.string.comp_help_progress_label, entry.percent)
        } else {
            tvProgress.visibility = View.GONE
        }

        // Campeões do card: imagem + nome. Os já-pegos pelo jogador aparecem
        // PRIMEIRO e em destaque (alpha cheio); os sugeridos (ainda não pegos)
        // vêm depois, levemente esmaecidos para indicar "opções que faltam".
        val container = card.findViewById<LinearLayout>(R.id.ll_comp_champions)
        container.removeAllViews()
        championsToShow(comp, entry.matchedPicks).forEach { champ ->
            val alreadyPicked = entry.matchedPicks.any { it.equals(champ.id, ignoreCase = true) }
            container.addView(buildChampionChip(activity, container, champ, alreadyPicked))
        }

        return card
    }

    /**
     * Define quais campeões mostrar no card e em que ordem:
     *  1º) os campeões que o jogador JÁ pegou e que pertencem à comp (destaque);
     *  2º) os [TeamComposition.keyChampions] ainda não pegos (mais críticos);
     *  3º) alguns [TeamComposition.requiredPicks] como complemento.
     *
     * Tudo sem duplicatas e até um limite, para o treinador ver o que tem e o
     * que pode adicionar para completar a composição.
     */
    private fun championsToShow(comp: TeamComposition, matchedPicks: List<String>): List<Champion> {
        val seen = linkedSetOf<String>()
        // 1º) picks já feitos que pertencem à comp
        matchedPicks.forEach { seen.add(it) }
        // 2º) keyChampions (os mais críticos)
        comp.keyChampions.forEach { if (seen.size < CHAMPIONS_PER_CARD) seen.add(it) }
        // 3º) requiredPicks como complemento
        comp.requiredPicks.forEach { if (seen.size < CHAMPIONS_PER_CARD) seen.add(it) }

        return seen.mapNotNull { ChampionRepository.getById(it) }
            .take(CHAMPIONS_PER_CARD)
    }

    /** Constrói o mini-card de um campeão (imagem circular + nome). */
    private fun buildChampionChip(
        activity: Activity,
        parent: ViewGroup,
        champ: Champion,
        alreadyPicked: Boolean
    ): View {
        val chip = LayoutInflater.from(activity)
            .inflate(R.layout.item_comp_champion, parent, false)

        val image = chip.findViewById<ImageView>(R.id.iv_comp_champion)
        val name  = chip.findViewById<TextView>(R.id.tv_comp_champion_name)

        Glide.with(image).load(champ.imageUrl).into(image)
        name.text = champ.name

        // Campeões já escolhidos ficam em destaque; os demais levemente esmaecidos.
        chip.alpha = if (alreadyPicked) 1f else 0.55f

        return chip
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    @ColorRes
    private fun tierColorRes(tier: String): Int = when (tier.uppercase()) {
        "S" -> R.color.comp_tier_s
        "A" -> R.color.comp_tier_a
        "B" -> R.color.comp_tier_b
        else -> R.color.comp_tier_b
    }

    /**
     * Limita a altura do ScrollView interno para o dialog não ocupar a tela
     * inteira quando há muitas composições. Como ScrollView não respeita
     * `maxHeight` em XML de forma confiável, ajustamos aqui após o measure.
     */
    private fun limitListHeight(activity: Activity, root: View) {
        val scroll = (root as? ViewGroup)?.let { findScrollView(it) } ?: return
        val maxPx = (MAX_LIST_HEIGHT_DP * activity.resources.displayMetrics.density).toInt()
        scroll.post {
            if (scroll.height > maxPx) {
                scroll.layoutParams = scroll.layoutParams.apply { height = maxPx }
                scroll.requestLayout()
            }
        }
    }

    /** Busca o primeiro ScrollView descendente (a lista de comps). */
    private fun findScrollView(group: ViewGroup): android.widget.ScrollView? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is android.widget.ScrollView) return child
            if (child is ViewGroup) findScrollView(child)?.let { return it }
        }
        return null
    }

    /** Dados consolidados de uma composição para exibição. */
    private data class CompEntry(
        val composition: TeamComposition,
        val bonus: Int,
        val percent: Int?,             // null quando não há picks (modo "mais fortes")
        val matchedPicks: List<String>, // ids/nomes de campeões já pegos que contam pra comp
        val matchedCount: Int = 0       // quantos picks casaram (usado pra ordenar)
    )

    private const val TIER_BADGE_CORNER_PX = 6f
    private const val CHAMPIONS_PER_CARD = 6
}
