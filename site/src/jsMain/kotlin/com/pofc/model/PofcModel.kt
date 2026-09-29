package com.pofc.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScoreMode {
    Standard,
    Obk
}

@Serializable
data class Player(
    val id: String,
    val name: String
)

@Serializable
data class PlayerHands(
    val playerId: String,
    val top: String = "",
    val middle: String = "",
    val bottom: String = "",
    val fantasy: Boolean = false,
    val busted: Boolean = false
)

@Serializable
data class PairingResult(
    val leftPlayerId: String,
    val rightPlayerId: String,
    val leftDelta: Int,
    val rightDelta: Int,
    val detail: List<String>
)

@Serializable
data class Round(
    val id: String,
    val createdAt: Double,
    val hands: List<PlayerHands>,
    val deltas: Map<String, Int>,
    val fantasy: Map<String, Int>,
    val pairings: List<PairingResult>
)

@Serializable
data class Session(
    val id: String,
    val name: String,
    val mode: ScoreMode,
    val players: List<Player>,
    val rounds: List<Round> = emptyList(),
    val createdAt: Double,
    val updatedAt: Double
)

@Serializable
data class Store(
    val sessions: List<Session> = emptyList(),
    val activeSessionId: String? = null
)

enum class Street(val label: String, val example: String) {
    Top("Top", "pair of queens, pair of kings, trips 7, ace high"),
    Middle("Middle", "straight 7 high, flush king high, full house 8 over 3"),
    Bottom("Bottom", "two pair aces and sevens, straight flush 9 high, quads 4")
}

enum class Category(val label: String, val strength: Int) {
    Unknown("Unknown", 0),
    High("High card", 1),
    Pair("Pair", 2),
    TwoPair("Two pair", 3),
    Trips("Trips", 4),
    Straight("Straight", 5),
    Flush("Flush", 6),
    FullHouse("Full house", 7),
    Quads("Quads", 8),
    StraightFlush("Straight flush", 9),
    RoyalFlush("Royal flush", 10)
}

data class ParsedHand(
    val raw: String,
    val street: Street,
    val category: Category,
    val ranks: List<Int>,
    val label: String,
    val royalty: Int,
    val fantasyEligible: Boolean,
    val warnings: List<String>,
    val specifiedRankCount: Int = ranks.size,
    val autoCards: List<String> = emptyList()
)

private data class HandShape(val ranks: Int, val cards: Int)

data class PlayerParsedHands(
    val player: Player,
    val top: ParsedHand,
    val middle: ParsedHand,
    val bottom: ParsedHand,
    val fouled: Boolean,
    val busted: Boolean,
    val orderIssue: String?
) {
    fun hand(street: Street): ParsedHand = when (street) {
        Street.Top -> top
        Street.Middle -> middle
        Street.Bottom -> bottom
    }
}

data class RoundPreview(
    val parsed: Map<String, PlayerParsedHands>,
    val deltas: Map<String, Int>,
    val fantasy: Map<String, Int>,
    val pairings: List<PairingResult>,
    val issues: List<String>
)

val standardDeck = listOf("S", "H", "D", "C").flatMap { suit ->
    listOf("A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2").map { rank ->
        "$rank$suit"
    }
}

private val rankValues = mapOf(
    "2" to 2,
    "3" to 3,
    "4" to 4,
    "5" to 5,
    "6" to 6,
    "7" to 7,
    "8" to 8,
    "9" to 9,
    "10" to 10,
    "J" to 11,
    "Q" to 12,
    "K" to 13,
    "A" to 14
)

private val valueRanks = rankValues.entries.associate { (rank, value) -> value to rank }

val topPairRoyalties = mapOf(6 to 1, 7 to 2, 8 to 3, 9 to 4, 10 to 5, 11 to 6, 12 to 7, 13 to 8, 14 to 9)
val topTripsRoyalties = (2..14).associateWith { it + 8 }
val middleRoyalties = mapOf(
    Category.Trips to 2,
    Category.Straight to 4,
    Category.Flush to 8,
    Category.FullHouse to 12,
    Category.Quads to 20,
    Category.StraightFlush to 30,
    Category.RoyalFlush to 50
)
val bottomRoyalties = mapOf(
    Category.Straight to 2,
    Category.Flush to 4,
    Category.FullHouse to 6,
    Category.Quads to 10,
    Category.StraightFlush to 15,
    Category.RoyalFlush to 25
)

