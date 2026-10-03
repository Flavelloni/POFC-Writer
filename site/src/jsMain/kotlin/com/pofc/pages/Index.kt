package com.pofc.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pofc.model.Category
import com.pofc.model.Player
import com.pofc.model.PlayerHands
import com.pofc.model.Round
import com.pofc.model.ScoreMode
import com.pofc.model.Session
import com.pofc.model.Store
import com.pofc.model.Street
import com.pofc.model.parseHand
import com.pofc.model.rankName
import com.pofc.model.scoreRound
import com.pofc.model.signed
import com.pofc.model.totals
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.gap
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minHeight
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.icons.MoonIcon
import com.varabyte.kobweb.silk.components.icons.SunIcon
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Date
import kotlin.random.Random

private const val StoreKey = "pofc.kobweb.sessions.v1"
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private enum class Screen {
    Sessions,
    NewSession,
    Session,
    Round
}

private data class HandSelection(val playerId: String, val street: Street)

@Page
@Composable
fun HomePage() {
    var store by remember { mutableStateOf(loadStore()) }
    var screen by remember { mutableStateOf(if (store.activeSessionId == null) Screen.Sessions else Screen.Session) }
    var editingRoundId by remember { mutableStateOf<String?>(null) }

    fun save(next: Store) {
        store = next
        window.localStorage.setItem(StoreKey, json.encodeToString(next))
    }

    fun clearPageData() {
        clearLocalPageData()
        store = Store()
        editingRoundId = null
        screen = Screen.Sessions
    }

    fun deleteSession(sessionId: String) {
        val sessions = store.sessions.filterNot { it.id == sessionId }
        val activeSessionId = store.activeSessionId.takeUnless { it == sessionId }
        save(store.copy(sessions = sessions, activeSessionId = activeSessionId))
        if (activeSessionId == null) {
            editingRoundId = null
            screen = Screen.Sessions
        }
    }

    val activeSession = store.sessions.firstOrNull { it.id == store.activeSessionId }

    Column(Modifier.fillMaxWidth().minHeight(100.percent).gap(16.px)) {
        TopBar(
            subtitle = activeSession?.name ?: "Scorekeeper",
            onSessions = { screen = Screen.Sessions },
            onNew = { screen = Screen.NewSession }
        )
        Div(attrs = {
            style {
                width(100.percent)
                maxWidth(1040.px)
                property("margin", "0 auto")
                padding(16.px)
                property("padding", "clamp(8px, 2.8vw, 16px)")
                property("box-sizing", "border-box")
            }
        }) {
        Column(Modifier.fillMaxWidth().gap(16.px)) {
            when (screen) {
                Screen.Sessions -> SessionsScreen(
                    store = store,
                    onOpen = { id ->
                        save(store.copy(activeSessionId = id))
                        screen = Screen.Session
                    },
                    onNew = { screen = Screen.NewSession },
                    onDeleteSession = { id ->
                        if (window.confirm("Delete this session from this device?")) {
                            deleteSession(id)
                        }
                    },
                    onClearData = {
                        if (window.confirm("Delete all Pineapple OFC data saved by this page on this device?")) {
                            clearPageData()
                        }
                    }
                )
                Screen.NewSession -> NewSessionScreen(
                    onCreate = { session ->
                        save(store.copy(sessions = store.sessions + session, activeSessionId = session.id))
                        screen = Screen.Session
                    },
                    onCancel = { screen = Screen.Sessions }
                )
                Screen.Session -> {
                    if (activeSession == null) {
                        SessionsScreen(
                            store,
                            onOpen = {},
                            onNew = { screen = Screen.NewSession },
                            onDeleteSession = { deleteSession(it) },
                            onClearData = { clearPageData() }
                        )
                    } else {
                        SessionScreen(
                            session = activeSession,
                            onModeChange = { mode ->
                                save(store.replace(activeSession.copy(mode = mode, updatedAt = Date.now())))
                            },
                            onAddRound = {
                                editingRoundId = null
                                screen = Screen.Round
                            },
                            onEditRound = {
                                editingRoundId = it
                                screen = Screen.Round
                            },
                            onDeleteRound = { roundId ->
                                val updated = activeSession.copy(
                                    rounds = activeSession.rounds.filterNot { it.id == roundId },
                                    updatedAt = Date.now()
                                )
                                save(store.replace(updated))
                            },
                            onDeleteSession = {
                                if (window.confirm("Delete ${activeSession.name} from this device?")) {
                                    deleteSession(activeSession.id)
                                }
                            }
                        )
                    }
                }
                Screen.Round -> {
                    if (activeSession != null) {
                        RoundScreen(
                            session = activeSession,
                            roundId = editingRoundId,
                            onSave = { round ->
                                val rounds = activeSession.rounds.filterNot { it.id == round.id } + round
                                val updated = activeSession.copy(rounds = rounds, updatedAt = Date.now())
                                save(store.replace(updated))
                                screen = Screen.Session
                            },
                            onCancel = { screen = Screen.Session }
                        )
                    }
                }
            }
        }
        }
        BottomNav(
            canShowScores = activeSession != null,
            onSessions = { screen = Screen.Sessions },
            onScores = { screen = Screen.Session },
            onRoyalties = { window.location.href = appUrl("royalties") }
        )
    }
}

