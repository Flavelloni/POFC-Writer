package com.pofc.pages

import androidx.compose.runtime.Composable
import com.pofc.model.Category
import com.pofc.model.bottomRoyalties
import com.pofc.model.middleRoyalties
import com.pofc.model.rankName
import com.pofc.model.standardDeck
import com.pofc.model.topPairRoyalties
import com.pofc.model.topTripsRoyalties
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.gap
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.core.Page
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text

@Page("/royalties")
@Composable
fun RoyaltiesPage() {
    Column(Modifier.fillMaxWidth().maxWidth(1040.px).padding(16.px).gap(16.px)) {
        Div(attrs = { cardStyle() }) {
            Column(Modifier.gap(10.px)) {
                H1 { Text("Royalties") }
                Text("Common Pineapple OFC bonuses used by the scorekeeper.")
                Button(attrs = {
                    onClick { window.location.href = appUrl("") }
                    buttonStyle()
                }) { Text("Back to scorekeeper") }
            }
        }
        Div(attrs = { cardStyle() }) {
            Column(Modifier.gap(8.px)) {
                H2 { Text("Top hand") }
                topPairRoyalties.forEach { (rank, value) -> RoyaltyRow("Pair of ${rankName(rank)}s", "Top", "+$value") }
                topTripsRoyalties.forEach { (rank, value) -> RoyaltyRow("Trips ${rankName(rank)}s", "Top", "+$value") }
            }
        }
        Div(attrs = { cardStyle() }) {
            Column(Modifier.gap(8.px)) {
                H2 { Text("Middle and bottom") }
                listOf(Category.Straight, Category.Flush, Category.FullHouse, Category.Quads, Category.StraightFlush, Category.RoyalFlush).forEach { category ->
                    RoyaltyRow(category.label, "Middle +${middleRoyalties[category] ?: 0}", "Bottom +${bottomRoyalties[category] ?: 0}")
                }
            }
        }
        Div(attrs = { cardStyle() }) {
            Column(Modifier.gap(10.px)) {
                H3 { Text("Standard 52-card deck") }
                Div(attrs = {
                    style {
                        display(DisplayStyle.Grid)
                        property("grid-template-columns", "repeat(auto-fill, minmax(42px, 1fr))")
                        gap(6.px)
                    }
                }) {
                    standardDeck.forEach { card -> PlayingCard(card) }
                }
            }
        }
    }
}

@Composable
private fun PlayingCard(code: String) {
    val suit = code.last()
    val rank = code.dropLast(1)
    val symbol = when (suit) {
        'H' -> "♥"
        'D' -> "♦"
        'C' -> "♣"
        else -> "♠"
    }
    val red = suit == 'H' || suit == 'D'
    Div(attrs = {
        style {
            minHeight(58.px)
            padding(6.px)
            border(1.px, LineStyle.Solid, Color(borderColor()))
            borderRadius(6.px)
            backgroundColor(Color(surfaceColor()))
            color(if (red) Color(dangerColor()) else Color(textColor()))
            display(DisplayStyle.Grid)
            property("place-items", "center")
            property("box-shadow", "0 3px 8px rgba(0, 0, 0, 0.10)")
        }
    }) {
        Div(attrs = { style { property("text-align", "center"); lineHeight("1.05") } }) {
            Div(attrs = { style { fontWeight("900") } }) { Text(rank) }
            Div(attrs = { style { fontSize(20.px) } }) { Text(symbol) }
        }
    }
}

@Composable
private fun RoyaltyRow(label: String, middle: String, right: String) {
    Row(Modifier.fillMaxWidth().gap(8.px)) {
        Div(attrs = { style { property("flex", "1") } }) { Text(label) }
        Div(attrs = { style { property("flex", "1"); color(Color(mutedTextColor())) } }) { Text(middle) }
        Div(attrs = { style { property("flex", "1"); fontWeight("800") } }) { Text(right) }
    }
}

private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.cardStyle() {
    style {
        padding(14.px)
        border(1.px, LineStyle.Solid, Color(borderColor()))
        borderRadius(8.px)
        backgroundColor(Color(surfaceColor()))
        color(Color(textColor()))
        property("box-shadow", "0 10px 30px rgba(0, 0, 0, 0.12)")
    }
}

private fun org.jetbrains.compose.web.attributes.AttrsScope<*>.buttonStyle() {
    style {
        minHeight(44.px)
        padding(0.px, 12.px)
        border(1.px, LineStyle.Solid, Color(textColor()))
        borderRadius(8.px)
        backgroundColor(Color(textColor()))
        color(Color(surfaceColor()))
        fontWeight("800")
    }
}

private fun surfaceColor(): String = "Canvas"

private fun textColor(): String = "CanvasText"

private fun mutedTextColor(): String = "GrayText"

private fun borderColor(): String = "GrayText"

private fun dangerColor(): String = "#b3261e"

private fun appUrl(page: String): String {
    val base = if (window.location.hostname.endsWith("github.io") || window.location.pathname.startsWith("/POFC-Writer")) "/POFC-Writer/" else "/"
    return base + page
}