fun parseHand(raw: String, street: Street): ParsedHand {
    val text = raw.normalize()
    if (text.isBlank()) {
        return ParsedHand(raw, street, Category.Unknown, emptyList(), "Missing hand", 0, false, listOf("Enter a hand description."))
    }

    val compact = text.replace(" ", "")
    val ranks = parseRanksInOrder(text)
    val category = when {
        compact.matches(Regex("(aaa|kkk|qqq|jjj|ttt|999|888|777|666|555|444|333|222)")) -> Category.Trips
        compact.matches(Regex("(aa|kk|qq|jj|tt|99|88|77|66|55|44|33|22)")) -> Category.Pair
        "royal" in text -> Category.RoyalFlush
        "straight flush" in text || Regex("\\bsf\\b").containsMatchIn(text) -> Category.StraightFlush
        "quad" in text || "four of a kind" in text || Regex("\\bfour\\b").containsMatchIn(text) -> Category.Quads
        "full house" in text || "boat" in text -> Category.FullHouse
        "flush" in text -> Category.Flush
        "straight" in text -> Category.Straight
        "two pair" in text || "2 pair" in text -> Category.TwoPair
        "trips" in text || "set" in text || Regex("\\bthree\\b").containsMatchIn(text) -> Category.Trips
        "pair" in text -> Category.Pair
        "high" in text || ranks.isNotEmpty() -> Category.High
        else -> Category.Unknown
    }

    val effectiveRanks = when {
        compact.matches(Regex("(aaa|kkk|qqq|jjj|ttt|999|888|777|666|555|444|333|222)")) -> listOf(codeRank(compact.first()))
        compact.matches(Regex("(aa|kk|qq|jj|tt|99|88|77|66|55|44|33|22)")) -> listOf(codeRank(compact.first()))
        category in listOf(Category.Flush, Category.High) -> ranks.take(if (street == Street.Top) 3 else 5)
        category in listOf(Category.Straight, Category.StraightFlush) -> ranks.take(1)
        category in listOf(Category.FullHouse, Category.TwoPair) -> ranks.take(2)
        category == Category.Pair -> ranks.take(if (street == Street.Top) 2 else 1)
        category == Category.RoyalFlush -> listOf(14)
        else -> ranks.take(1)
    }

    val hand = ParsedHand(
        raw = raw,
        street = street,
        category = category,
        ranks = effectiveRanks,
        label = describe(category, effectiveRanks),
        royalty = royaltyFor(street, category, effectiveRanks),
        fantasyEligible = street == Street.Top && (category == Category.Trips || (category == Category.Pair && effectiveRanks.firstOrNull() ?: 0 >= 12)),
        warnings = if (category == Category.Unknown) listOf("Use examples like pair of kings, straight 7 high, or full house 8 over 3.") else emptyList(),
        specifiedRankCount = effectiveRanks.size
    )
    return hand
}