@Composable
private fun TopBar(subtitle: String, onSessions: () -> Unit, onNew: () -> Unit) {
    Div(attrs = {
        style {
            width(100.percent)
            property("border-bottom", "1px solid ${borderColor()}")
            backgroundColor(Color(surfaceColor()))
        }
    }) {
    Div(attrs = {
        style {
            width(100.percent)
            maxWidth(1040.px)
            property("margin", "0 auto")
            property("box-sizing", "border-box")
        }
    }) {
    Row(Modifier.fillMaxWidth().padding(12.px).gap(12.px), verticalAlignment = Alignment.CenterVertically) {
        Div(attrs = {
            style {
                width(42.px)
                minHeight(42.px)
                borderRadius(8.px)
                backgroundColor(Color(textColor()))
                color(Color(surfaceColor()))
                display(DisplayStyle.Grid)
                property("place-items", "center")
                fontWeight("900")
            }
        }) {
            Text("OFC")
        }
        Column(Modifier.gap(2.px)) {
            H1(attrs = { style { margin(0.px); fontSize(18.px); lineHeight("1") } }) { Text("Pineapple Open Face") }
            Muted(subtitle)
        }
        Row(Modifier.gap(8.px).margin(left = 0.px)) {
            AppButton("Sessions", onSessions)
            PrimaryButton("New", onNew)
            ColorModeButton()
        }
    }
    }
    }
}

@Composable
private fun ColorModeButton() {
    var colorMode by ColorMode.currentState
    Button(attrs = {
        onClick { colorMode = colorMode.opposite }
        iconButtonStyle()
        attr("aria-label", "Toggle color mode")
        attr("title", "Toggle color mode")
    }) {
        if (colorMode.isLight) MoonIcon() else SunIcon()
    }
}

