package com.cblol.scout.data.realm

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * Entidade Realm que armazena o save da carreira.
 *
 * **Design híbrido (campos diretos + payload JSON):** o [com.cblol.scout.data.GameState]
 * é uma estrutura grande, com listas/mapas aninhados (jogadas, eventos, ofertas,
 * laços, etc.) que mudam em bloco a cada tick do jogo. Modelar tudo como
 * entidades Realm normalizadas exigiria dezenas de classes auxiliares e
 * relacionamentos só para representar dados que sempre são lidos/escritos
 * juntos. Esse custo de complexidade não traz benefício na prática.
 *
 * Em vez disso usamos um modelo **híbrido**:
 *  - **Campos diretos** para as propriedades-chave e os contadores leves
 *    (nome do técnico, time, datas do split, orçamento atual, divisão). Isso
 *    permite usar o Realm como API: consultas rápidas para responder
 *    perguntas pontuais (ex: "existe um save?", "qual o orçamento atual?") sem
 *    desserializar o estado todo.
 *  - **Payload JSON** ([payloadJson]) para o "resto" — coleções e estruturas
 *    aninhadas que sempre são lidas/escritas como um todo
 *    (matches, gameLog, playerOverrides, academy, bank, etc.). Mantém o save
 *    compacto e simplifica migrações: a evolução do schema vive no data class,
 *    não em N entidades.
 *
 * **Slot único:** sempre há no máximo UM registro com [SLOT_ID] = "current".
 * Para suportar múltiplos saves no futuro basta usar ids distintos — a estrutura
 * já está preparada (PrimaryKey).
 *
 * **Por que não SharedPreferences?** O Realm já é usado para dados estáticos
 * com criptografia AES-256 e Android Keystore. Reusar a mesma infraestrutura
 * para o save:
 *  - Mantém TODOS os dados do app no mesmo motor de persistência (consistência).
 *  - Herda criptografia transparente do arquivo (saves não ficam em claro nas
 *    SharedPreferences, que podem ser dumpadas em root).
 *  - Habilita queries pontuais sem precisar abrir+parsear o JSON inteiro.
 *  - Permite transações atômicas para load/save (importante quando passarmos
 *    a fragmentar partes do estado em entidades separadas).
 */
class GameStateBlobEntity : RealmObject {

    @PrimaryKey
    var id: String = SLOT_ID

    // ── Campos-chave consultáveis sem desserializar o payload ───────────

    var managerName: String = ""
    var managerTeamId: String = ""
    var splitStartDate: String = ""
    var splitEndDate: String = ""
    var currentDate: String = ""
    var budget: Long = 0L
    var sponsorshipPerWeek: Long = 0L
    /** Nome do enum [com.cblol.scout.data.Division]. */
    var division: String = "FIRST"

    /**
     * Restante do estado em JSON. Inclui todas as listas/mapas aninhados.
     *
     * Mantido como blob por simplicidade — a evolução do save segue acontecendo
     * via [com.cblol.scout.game.repo.GameStateMigrator] sobre o data class
     * desserializado.
     */
    var payloadJson: String = ""

    /**
     * Timestamp (epoch millis) da última gravação. Útil para diagnóstico e
     * para o "save mais recente" quando passarmos a ter múltiplos slots.
     */
    var savedAtMillis: Long = 0L

    companion object {
        /** Id do único slot de save por enquanto. */
        const val SLOT_ID = "current"
    }
}