fun scoreRound(session: Session, hands: List<PlayerHands>): RoundPreview {
    val issues = mutableListOf<String>()
    val parsedEntries = session.players.associateWith { player ->
        hands.firstOrNull { it.playerId == player.id } ?: PlayerHands(player.id)
    }
    val resolvedHands = resolveAutomaticHands(session.players, parsedEntries)
    val parsed = session.players.associate { player ->
        val entry = hands.firstOrNull { it.playerId == player.id } ?: PlayerHands(player.id)
        val parsedHands = if (entry.busted) {
            PlayerParsedHands(
                player = player,
                top = parseHand("bust", Street.Top),
                middle = parseHand("bust", Street.Middle),
                bottom = parseHand("bust", Street.Bottom),
                fouled = true,
                busted = true,
                orderIssue = null
            )
        } else {
            PlayerParsedHands(
                player = player,
                top = resolvedHands.getValue(player.id).getValue(Street.Top),
                middle = resolvedHands.getValue(player.id).getValue(Street.Middle),
                bottom = resolvedHands.getValue(player.id).getValue(Street.Bottom),
                fouled = false,
                busted = false,
                orderIssue = null
            ).withOrderValidation()
        }
        parsedHands.orderIssue?.let { issues += "${player.name}: $it" }
        if (!parsedHands.busted) {
            parsedHands.allHands().flatMap { it.warnings }.forEach { issues += "${player.name}: $it" }
        }
        player.id to parsedHands
    }

    val deltas = session.players.associate { it.id to 0 }.toMutableMap()
    val pairings = mutableListOf<PairingResult>()
    for (leftIndex in session.players.indices) {
        for (rightIndex in leftIndex + 1 until session.players.size) {
            val left = session.players[leftIndex]
            val right = session.players[rightIndex]
            val result = scorePair(session.mode, parsed.getValue(left.id), parsed.getValue(right.id))
            result.issue?.let { issues += "${left.name} vs ${right.name}: $it" }
            deltas[left.id] = deltas.getValue(left.id) + result.pairing.leftDelta
            deltas[right.id] = deltas.getValue(right.id) + result.pairing.rightDelta
            pairings += result.pairing
        }
    }

    val fantasy = session.players.associate { player ->
        val explicit = hands.firstOrNull { it.playerId == player.id }?.fantasy == true
        val earned = !parsed.getValue(player.id).busted && parsed.getValue(player.id).top.fantasyEligible
        player.id to if (explicit || earned) 1 else 0
    }

    return RoundPreview(parsed, deltas, fantasy, pairings, issues)
}

fun resolvedHandsForDisplay(players: List<Player>, hands: List<PlayerHands>): Map<String, Map<Street, ParsedHand>> =
    resolveAutomaticHands(players, players.associateWith { player ->
        hands.firstOrNull { it.playerId == player.id } ?: PlayerHands(player.id)
    })

fun totals(session: Session): Pair<Map<String, Int>, Map<String, Int>> {
    val scores = session.players.associate { it.id to 0 }.toMutableMap()
    val fantasies = session.players.associate { it.id to 0 }.toMutableMap()
    session.rounds.forEach { round ->
        round.deltas.forEach { (id, value) -> scores[id] = scores.getValue(id) + value }
        round.fantasy.forEach { (id, value) -> fantasies[id] = fantasies.getValue(id) + value }
    }
    return scores to fantasies
}

fun Int.signed(): String = if (this > 0) "+$this" else toString()

fun rankName(value: Int): String = valueRanks[value] ?: "?"

private data class PairScore(val pairing: PairingResult, val issue: String?)

private fun scorePair(mode: ScoreMode, left: PlayerParsedHands, right: PlayerParsedHands): PairScore {
    if (left.fouled && right.fouled) {
        return PairScore(PairingResult(left.player.id, right.player.id, 0, 0, listOf("Both players fouled.")), null)
    }
    if (left.fouled || right.fouled) {
        val clean = if (left.fouled) right else left
        val value = 6 + if (clean.busted) 0 else clean.allHands().sumOf { it.royalty }
        val leftDelta = if (left.fouled) -value else value
        return PairScore(
            PairingResult(left.player.id, right.player.id, leftDelta, -leftDelta, listOf("${clean.player.name} wins against a bust.")),
            null
        )
    }

    var streetTotal = 0
    val detail = mutableListOf<String>()
    Street.entries.forEach { street ->
        val comparison = compareHands(left.hand(street), right.hand(street))
        if (comparison.result == null) {
            return PairScore(PairingResult(left.player.id, right.player.id, 0, 0, detail), "${street.label}: ${comparison.reason}")
        }
        streetTotal += comparison.result
        detail += "${street.label}: " + when {
            comparison.result > 0 -> left.player.name
            comparison.result < 0 -> right.player.name
            else -> "tie"
        }
    }

    var base = streetTotal
    if (kotlin.math.abs(streetTotal) == 3) base = streetTotal.sign() * 6
    if (mode == ScoreMode.Obk && kotlin.math.abs(streetTotal) == 1) base = streetTotal.sign() * 2
    val royalties = left.allHands().sumOf { it.royalty } - right.allHands().sumOf { it.royalty }
    val leftDelta = base + royalties
    detail += "Base ${base.signed()}, royalties ${royalties.signed()}"
    return PairScore(PairingResult(left.player.id, right.player.id, leftDelta, -leftDelta, detail), null)
}