@Composable
private fun SessionsScreen(
    store: Store,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearData: () -> Unit
) {
    Panel {
        Column(Modifier.gap(12.px)) {
            H2 { Text("Sessions") }
            Muted("Start a table or resume an old one. Refreshing or closing the browser keeps this data on this device.")
            Row(Modifier.gap(8.px)) {
                PrimaryButton("Start new session", onNew)
                DangerButton("Clear device data", onClearData)
            }
        }
    }
    if (store.sessions.isEmpty()) {
        Panel { Muted("No sessions yet.") }
    } else {
        store.sessions.sortedByDescending { it.updatedAt }.forEach { session ->
            val (scores, fantasies) = totals(session)
            Panel {
                Column(Modifier.gap(10.px)) {
                    H3 { Text(session.name) }
                    Muted("${session.rounds.size} rounds | ${if (session.mode == ScoreMode.Obk) "OBK" else "Standard"} rules")
                    ScoreGrid(session, scores, fantasies, latest = session.rounds.lastOrNull())
                    ButtonRowEnd {
                        AppButton("Open", onClick = { onOpen(session.id) })
                        DangerButton("Delete", onClick = { onDeleteSession(session.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NewSessionScreen(onCreate: (Session) -> Unit, onCancel: () -> Unit) {
    var sessionName by remember { mutableStateOf("Session from ${Date().toLocaleDateString()}") }
    var playerOne by remember { mutableStateOf("") }
    var playerTwo by remember { mutableStateOf("") }
    var playerThree by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ScoreMode.Standard) }

    Panel {
        Column(Modifier.gap(14.px)) {
            H2 { Text("New session") }
            Field("Session name", sessionName, { sessionName = it }, "Friday game")
            Row(Modifier.gap(8.px)) {
                ToggleButton("Standard", mode == ScoreMode.Standard) { mode = ScoreMode.Standard }
                ToggleButton("OBK", mode == ScoreMode.Obk) { mode = ScoreMode.Obk }
            }
            Field("Player 1", playerOne, { playerOne = it }, "Name")
            Field("Player 2", playerTwo, { playerTwo = it }, "Name")
            Field("Player 3", playerThree, { playerThree = it }, "Optional")
            Row(Modifier.gap(8.px)) {
                PrimaryButton("Start") {
                    val names = listOf(playerOne, playerTwo, playerThree).map { it.trim() }.filter { it.isNotBlank() }
                    if (names.size in 2..3) {
                        val now = Date.now()
                        onCreate(
                            Session(
                                id = id("session"),
                                name = sessionName.ifBlank { "Pineapple OFC" },
                                mode = mode,
                                players = names.map { Player(id("player"), it) },
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                }
                AppButton("Cancel", onCancel)
            }
        }
    }
}

@Composable
private fun SessionScreen(
    session: Session,
    onModeChange: (ScoreMode) -> Unit,
    onAddRound: () -> Unit,
    onEditRound: (String) -> Unit,
    onDeleteRound: (String) -> Unit,
    onDeleteSession: () -> Unit
) {
    val (scores, fantasies) = totals(session)
    Panel {
        Column(Modifier.gap(12.px)) {
            Row(Modifier.fillMaxWidth().gap(8.px), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.gap(4.px)) {
                    H2 { Text(session.name) }
                    Muted(if (session.mode == ScoreMode.Obk) "OBK: a 2:1 street win is worth 2 base points." else "Standard: a 2:1 street win is worth 1 base point.")
                }
                PrimaryButton("Add round", onAddRound)
                DangerButton("Delete session", onDeleteSession)
            }
            Row(Modifier.gap(8.px)) {
                ToggleButton("Standard", session.mode == ScoreMode.Standard) { onModeChange(ScoreMode.Standard) }
                ToggleButton("OBK", session.mode == ScoreMode.Obk) { onModeChange(ScoreMode.Obk) }
            }
            ScoreGrid(session, scores, fantasies, session.rounds.lastOrNull())
        }
    }
    session.rounds.asReversed().forEachIndexed { index, round ->
        Panel {
            Column(Modifier.gap(10.px)) {
                H3 { Text("Round ${session.rounds.size - index}") }
                Muted(Date(round.createdAt).toLocaleString())
                DeltaRow(session, round.deltas)
                round.pairings.forEach { pairing ->
                    val left = session.players.first { it.id == pairing.leftPlayerId }
                    val right = session.players.first { it.id == pairing.rightPlayerId }
                    Muted("${left.name} vs ${right.name}: ${pairing.leftDelta.signed()} / ${pairing.rightDelta.signed()}")
                }
                Row(Modifier.gap(8.px)) {
                    AppButton("Edit", onClick = { onEditRound(round.id) })
                    DangerButton("Delete", onClick = { onDeleteRound(round.id) })
                }
            }
        }
    }
}

@Composable
private fun RoundScreen(session: Session, roundId: String?, onSave: (Round) -> Unit, onCancel: () -> Unit) {
    val existing = session.rounds.firstOrNull { it.id == roundId }
    var hands by remember(roundId) {
        mutableStateOf(
            session.players.associate { player ->
                val entry = existing?.hands?.firstOrNull { it.playerId == player.id } ?: PlayerHands(player.id)
                player.id to entry
            }
        )
    }
    var selectedHand by remember(roundId) { mutableStateOf<HandSelection?>(null) }
    val preview = scoreRound(session, hands.values.toList())

    session.players.forEach { player ->
        val entry = hands.getValue(player.id)
        Panel {
            Column(Modifier.gap(12.px)) {
                Row(Modifier.fillMaxWidth().gap(8.px), verticalAlignment = Alignment.CenterVertically) {
                    H3 { Text(player.name) }
                    ToggleButton("Bust", entry.busted) {
                        hands = hands + (player.id to entry.copy(busted = !entry.busted))
                    }
                    Label {
                        CheckboxInput(checked = entry.fantasy) {
                            onChange { event ->
                                hands = hands + (player.id to entry.copy(fantasy = event.value))
                            }
                        }
                        Text(" Fantasy land")
                    }
                }
                if (entry.busted) {
                    WarningBox("${player.name} is busted. No hand details are required for this round.")
                } else {
                    HandButton(Street.Top, entry.top) { selectedHand = HandSelection(player.id, Street.Top) }
                    HandButton(Street.Middle, entry.middle) { selectedHand = HandSelection(player.id, Street.Middle) }
                    HandButton(Street.Bottom, entry.bottom) { selectedHand = HandSelection(player.id, Street.Bottom) }
                }
            }
        }
    }

    selectedHand?.let { selection ->
        val player = session.players.first { it.id == selection.playerId }
        val entry = hands.getValue(player.id)
        val current = when (selection.street) {
            Street.Top -> entry.top
            Street.Middle -> entry.middle
            Street.Bottom -> entry.bottom
        }
        HandPicker(
            playerName = player.name,
            street = selection.street,
            current = current,
            onApply = { value ->
                val updated = when (selection.street) {
                    Street.Top -> entry.copy(top = value)
                    Street.Middle -> entry.copy(middle = value)
                    Street.Bottom -> entry.copy(bottom = value)
                }
                hands = hands + (player.id to updated)
                selectedHand = null
            },
            onClose = { selectedHand = null }
        )
    }

    Panel {
        Column(Modifier.gap(12.px)) {
            H2 { Text(if (existing == null) "New round" else "Edit round") }
            H3 { Text("Preview") }
            DeltaRow(session, preview.deltas)
            preview.pairings.forEach { pairing ->
                val left = session.players.first { it.id == pairing.leftPlayerId }
                val right = session.players.first { it.id == pairing.rightPlayerId }
                Muted("${left.name} vs ${right.name}: ${pairing.leftDelta.signed()} / ${pairing.rightDelta.signed()}")
            }
            if (preview.issues.isNotEmpty()) {
                WarningBox(preview.issues.joinToString(" | "))
            }
            Row(Modifier.gap(8.px)) {
                if (preview.issues.isEmpty()) {
                    PrimaryButton("Save round") {
                        onSave(
                            Round(
                                id = existing?.id ?: id("round"),
                                createdAt = existing?.createdAt ?: Date.now(),
                                hands = hands.values.toList(),
                                deltas = preview.deltas,
                                fantasy = preview.fantasy,
                                pairings = preview.pairings
                            )
                        )
                    }
                } else {
                    DisabledButton("Resolve issues to save")
                }
                AppButton("Cancel", onCancel)
            }
        }
    }
}

@Composable
private fun HandButton(street: Street, value: String, onClick: () -> Unit) {
    val parsed = parseHand(value, street)
    Button(attrs = {
        onClick { onClick() }
        style {
            width(100.percent)
            minHeight(48.px)
            padding(0.px, 12.px)
            border(1.px, LineStyle.Solid, Color(borderColor()))
            borderRadius(8.px)
            backgroundColor(Color(surfaceColor()))
            color(Color(textColor()))
            property("text-align", "left")
            property("cursor", "pointer")
            property("box-sizing", "border-box")
            fontSize(16.px)
            fontWeight("800")
        }
    }) {
        Text("${street.label}: ${if (value.isBlank()) "?" else parsed.label}")
    }
}

@Composable
private fun CardPlaceholders(count: Int, cards: List<String>) {
    Row(Modifier.gap(4.px)) {
        repeat(count) { index ->
            val card = cards.getOrNull(index)
            if (card == null) {
                Div(attrs = {
                    style {
                        width(30.px)
                        height(42.px)
                        border(1.px, LineStyle.Solid, Color(borderColor()))
                        borderRadius(6.px)
                        backgroundColor(Color(mutedSurfaceColor()))
                        display(DisplayStyle.Grid)
                        property("place-items", "center")
                        color(Color(mutedTextColor()))
                        fontWeight("800")
                    }
                }) { Text("+") }
            } else {
                MiniPlayingCard(card)
            }
        }
    }
}

@Composable
private fun MiniPlayingCard(code: String) {
    val rank = code.dropLast(1)
    val suit = code.last()
    val symbol = suitSymbol(suit)
    val red = suit == 'H' || suit == 'D'
    Div(attrs = {
        style {
            width(30.px)
            height(42.px)
            border(1.px, LineStyle.Solid, Color(borderColor()))
            borderRadius(6.px)
            backgroundColor(Color(surfaceColor()))
            color(if (red) Color(dangerColor()) else Color(textColor()))
            display(DisplayStyle.Grid)
            property("place-items", "center")
            property("box-shadow", "0 2px 6px rgba(31, 37, 34, 0.12)")
        }
    }) {
        Div(attrs = { style { property("text-align", "center"); lineHeight("1") } }) {
            Div(attrs = { style { fontSize(11.px); fontWeight("900") } }) { Text(rank) }
            Div(attrs = { style { fontSize(14.px) } }) { Text(symbol) }
        }
    }
}

@Composable
private fun HandPicker(playerName: String, street: Street, current: String, onApply: (String) -> Unit, onClose: () -> Unit) {
    val parsedCurrent = parseHand(current, street)
    var category by remember(street, current) { mutableStateOf(parsedCurrent.category.takeUnless { it == Category.Unknown }) }
    var firstRank by remember(street, current) { mutableStateOf(parsedCurrent.ranks.getOrNull(0)) }
    var secondRank by remember(street, current) { mutableStateOf(parsedCurrent.ranks.getOrNull(1)) }
    var changingCategory by remember(street, current) { mutableStateOf(category == null) }
    val choices = if (street == Street.Top) {
        listOf(Category.High, Category.Pair, Category.Trips)
    } else {
        listOf(
            Category.High,
            Category.Pair,
            Category.TwoPair,
            Category.Trips,
            Category.Straight,
            Category.Flush,
            Category.FullHouse,
            Category.Quads,
            Category.StraightFlush,
            Category.RoyalFlush
        )
    }
    val needsSecond = category in listOf(Category.TwoPair, Category.FullHouse) || (street == Street.Top && category == Category.Pair)
    val needsFirst = category != null && category != Category.RoyalFlush
    var thirdRank by remember(street, current) { mutableStateOf(parsedCurrent.ranks.getOrNull(2)) }
    var fourthRank by remember(street, current) { mutableStateOf(parsedCurrent.ranks.getOrNull(3)) }
    var fifthRank by remember(street, current) { mutableStateOf(parsedCurrent.ranks.getOrNull(4)) }
    val multiRankCategory = category in listOf(Category.Flush, Category.High)
    val multiRankCount = if (street == Street.Top) 3 else 5
    val selectedRanks = if (multiRankCategory) {
        listOfNotNull(firstRank, secondRank, thirdRank, fourthRank, fifthRank).take(multiRankCount)
    } else {
        listOfNotNull(firstRank, secondRank)
    }
    val value = category?.let { handText(it, selectedRanks) }.orEmpty()

    Div(attrs = {
        style {
            property("position", "fixed")
            property("inset", "0")
            backgroundColor(Color("rgba(31, 37, 34, 0.42)"))
            property("z-index", "40")
            padding(14.px)
            property("padding", "clamp(6px, 2vw, 14px)")
            property("box-sizing", "border-box")
            display(DisplayStyle.Grid)
            property("place-items", "center")
        }
    }) {
        Div(attrs = {
            style {
                width(100.percent)
                maxWidth(720.px)
                maxHeight(94.vh)
                property("overflow", "auto")
                padding(12.px)
                property("padding", "clamp(8px, 2.4vw, 14px)")
                borderRadius(8.px)
                backgroundColor(Color(surfaceColor()))
                color(Color(textColor()))
                property("box-sizing", "border-box")
            }
        }) {
            Column(Modifier.gap(10.px)) {
                H2(attrs = { style { margin(0.px) } }) { Text("$playerName ${street.label}") }
                if (category == null || changingCategory) {
                    OptionGrid {
                        choices.forEach { option ->
                            ToggleButton(option.label, category == option) {
                                category = option
                                changingCategory = false
                                firstRank = null
                                secondRank = null
                                thirdRank = null
                                fourthRank = null
                                fifthRank = null
                                if (option == Category.RoyalFlush) {
                                    firstRank = 14
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.gap(8.px), verticalAlignment = Alignment.CenterVertically) {
                        ToggleButton(category?.label.orEmpty(), true) {}
                        AppButton("Change hand type") { changingCategory = true }
                    }
                }
                if (multiRankCategory) {
                    MultiRankButtons(
                        label = if (category == Category.Flush) "Flush cards" else "High cards",
                        selected = selectedRanks,
                        max = multiRankCount
                    ) { next ->
                        firstRank = next.getOrNull(0)
                        secondRank = next.getOrNull(1)
                        thirdRank = next.getOrNull(2)
                        fourthRank = next.getOrNull(3)
                        fifthRank = next.getOrNull(4)
                    }
                } else if (needsFirst || needsSecond) {
                    if (needsFirst) {
                        RankButtonSection(if (category in listOf(Category.Straight, Category.StraightFlush)) "High" else if (category == Category.Pair) "Pair" else "Rank", firstRank) { firstRank = it }
                    }
                    if (needsSecond) {
                        RankButtonSection(if (category == Category.FullHouse) "Pair" else if (category == Category.Pair) "Kicker" else "2nd pair", secondRank) { secondRank = it }
                    }
                }
                GoodBox("Selected: ${if (value.isBlank()) "nothing yet" else parseHand(value, street).label}")
                Row(Modifier.gap(8.px)) {
                    PrimaryButton("Use hand") {
                        if (category != null) onApply(value)
                    }
                    AppButton("Clear") { onApply("") }
                    AppButton("Close", onClose)
                }
            }
        }
    }
}

@Composable
private fun OptionGrid(content: @Composable () -> Unit) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            property("flex-wrap", "wrap")
            gap(8.px)
            width(100.percent)
        }
    }) {
        content()
    }
}

@Composable
private fun MultiRankButtons(label: String, selected: List<Int>, max: Int, onChange: (List<Int>) -> Unit) {
    Column(Modifier.gap(6.px)) {
        H3(attrs = { style { margin(0.px) } }) { Text(label) }
        OptionGrid {
            (14 downTo 2).forEach { rank ->
                val active = rank in selected
                ToggleButton(rankName(rank), active) {
                    val next = if (active) {
                        selected.filterNot { it == rank }
                    } else {
                        (selected + rank).take(max)
                    }
                    onChange(next)
                }
            }
        }
    }
}

@Composable
private fun RankButtonSection(label: String, selected: Int?, onSelect: (Int?) -> Unit) {
    Column(Modifier.gap(6.px)) {
        H3(attrs = { style { margin(0.px); fontSize(16.px) } }) { Text(label) }
        OptionGrid {
            (14 downTo 2).forEach { rank ->
                ToggleButton(rankName(rank), selected == rank) {
                    onSelect(rank.takeUnless { selected == rank })
                }
            }
        }
    }
}

private fun handText(category: Category, ranks: List<Int>): String =
    when (category) {
        Category.Unknown -> ""
        Category.High -> if (ranks.isEmpty()) "high card" else "${ranks.joinToString(" ") { rankName(it) }} high"
        Category.Pair -> when (ranks.size) {
            0 -> "pair"
            1 -> "pair of ${rankName(ranks[0])}"
            else -> "pair of ${rankName(ranks[0])} kicker ${rankName(ranks[1])}"
        }
        Category.TwoPair -> when (ranks.size) {
            0 -> "two pair"
            1 -> "two pair ${rankName(ranks[0])}"
            else -> "two pair ${rankName(ranks[0])} and ${rankName(ranks[1])}"
        }
        Category.Trips -> ranks.getOrNull(0)?.let { "trips ${rankName(it)}" } ?: "trips"
        Category.Straight -> ranks.getOrNull(0)?.let { "straight ${rankName(it)} high" } ?: "straight"
        Category.Flush -> if (ranks.isEmpty()) "flush" else "flush ${ranks.joinToString(" ") { rankName(it) }}"
        Category.FullHouse -> when (ranks.size) {
            0 -> "full house"
            1 -> "full house ${rankName(ranks[0])}"
            else -> "full house ${rankName(ranks[0])} over ${rankName(ranks[1])}"
        }
        Category.Quads -> ranks.getOrNull(0)?.let { "quads ${rankName(it)}" } ?: "quads"
        Category.StraightFlush -> ranks.getOrNull(0)?.let { "straight flush ${rankName(it)} high" } ?: "straight flush"
        Category.RoyalFlush -> "royal flush"
    }

private fun suitSymbol(suit: Char): String = when (suit) {
    'H' -> "♥"
    'D' -> "♦"
    'C' -> "♣"
    else -> "♠"
}

@Composable
private fun ScoreGrid(session: Session, scores: Map<String, Int>, fantasies: Map<String, Int>, latest: Round?) {
    Div(attrs = {
        style {
            display(DisplayStyle.Grid)
            property("grid-template-columns", "minmax(0, 1fr)")
            gap(10.px)
            width(100.percent)
        }
    }) {
        session.players.forEach { player ->
            Div(attrs = {
                cardStyle()
                style {
                    display(DisplayStyle.Flex)
                    property("align-items", "center")
                    property("justify-content", "space-between")
                    gap(12.px)
                }
            }) {
                H3(attrs = { style { margin(0.px) } }) { Text(player.name) }
                Div(attrs = { style { fontSize(32.px); fontWeight("900") } }) { Text(scores.getValue(player.id).signed()) }
                Div(attrs = { style { property("text-align", "right") } }) {
                    Muted("Latest ${latest?.deltas?.get(player.id)?.signed() ?: "0"}")
                    Muted("Fantasy ${fantasies.getValue(player.id)}")
                }
            }
        }
    }
}

@Composable
private fun DeltaRow(session: Session, deltas: Map<String, Int>) {
    Row(Modifier.gap(8.px)) {
        session.players.forEach { player ->
            Chip("${player.name} ${deltas.getValue(player.id).signed()}")
        }
    }
}

@Composable
private fun BottomNav(canShowScores: Boolean, onSessions: () -> Unit, onScores: () -> Unit, onRoyalties: () -> Unit) {
    Div(attrs = {
        style {
            width(100.percent)
            property("border-top", "1px solid ${borderColor()}")
            backgroundColor(Color(surfaceColor()))
        }
    }) {
        Div(attrs = {
            style {
                width(100.percent)
                maxWidth(1040.px)
                property("margin", "0 auto")
                padding(10.px)
                property("box-sizing", "border-box")
            }
        }) {
            Row(Modifier.fillMaxWidth().gap(8.px)) {
                AppButton("Sessions", onSessions)
                AppButton("Scores", if (canShowScores) onScores else onSessions)
                AppButton("Royalties", onRoyalties)
            }
        }
    }
}

@Composable
private fun ButtonRowEnd(content: @Composable () -> Unit) {
    Div(attrs = {
        style {
            width(100.percent)
            display(DisplayStyle.Flex)
            property("justify-content", "flex-end")
            property("flex-wrap", "wrap")
            gap(8.px)
        }
    }) {
        content()
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Div(attrs = { cardStyle() }) {
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Div(attrs = { style { color(Color(mutedTextColor())); fontSize(14.px) } }) { Text(text) }
}

@Composable
private fun WarningBox(text: String) {
    Div(attrs = { style { padding(12.px); border(1.px, LineStyle.Solid, Color(dangerBorderColor())); borderRadius(8.px); backgroundColor(Color(surfaceColor())); color(Color(dangerColor())) } }) {
        Text(text)
    }
}

@Composable
private fun GoodBox(text: String) {
    Div(attrs = { style { padding(12.px); border(1.px, LineStyle.Solid, Color(borderColor())); borderRadius(8.px); backgroundColor(Color(mutedSurfaceColor())); color(Color(textColor())) } }) {
        Text(text)
    }
}

@Composable
private fun Chip(text: String) {
    Span(attrs = {
        style {
            padding(6.px, 9.px)
            borderRadius(6.px)
            backgroundColor(Color(mutedSurfaceColor()))
            color(Color(textColor()))
            fontWeight("800")
        }
    }) { Text(text) }
}

@Composable
private fun AppButton(text: String, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        buttonStyle(surfaceColor(), textColor(), borderColor())
    }) { Text(text) }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        buttonStyle(textColor(), surfaceColor(), textColor())
    }) { Text(text) }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        buttonStyle(surfaceColor(), dangerColor(), dangerBorderColor())
    }) { Text(text) }
}

@Composable
private fun ToggleButton(text: String, active: Boolean, onClick: () -> Unit) {
    Button(attrs = {
        onClick { onClick() }
        buttonStyle(if (active) textColor() else surfaceColor(), if (active) surfaceColor() else textColor(), textColor())
    }) { Text(text) }
}

@Composable
private fun DisabledButton(text: String) {
    Button(attrs = {
        disabled()
        buttonStyle(mutedSurfaceColor(), mutedTextColor(), borderColor())
        style {
            property("cursor", "not-allowed")
            property("opacity", "0.75")
        }
    }) { Text(text) }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    Label {
        Text(label)
        Input(InputType.Text) {
            value(value)
            placeholder(hint)
            onInput { onChange(it.value) }
            inputStyle()
        }
    }
}

private fun loadStore(): Store =
    try {
        window.localStorage.getItem(StoreKey)?.let { json.decodeFromString<Store>(it) } ?: Store()
    } catch (_: Throwable) {
        Store()
    }

private fun clearLocalPageData() {
    val storage = window.localStorage
    val keys = (0 until storage.length)
        .mapNotNull { storage.key(it) }
        .filter { it == StoreKey || it.startsWith("pofc.") }
    keys.forEach { storage.removeItem(it) }
}

private fun Store.replace(session: Session): Store =
    copy(sessions = sessions.map { if (it.id == session.id) session else it }, activeSessionId = session.id)

private fun id(prefix: String): String = "$prefix-${Date.now().toLong()}-${Random.nextInt(1000, 9999)}"

private fun appUrl(page: String): String {
    val base = if (window.location.hostname.endsWith("github.io") || window.location.pathname.startsWith("/POFC-Writer")) "/POFC-Writer/" else "/"
    return base + page
}

private fun pageColor(): String = "Canvas"

private fun surfaceColor(): String = "Canvas"

private fun mutedSurfaceColor(): String = "ButtonFace"

private fun textColor(): String = "CanvasText"

private fun mutedTextColor(): String = "GrayText"

private fun borderColor(): String = "GrayText"

private fun shadowColor(): String = "rgba(0, 0, 0, 0.12)"

private fun dangerColor(): String = "#b3261e"

private fun dangerBorderColor(): String = "#d7a09a"

private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.cardStyle() {
    style {
        width(100.percent)
        padding(14.px)
        property("padding", "clamp(9px, 2.6vw, 14px)")
        border(1.px, LineStyle.Solid, Color(borderColor()))
        borderRadius(8.px)
        backgroundColor(Color(surfaceColor()))
        color(Color(textColor()))
        property("box-shadow", "0 10px 30px ${shadowColor()}")
        property("box-sizing", "border-box")
    }
}

private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.buttonStyle(background: String, color: String, borderColor: String) {
    style {
        minHeight(44.px)
        padding(0.px, 12.px)
        border(1.px, LineStyle.Solid, Color(borderColor))
        borderRadius(8.px)
        backgroundColor(Color(background))
        color(Color(color))
        fontWeight("800")
        property("cursor", "pointer")
    }
}

private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.inputStyle() {
    style {
        width(100.percent)
        minHeight(44.px)
        padding(10.px, 12.px)
        border(1.px, LineStyle.Solid, Color(borderColor()))
        borderRadius(8.px)
        backgroundColor(Color(surfaceColor()))
        color(Color(textColor()))
        fontSize(16.px)
        property("box-sizing", "border-box")
    }
}

private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.iconButtonStyle() {
    style {
        width(44.px)
        minHeight(44.px)
        padding(0.px)
        border(1.px, LineStyle.Solid, Color(borderColor()))
        borderRadius(8.px)
        backgroundColor(Color(surfaceColor()))
        color(Color(textColor()))
        display(DisplayStyle.Grid)
        property("place-items", "center")
        property("cursor", "pointer")
    }
}
