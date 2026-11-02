package dev.mauve.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Shop
import dev.mauve.fit.system.Haptics

/** The shop with coins, plus skills per muscle group and the exercise collections. */
@Composable
fun ShopScreen(data: AppData, nav: NavController) {
    val targetOf: (String) -> String? = { App.repo[it]?.target }
    val coins = Shop.balance(data, targetOf)
    var tab by remember { mutableStateOf(0) }
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                CoinBadge(coins)
            }
        }
        item { ScreenTitle(L("Münzen für Training, Rekorde und Quests", "Coins for training, records and quests"), L("Shop", "Shop")) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(L("Shop", "Shop"), L("Skills", "Skills"), L("Sammlungen", "Collections")).forEachIndexed { i, label ->
                    Chip(label, selected = tab == i) { Haptics.tick(); tab = i }
                }
            }
        }
        when (tab) {
            0 -> items(Shop.items(), key = { it.key }) { item -> ShopRow(data, item, coins) }
            1 -> {
                item {
                    Text(L("Jede Muskelgruppe steigt mit deinen Arbeitssätzen im Level. Jedes Level bringt 10 Münzen.",
                        "Each muscle group levels up with your working sets. Every level pays 10 coins."),
                        color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                }
                items(Shop.skills(data, targetOf), key = { "s" + it.name }) { s ->
                    Card(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(L("Level ${s.level}", "Level ${s.level}"), color = Ink.accent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Meter(s.fraction, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(L("${s.into} / ${s.need} Sätze bis Level ${s.level + 1} · ${s.sets} gesamt", "${s.into} / ${s.need} sets to level ${s.level + 1} · ${s.sets} total"),
                            style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                    }
                }
            }
            else -> {
                val list = Shop.collections(data)
                item {
                    Text(L("Jede Übung hat eine Sammlung mit Stufen I bis V (10, 25, 50, 100, 250 Sätze). Jede neue Stufe bringt Münzen.",
                        "Every exercise has a collection with tiers I to V (10, 25, 50, 100, 250 sets). Every new tier pays coins."),
                        color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                }
                if (list.isEmpty()) item { Text(L("Nach dem ersten Training füllt sich das hier.", "This fills up after your first workout."), color = Ink.muted) }
                items(list, key = { "c" + it.exerciseId }) { c ->
                    val e = App.repo[c.exerciseId]
                    Card(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Thumb(e, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e?.title ?: c.exerciseId, Modifier.marquee(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Spacer(Modifier.height(6.dp))
                                Meter(c.fraction, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(4.dp))
                                Text(c.next?.let { L("${c.sets} / $it Sätze", "${c.sets} / $it sets") } ?: L("${c.sets} Sätze · voll", "${c.sets} sets · maxed"),
                                    style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(if (c.tier > 0) Shop.roman(c.tier) else "–", style = MaterialTheme.typography.titleLarge,
                                color = if (c.tier > 0) Ink.accent else Ink.faint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopRow(data: AppData, item: Shop.Item, coins: Int) {
    val owned = item.key in data.owned
    val available = Shop.available(data, item)
    val affordable = coins >= item.price
    Card(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShopIcon(item, 48.dp, locked = !Shop.unlocked(data, item))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                // Rarity in its colour, and the level it waits for while locked.
                Text(
                    L(item.rarity.de, item.rarity.en) + if (!Shop.unlocked(data, item)) L(" · ab Level ${item.level}", " · from level ${item.level}") else "",
                    style = MaterialTheme.typography.labelSmall, color = rarityColor(item.rarity), fontWeight = FontWeight.SemiBold,
                )
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                if (item.kind == Shop.Kind.Freeze) Text(L("Vorrat: ${data.freezes} von ${Shop.FREEZE_MAX_BOUGHT}", "In stock: ${data.freezes} of ${Shop.FREEZE_MAX_BOUGHT}"),
                    style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                if (item.kind == Shop.Kind.Boost && !available) Text(L("Läuft", "Running"), style = MaterialTheme.typography.labelSmall, color = Ink.accent)
            }
            Spacer(Modifier.width(8.dp))
            when {
                // A bought title can be worn (or taken off); a bought colour is picked in the settings.
                owned && item.kind == Shop.Kind.Title -> {
                    val key = item.key.removePrefix("title:")
                    val worn = data.title == key
                    Tag(if (worn) L("Aktiv", "Worn") else L("Anlegen", "Wear"), if (worn) Ink.accent else Ink.muted,
                        Modifier.clip(Pill).clickableTick { App.store.setTitle(if (worn) "" else key) })
                }
                owned -> Tag(L("Gekauft", "Owned"), Ink.accent)
                else -> Tag("${item.price}", if (available && affordable) Ink.accent else Ink.faint,
                    Modifier.clip(Pill).clickableTick { if (available && affordable && App.store.buy(item)) Haptics.success() else Haptics.warn() })
            }
        }
    }
}

private fun Modifier.clickableTick(onClick: () -> Unit) = this.then(Modifier.clickable { Haptics.tick(); onClick() })

/** Coin balance as a small pill. */
@Composable
fun CoinBadge(coins: Int, modifier: Modifier = Modifier) {
    Row(modifier.clip(Pill).background(Ink.raised).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Toll, null, tint = Ink.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("$coins", fontWeight = FontWeight.Bold)
    }
}