private data class Comparison(val result: Int?, val reason: String)

private fun compareHands(left: ParsedHand, right: ParsedHand): Comparison {
    if (left.category == Category.Unknown || right.category == Category.Unknown) {
        return Comparison(null, "A hand type is missing or not recognized.")
    }
    val categoryDiff = left.category.strength.compareTo(right.category.strength)
    if (categoryDiff != 0) return Comparison(categoryDiff.sign(), "")
    val needed = when (left.category) {
        Category.RoyalFlush -> 0
        Category.Flush -> 5
        Category.High -> 1
        Category.FullHouse, Category.TwoPair -> 2
        Category.Pair -> if (left.street == Street.Top) 2 else 1
        else -> 1
    }
    repeat(needed) { index ->
        val leftRank = left.ranks.getOrNull(index)
        val rightRank = right.ranks.getOrNull(index)
        if (leftRank == null || rightRank == null) {
            return Comparison(null, "${left.category.label} needs rank detail to break the tie.")
        }
        if (leftRank != rightRank) return Comparison(leftRank.compareTo(rightRank).sign(), "")
    }
    return Comparison(0, "")
}

private fun resolveAutomaticHands(players: List<Player>, entries: Map<Player, PlayerHands>): Map<String, Map<Street, ParsedHand>> {
    val parsed = players.associate { player ->
        val entry = entries.getValue(player)
        player.id to mapOf(
            Street.Top to parseHand(entry.top, Street.Top),
            Street.Middle to parseHand(entry.middle, Street.Middle),
            Street.Bottom to parseHand(entry.bottom, Street.Bottom)
        )
    }
    val rankUsage = mutableMapOf<Int, Int>().withDefault { 0 }
    val cardUsage = mutableSetOf<String>()
    parsed.values.flatMap { it.values }.forEach { hand ->
        if (hand.specifiedRankCount > 0) {
            rankConsumption(hand, specifiedOnly = true).forEach { (rank, count) ->
                rankUsage[rank] = rankUsage.getValue(rank) + count
            }
        }
    }
    val resolved = parsed.mapValues { (_, streetHands) -> streetHands.toMutableMap() }
    data class AutoTarget(val player: Player, val street: Street, val hand: ParsedHand)
    val targets = players.flatMap { player ->
        Street.entries.mapNotNull { street ->
            val hand = parsed.getValue(player.id).getValue(street)
            if (hand.category != Category.Unknown && hand.category != Category.RoyalFlush) {
                AutoTarget(player, street, hand)
            } else {
                null
            }
        }
    }.sortedBy { autoPriority(it.hand.category) }

    targets.forEach { target ->
        val auto = automaticHand(target.hand, rankUsage, cardUsage)
        if (auto != null) {
            val autoHand = target.hand.withAutomatic(auto.ranks, auto.cards)
            resolved.getValue(target.player.id)[target.street] = autoHand
            val previous = rankConsumption(target.hand, specifiedOnly = true)
            rankConsumption(autoHand, specifiedOnly = false).forEach { (rank, count) ->
                val newlyInferred = count - (previous[rank] ?: 0)
                if (newlyInferred > 0) {
                    rankUsage[rank] = rankUsage.getValue(rank) + newlyInferred
                }
            }
            cardUsage += auto.cards
        }
    }

    return resolved
}

private fun autoPriority(category: Category): Int = when (category) {
    Category.StraightFlush -> 0
    Category.RoyalFlush -> 1
    Category.Quads -> 2
    Category.FullHouse -> 3
    Category.Straight -> 4
    Category.Trips -> 5
    Category.Pair, Category.TwoPair -> 6
    Category.High, Category.Flush -> 7
    Category.Unknown -> 8
}

