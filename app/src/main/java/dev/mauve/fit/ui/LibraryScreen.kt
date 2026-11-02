package dev.mauve.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.mauve.fit.system.Haptics
import dev.mauve.fit.App
import dev.mauve.fit.data.AppData
import dev.mauve.fit.data.Exercise
import dev.mauve.fit.data.L
import dev.mauve.fit.data.Labels

/**
 * Searchable exercise library. With [pickFor] set ("workout" or a plan id) tapping an exercise
 * adds it there and goes back, so the same screen doubles as the exercise picker. [onOpen] shows a
 * tapped exercise in place (the detail pane on wide windows) instead of on its own screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(data: AppData, nav: NavController, pickFor: String?, onOpen: ((String) -> Unit)? = null) {
    val repo = App.repo
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }

    val results = remember(query, category, equipment, favoritesOnly, data.favorites) {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
        repo.all.filter { e ->
            val haystack = (e.name + " " + e.muscles.joinToString(" ") { "$it ${Labels.muscle(it)}" } + " " + Labels.equipment(e.equipment)).lowercase()
            terms.all { it in haystack } &&
                (category == null || e.category == category) &&
                (equipment == null || e.equipment == equipment) &&
                (!favoritesOnly || e.id in data.favorites)
        }.sortedByDescending { it.id in data.favorites }
    }

    fun pick(e: Exercise) {
        when (pickFor) {
            null -> if (onOpen != null) onOpen(e.id) else nav.navigate("exercise/${e.id}")
            "workout" -> { App.store.addToActive(e.id); nav.popBackStack() }
            else -> { App.store.addToRoutine(pickFor, e.id); nav.popBackStack() }
        }
    }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 132.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, L("Zurück", "Back"), size = 44.dp) { nav.popBackStack() }
                Spacer(Modifier.height(16.dp))
                if (pickFor != null) ScreenTitle(L("Hinzufügen", "Add"), L("Übung wählen", "Choose exercise"))
                else ScreenTitle(L("${repo.all.size} Übungen", "${repo.all.size} exercises"), L("Übungen", "Exercises"))
            }
        }
        // Back, search and filters stay pinned while the results scroll underneath.
        stickyHeader {
            Column(Modifier.fillMaxWidth().stickyScrim().padding(top = 8.dp, bottom = 14.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SearchField(query) { query = it } }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip(L("Favoriten", "Favourites"), favoritesOnly) { favoritesOnly = !favoritesOnly } }
                    items(repo.categories) { c -> Chip(Labels.category(c), category == c) { category = if (category == c) null else c } }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(repo.equipment) { eq -> Chip(Labels.equipment(eq), equipment == eq) { equipment = if (equipment == eq) null else eq } }
                }
                Text(
                    L("${results.size} Treffer", "${results.size} results"), Modifier.padding(start = 20.dp, top = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = Ink.faint,
                )
            }
        }
        items(results, key = { it.id }) { e ->
            Row(
                Modifier.fillMaxWidth().clickable { Haptics.click(); pick(e) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(e, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        Labels.muscle(e.target) + " · " + Labels.equipment(e.equipment),
                        style = MaterialTheme.typography.bodySmall, color = Ink.muted, maxLines = 1,
                    )
                }
                when {
                    pickFor != null -> Icon(Icons.Rounded.Add, L("Hinzufügen", "Add"), tint = Ink.accent)
                    e.id in data.favorites -> Icon(Icons.Rounded.Favorite, null, tint = Ink.red, modifier = Modifier.width(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.surface).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Ink.muted)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value, onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.text),
            cursorBrush = SolidColor(Ink.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(L("z. B. bench press, Bizeps, Kurzhantel", "e.g. bench press, biceps, dumbbell"), color = Ink.faint, style = MaterialTheme.typography.bodyLarge)
                inner()
            },
        )
        if (value.isNotEmpty()) Icon(Icons.Rounded.Close, L("Leeren", "Clear"), tint = Ink.muted, modifier = Modifier.clickable { onChange("") })
    }
}