private data class AutomaticHand(val ranks: List<Int>, val cards: List<String>)

private fun ParsedHand.withAutomatic(newRanks: List<Int>, cards: List<String>): ParsedHand =
    copy(
        ranks = newRanks,
        label = describe(category, newRanks),
        royalty = royaltyFor(street, category, newRanks),
        fantasyEligible = street == Street.Top && (category == Category.Trips || (category == Category.Pair && newRanks.firstOrNull() ?: 0 >= 12)),
        warnings = warnings + impossibleAutoWarning(category, cards),
        specifiedRankCount = specifiedRankCount,
        autoCards = cards
    )

private fun impossibleAutoWarning(category: Category, cards: List<String>): List<String> =
    if (category != Category.Unknown && cards.isEmpty()) {
        listOf("${category.label} cannot be auto-filled from the available cards. Change a specified hand.")
    } else {
        emptyList()
    }

private fun automaticHand(hand: ParsedHand, rankUsage: Map<Int, Int>, cardUsage: Set<String>): AutomaticHand? {
    val category = hand.category
    val street = hand.street
    val specified = hand.ranks.take(hand.specifiedRankCount)
    fun available(rank: Int): Int {
        val abstractUsed = rankUsage[rank] ?: 0
        val concreteUsed = cardUsage.count { card -> card.dropLast(1) == rankName(rank) }
        return 4 - maxOf(abstractUsed, concreteUsed)
    }
    fun lowestDistinct(count: Int, excluded: Set<Int> = emptySet()): List<Int> =
        (2..14).filter { it !in excluded && available(it) > 0 }.take(count)
    fun lowKickers(count: Int, excluded: Set<Int>): List<Int> =
        lowestDistinct(count, excluded)
    fun cardsForRanks(ranks: List<Int>, suitOrder: List<Char>): List<String>? {
        val usedCards = mutableSetOf<String>()
        return ranks.map { rank ->
            suitOrder.map { "${rankName(rank)}$it" }.firstOrNull { it !in usedCards && it !in cardUsage }?.also { usedCards += it }
                ?: return null
        }
    }
    fun cardsForRanksByPosition(ranks: List<Int>, suitsByPosition: List<Char>): List<String>? {
        val usedCards = mutableSetOf<String>()
        return ranks.mapIndexed { index, rank ->
            val preferred = suitsByPosition.getOrElse(index) { suitsByPosition.last() }
            listOf(preferred, 'H', 'S', 'D', 'C')
                .distinct()
                .map { "${rankName(rank)}$it" }
                .firstOrNull { it !in usedCards && it !in cardUsage }
                ?.also { usedCards += it }
                ?: return null
        }
    }
    fun offsuitCardsForRanks(ranks: List<Int>): List<String>? =
        cardsForRanksByPosition(ranks, listOf('H', 'S', 'D', 'C', 'H'))
    fun suitedCardsForRanks(ranks: List<Int>): List<String>? =
        listOf('H', 'S', 'D', 'C').firstNotNullOfOrNull { suit ->
            cardsForRanks(ranks, listOf(suit))
        }
    fun straightRanks(high: Int): List<Int> =
        if (high == 5) listOf(5, 4, 3, 2, 14) else (high downTo (high - 4)).toList()
    fun lowestStraightRanks(): List<Int>? =
        (5..14).map { straightRanks(it) }.firstOrNull { ranks -> ranks.all { available(it) > 0 } }

    return when (category) {
        Category.High -> {
            val cardsRanks = if (street == Street.Top) {
                completeTopHigh(specified, ::available)
            } else {
                highCardCandidates()
                    .firstOrNull { candidate -> specified.all { it in candidate } && candidate.all { available(it) > 0 } }
                    .orEmpty()
            }
            val highRank = specified.firstOrNull() ?: cardsRanks.maxOrNull()
            AutomaticHand(listOfNotNull(highRank), highRank?.let { cardsForRanks(listOf(it), listOf('H', 'S', 'D', 'C')) }.orEmpty())
        }
        Category.Pair -> {
            val pair = specified.firstOrNull() ?: (2..14).firstOrNull { available(it) >= 2 } ?: return AutomaticHand(emptyList(), emptyList())
            val kickers = if (street == Street.Top) {
                listOfNotNull(specified.getOrNull(1) ?: lowestDistinct(1, setOf(pair)).firstOrNull())
            } else {
                lowKickers(3, setOf(pair))
            }
            val ranks = if (street == Street.Top) listOf(pair) + kickers else listOf(pair)
            AutomaticHand(ranks, cardsForRanks(List(2) { pair } + kickers, listOf('H', 'S', 'D', 'C')).orEmpty())
        }
        Category.TwoPair -> {
            val firstPair = specified.firstOrNull() ?: (2..14).firstOrNull { available(it) >= 2 } ?: return AutomaticHand(emptyList(), emptyList())
            val secondPair = specified.getOrNull(1) ?: (2..14).firstOrNull { it != firstPair && available(it) >= 2 } ?: return AutomaticHand(emptyList(), emptyList())
            val kicker = lowKickers(1, setOf(firstPair, secondPair))
            AutomaticHand(listOf(firstPair, secondPair), cardsForRanks(List(2) { firstPair } + List(2) { secondPair } + kicker, listOf('H', 'S', 'D', 'C')).orEmpty())
        }
        Category.Trips -> {
            val rank = specified.firstOrNull() ?: (2..14).firstOrNull { available(it) >= 3 } ?: return AutomaticHand(emptyList(), emptyList())
            val kickers = if (street == Street.Top) emptyList() else lowKickers(2, setOf(rank))
            AutomaticHand(listOf(rank), cardsForRanks(List(3) { rank } + kickers, listOf('H', 'S', 'D', 'C')).orEmpty())
        }
        Category.Straight -> {
            val ranks = specified.firstOrNull()?.let { straightRanks(it) } ?: lowestStraightRanks().orEmpty()
            AutomaticHand(listOfNotNull(ranks.firstOrNull()), offsuitCardsForRanks(ranks).orEmpty())
        }
        Category.StraightFlush -> {
            val ranks = specified.firstOrNull()?.let { straightRanks(it) }
                ?: (5..13).map { straightRanks(it) }.firstOrNull { ranks ->
                    ranks.all { available(it) > 0 } && suitedCardsForRanks(ranks) != null
                }.orEmpty()
            AutomaticHand(listOfNotNull(ranks.firstOrNull()), suitedCardsForRanks(ranks).orEmpty())
        }
        Category.Quads -> {
            val rank = specified.firstOrNull() ?: (2..14).firstOrNull { available(it) >= 4 } ?: return AutomaticHand(emptyList(), emptyList())
            val kicker = lowKickers(1, setOf(rank))
            AutomaticHand(listOf(rank), cardsForRanks(List(4) { rank } + kicker, listOf('H', 'S', 'D', 'C')).orEmpty())
        }
        Category.FullHouse -> {
            val trip = specified.firstOrNull() ?: (2..14).firstOrNull { available(it) >= 3 } ?: return AutomaticHand(emptyList(), emptyList())
            val pair = specified.getOrNull(1) ?: (2..14).firstOrNull { it != trip && available(it) >= 2 } ?: return AutomaticHand(emptyList(), emptyList())
            AutomaticHand(listOf(trip, pair), cardsForRanks(List(3) { trip } + List(2) { pair }, listOf('H', 'S', 'D', 'C')).orEmpty())
        }
        Category.Flush -> {
            val ranks = if (specified.isEmpty()) {
                highCardCandidates().firstOrNull { candidate -> candidate.all { available(it) > 0 } }.orEmpty()
            } else {
                highCardCandidates()
                    .firstOrNull { candidate -> specified.all { it in candidate } && candidate.all { available(it) > 0 } }
                    .orEmpty()
            }
            AutomaticHand(ranks, suitedCardsForRanks(ranks).orEmpty())
        }
        else -> null
    }
}

private fun completeTopHigh(specified: List<Int>, available: (Int) -> Int): List<Int> =
    combinations((2..14).toList(), 3)
        .filter { candidate -> specified.all { it in candidate } && candidate.all { available(it) > 0 } }
        .sortedWith(::compareHighCardCandidates)
        .firstOrNull()
        ?.sortedDescending()
        .orEmpty()

private fun highCardCandidates(): List<List<Int>> =
    combinations((2..14).toList(), 5)
        .filterNot { isStraightRanks(it) }
        .sortedWith(::compareHighCardCandidates)
        .map { it.sortedDescending() }

private fun compareHighCardCandidates(left: List<Int>, right: List<Int>): Int {
    val leftDesc = left.sortedDescending()
    val rightDesc = right.sortedDescending()
    leftDesc.zip(rightDesc).forEach { (leftRank, rightRank) ->
        if (leftRank != rightRank) return leftRank.compareTo(rightRank)
    }
    return 0
}

private fun combinations(values: List<Int>, size: Int): List<List<Int>> {
    if (size == 0) return listOf(emptyList())
    if (values.size < size) return emptyList()
    if (values.size == size) return listOf(values)
    val head = values.first()
    val tail = values.drop(1)
    return combinations(tail, size - 1).map { listOf(head) + it } + combinations(tail, size)
}

private fun isStraightRanks(ranks: List<Int>): Boolean {
    val sorted = ranks.sorted()
    if (sorted == listOf(2, 3, 4, 5, 14)) return true
    return sorted.zipWithNext().all { (left, right) -> right == left + 1 }
}

private fun handShape(category: Category, street: Street): HandShape = when (category) {
    Category.High -> HandShape(1, if (street == Street.Top) 3 else 5)
    Category.Pair -> HandShape(if (street == Street.Top) 2 else 1, if (street == Street.Top) 3 else 2)
    Category.TwoPair -> HandShape(2, 5)
    Category.Trips -> HandShape(1, 3)
    Category.Straight -> HandShape(1, 5)
    Category.Flush -> HandShape(5, 5)
    Category.FullHouse -> HandShape(2, 5)
    Category.Quads -> HandShape(1, 4)
    Category.StraightFlush -> HandShape(1, 5)
    Category.RoyalFlush -> HandShape(1, 5)
    Category.Unknown -> HandShape(0, 0)
}

private fun rankConsumption(hand: ParsedHand, specifiedOnly: Boolean): Map<Int, Int> =
    when (hand.category) {
        Category.High, Category.Flush -> hand.ranks.take(hand.specifiedRankCount.takeIf { specifiedOnly } ?: hand.ranks.size).associateWith { 1 }
        Category.Pair -> hand.ranks.firstOrNull()?.let { pair ->
            buildMap {
                put(pair, 2)
                if (!specifiedOnly || hand.specifiedRankCount > 1) {
                    hand.ranks.getOrNull(1)?.let { put(it, 1) }
                }
            }
        } ?: emptyMap()
        Category.Trips -> hand.ranks.firstOrNull()?.let { mapOf(it to 3) } ?: emptyMap()
        Category.Quads -> hand.ranks.firstOrNull()?.let { mapOf(it to 4) } ?: emptyMap()
        Category.FullHouse -> buildMap {
            hand.ranks.getOrNull(0)?.let { put(it, 3) }
            if (!specifiedOnly || hand.specifiedRankCount > 1) {
                hand.ranks.getOrNull(1)?.let { put(it, 2) }
            }
        }
        Category.TwoPair -> buildMap {
            hand.ranks.take(if (specifiedOnly) hand.specifiedRankCount else hand.ranks.size).forEach { put(it, 2) }
        }
        Category.Straight, Category.StraightFlush -> straightRanksForModel(hand.ranks.firstOrNull()).associateWith { 1 }
        Category.RoyalFlush -> listOf(14, 13, 12, 11, 10).associateWith { 1 }
        Category.Unknown -> emptyMap()
    }

private fun PlayerParsedHands.withOrderValidation(): PlayerParsedHands {
    val topMiddle = compareHands(top, middle)
    if (topMiddle.result == 1) return copy(fouled = true, orderIssue = "Top hand is stronger than middle hand.")
    if (topMiddle.result == null) return copy(orderIssue = "Top vs middle is unclear. ${topMiddle.reason}")
    val middleBottom = compareHands(middle, bottom)
    if (middleBottom.result == 1) return copy(fouled = true, orderIssue = "Middle hand is stronger than bottom hand.")
    if (middleBottom.result == null) return copy(orderIssue = "Middle vs bottom is unclear. ${middleBottom.reason}")
    return this
}

private fun PlayerParsedHands.allHands(): List<ParsedHand> = listOf(top, middle, bottom)

private fun String.normalize(): String =
    lowercase()
        .replace("-", " ")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun parseRanksInOrder(text: String): List<Int> {
    val patterns = listOf(
        14 to Regex("\\b(a|ace|aces)\\b"),
        13 to Regex("\\b(k|king|kings)\\b"),
        12 to Regex("\\b(q|queen|queens)\\b"),
        11 to Regex("\\b(j|jack|jacks)\\b"),
        10 to Regex("\\b(10|t|ten|tens)\\b"),
        9 to Regex("\\b(9|nine|nines)\\b"),
        8 to Regex("\\b(8|eight|eights)\\b"),
        7 to Regex("\\b(7|seven|sevens)\\b"),
        6 to Regex("\\b(6|six|sixes)\\b"),
        5 to Regex("\\b(5|five|fives)\\b"),
        4 to Regex("\\b(4|four|fours)\\b"),
        3 to Regex("\\b(3|three|threes)\\b"),
        2 to Regex("\\b(2|two|twos|deuce|deuces)\\b")
    )
    return patterns.flatMap { (rank, regex) ->
        regex.findAll(text).map { match -> match.range.first to rank }
    }.sortedBy { it.first }.map { it.second }.distinct()
}

private fun codeRank(char: Char): Int = when (char.lowercaseChar()) {
    'a' -> 14
    'k' -> 13
    'q' -> 12
    'j' -> 11
    't' -> 10
    else -> char.digitToInt()
}

private fun royaltyFor(street: Street, category: Category, ranks: List<Int>): Int = when (street) {
    Street.Top -> when (category) {
        Category.Pair -> topPairRoyalties[ranks.firstOrNull()] ?: 0
        Category.Trips -> topTripsRoyalties[ranks.firstOrNull()] ?: 0
        else -> 0
    }
    Street.Middle -> middleRoyalties[category] ?: 0
    Street.Bottom -> bottomRoyalties[category] ?: 0
}

private fun describe(category: Category, ranks: List<Int>): String {
    if (category == Category.Unknown) return "Unknown"
    if (category == Category.RoyalFlush) return "Royal flush"
    if (category == Category.FullHouse) {
        return if (ranks.size >= 2) "Full house ${rankName(ranks[0])} over ${rankName(ranks[1])}" else "Full house"
    }
    if (category == Category.TwoPair) {
        return if (ranks.size >= 2) "Two pair ${rankName(ranks[0])} and ${rankName(ranks[1])}" else "Two pair"
    }
    if (category == Category.Pair && ranks.size >= 2) {
        return "Pair of ${rankName(ranks[0])}s, ${rankName(ranks[1])} kicker"
    }
    if (category in listOf(Category.Straight, Category.Flush, Category.StraightFlush, Category.High)) {
        return if (ranks.isEmpty()) {
            category.label
        } else if (category == Category.Flush || category == Category.High) {
            "${category.label} ${ranks.joinToString(" ") { rankName(it) }}"
        } else {
            "${category.label} ${rankName(ranks.first())} high"
        }
    }
    return "${category.label}${ranks.firstOrNull()?.let { " of ${rankName(it)}s" } ?: ""}"
}

private fun straightRanksForModel(high: Int?): List<Int> {
    val top = high ?: return emptyList()
    return if (top == 5) listOf(5, 4, 3, 2, 14) else (top downTo (top - 4)).map { it.coerceAtLeast(2) }
}

private fun Int.sign(): Int = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}
