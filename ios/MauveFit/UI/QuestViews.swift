import SwiftUI

/** "Level 4 · Athlete" with the bar to the next level; a bought title replaces the level's own. */
struct LevelRow: View {
    let rank: Quests.Rank
    var title = ""

    var body: some View {
        HStack(spacing: 12) {
            Text("\(rank.level)").font(.fit(.titleMedium).weight(.bold)).foregroundStyle(Ink.bg)
                .frame(width: 44, height: 44).background(Circle().fill(Ink.accent))
            VStack(alignment: .leading, spacing: 6) {
                Text("Level \(rank.level) · \(Shop.titleLabel(title) ?? rank.title)").font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                Meter(fraction: rank.fraction)
                Text(L("\(rank.into) / \(rank.need) XP bis Level \(rank.level + 1)", "\(rank.into) / \(rank.need) XP to level \(rank.level + 1)"))
                    .font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/** Coin balance (or a gain) as a small pill. */
struct CoinBadge: View {
    let coins: Int
    var gain = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "circle.circle.fill").foregroundStyle(Ink.accent)
            Text(verbatim: gain ? "+\(coins)" : "\(coins)").font(.fit(.label).weight(.bold)).foregroundStyle(Ink.text)
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(Capsule().fill(Ink.raised))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(gain ? L("\(coins) Münzen dazu", "\(coins) coins gained") : L("\(coins) Münzen", "\(coins) coins"))
    }
}

/** Level with its XP bar and coins, today's three quests and the week's two side quests. */
struct QuestCard: View {
    @Environment(Store.self) private var store

    var body: some View {
        let d = store.data
        let daily = Quests.daily(d)
        let weekly = Quests.weekly(d)
        Card {
            LevelRow(rank: Quests.level(Quests.xp(d)), title: d.title)
            HStack {
                CoinBadge(coins: Shop.balance(d))
                Spacer()
                NavigationLink { ShopView() } label: {
                    Text(L("Shop, Skills und Sammlungen", "Shop, skills and collections")).font(.fit(.label)).foregroundStyle(Ink.accent)
                }
                .accessibilityIdentifier("shop")
            }
            .padding(.top, 4)
            SectionTitle(text: L("Quests heute", "Today's quests"))
            ForEach(daily) { QuestRow(quest: $0) }
            SectionTitle(text: L("Nebenquests der Woche", "Side quests this week"))
            ForEach(weekly) { QuestRow(quest: $0) }
        }
        // Finished quests pay out as soon as they show as done.
        .onAppear { store.claimQuests() }
        .onChange(of: daily.filter(\.done).count + weekly.filter(\.done).count) { store.claimQuests() }
        .sensoryFeedback(.success, trigger: d.questsDone)
    }
}

private struct QuestRow: View {
    let quest: Quests.Quest

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: quest.done ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 18)).foregroundStyle(quest.done ? Ink.accent : Ink.faint)
            Text(quest.title).font(.fit(.bodyMedium)).foregroundStyle(quest.done ? Ink.muted : Ink.text).lineLimit(2)
            Spacer(minLength: 6)
            if !quest.done && quest.target > 1 {
                Text(quest.label).font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
            }
            Text("+\(quest.xp) XP").font(.fit(.labelSmall).weight(.semibold)).foregroundStyle(quest.done ? Ink.accent : Ink.muted)
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
        .accessibilityValue(quest.done ? L("erledigt", "done") : quest.label)
    }
}

/** This week's challenge: what to do, how far along, and a word of praise once it is done. */
struct ChallengeCard: View {
    @Environment(Store.self) private var store

    var body: some View {
        let c = Motivation.challenge(store.data)
        let completed = store.data.challengesDone.count
        Card {
            HStack {
                SectionTitle(text: L("Wochen-Challenge", "Weekly challenge"), color: c.done ? Ink.accent : Ink.muted).padding(.top, -10)
                if c.done { Tag(text: L("Geschafft", "Done"), color: Ink.accent) } else { Text(c.label).font(.fit(.label)).foregroundStyle(Ink.muted) }
            }
            Text(c.title).font(.fit(.titleLarge)).foregroundStyle(Ink.text)
            Meter(fraction: c.fraction)
            Text(c.done ? L("Stark. Das ist deine \(max(1, completed)). geschaffte Challenge.", "Nice work. That makes \(max(1, completed)) \(completed <= 1 ? "challenge" : "challenges") done.") : c.detail)
                .font(.fit(.bodySmall)).foregroundStyle(c.done ? Ink.text : Ink.muted)
        }
        // A challenge met is booked once (100 XP, 50 coins).
        .onAppear { if c.done { store.completeChallenge(c.week) } }
        .onChange(of: c.done) { _, done in if done { store.completeChallenge(c.week) } }
    }
}

/** Coins buy rerolls, boosters, freezes, colours and titles; skills and collections show where they come from. */
struct ShopView: View {
    @Environment(Store.self) private var store
    @State private var tab = 0

    var body: some View {
        let d = store.data
        let coins = Shop.balance(d)
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Text(L("Münzen für Training, Rekorde und Quests", "Coins for training, records and quests")).font(.fit(.body)).foregroundStyle(Ink.muted)
                    Spacer()
                    CoinBadge(coins: coins)
                }
                HStack(spacing: 8) {
                    Chip(text: L("Shop", "Shop"), on: tab == 0) { tab = 0 }
                    Chip(text: L("Skills", "Skills"), on: tab == 1) { tab = 1 }
                    Chip(text: L("Sammlungen", "Collections"), on: tab == 2) { tab = 2 }
                    Spacer()
                }
                switch tab {
                case 0:
                    ForEach(Shop.items()) { ShopRow(item: $0, coins: coins) }
                case 1:
                    Text(L("Jede Muskelgruppe steigt mit deinen Arbeitssätzen im Level. Jedes Level bringt 10 Münzen.",
                           "Each muscle group levels up with your working sets. Every level pays 10 coins."))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(Shop.skills(d)) { s in
                        Card(padding: 16) {
                            HStack {
                                Text(s.name).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
                                Spacer()
                                Text("Level \(s.level)").font(.fit(.label).weight(.bold)).foregroundStyle(Ink.accent)
                            }
                            Meter(fraction: s.fraction)
                            Text(L("\(s.into) / \(s.need) Sätze bis Level \(s.level + 1) · \(s.sets) gesamt", "\(s.into) / \(s.need) sets to level \(s.level + 1) · \(s.sets) total"))
                                .font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
                        }
                    }
                default:
                    let list = Shop.collections(d)
                    Text(L("Jede Übung hat eine Sammlung mit Stufen I bis X (ab 10, 25, 50, 100, 250 Sätzen). Jede neue Stufe bringt Münzen.",
                           "Every exercise has a collection with tiers I to X (from 10, 25, 50, 100, 250 sets). Every new tier pays coins."))
                        .font(.fit(.bodySmall)).foregroundStyle(Ink.muted).frame(maxWidth: .infinity, alignment: .leading)
                    if list.isEmpty {
                        Card { Text(L("Nach dem ersten Training füllt sich das hier.", "This fills up after your first workout.")).font(.fit(.body)).foregroundStyle(Ink.muted) }
                    }
                    ForEach(list) { c in
                        let e = ExerciseRepo.shared[c.exerciseId]
                        Card(padding: 14) {
                            HStack(spacing: 12) {
                                ExerciseThumb(exercise: e, size: 44)
                                VStack(alignment: .leading, spacing: 5) {
                                    Text(e?.title ?? c.exerciseId).font(.fit(.titleSmall)).foregroundStyle(Ink.text).lineLimit(1)
                                    Meter(fraction: c.fraction)
                                    Text(c.next.map { L("\(c.sets) / \($0) Sätze", "\(c.sets) / \($0) sets") } ?? L("\(c.sets) Sätze · voll", "\(c.sets) sets · maxed"))
                                        .font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
                                }
                                Text(c.tier > 0 ? Shop.roman(c.tier) : "–").font(.fit(.titleLarge).weight(.bold))
                                    .foregroundStyle(c.tier > 0 ? Ink.accent : Ink.faint)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .screen()
        .accessibilityIdentifier("shopScreen")
        .navigationTitle(L("Shop", "Shop"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ShopRow: View {
    @Environment(Store.self) private var store
    let item: Shop.Item
    let coins: Int
    @State private var bought = 0

    var body: some View {
        let d = store.data
        let unlocked = Shop.unlocked(d, item)
        let available = Shop.available(d, item)
        let owned = d.owned.contains(item.key)
        Card(padding: 16) {
            HStack(spacing: 12) {
                ShopIcon(item: item, locked: !unlocked)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.title).font(.fit(.titleSmall)).foregroundStyle(Ink.text)
                    // Rarity in its colour, and the level it waits for while locked.
                    Text(item.rarity.label + (unlocked ? "" : L(" · ab Level \(item.level)", " · from level \(item.level)")))
                        .font(.fit(.labelSmall).weight(.semibold)).foregroundStyle(rarityColor(item.rarity))
                    Text(item.detail).font(.fit(.bodySmall)).foregroundStyle(Ink.muted)
                    if item.kind == .freeze {
                        Text(L("Vorrat: \(d.freezes) von \(Shop.freezeMaxBought)", "In stock: \(d.freezes) of \(Shop.freezeMaxBought)"))
                            .font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
                    }
                    if item.kind == .boost && unlocked && !available {
                        Text(L("Läuft", "Running")).font(.fit(.labelSmall)).foregroundStyle(Ink.accent)
                    }
                }
                Spacer(minLength: 6)
                if owned && item.kind == .title {
                    // A bought title can be worn, or taken off again.
                    let key = String(item.key.dropFirst("title:".count))
                    let worn = d.title == key
                    Button(worn ? L("Aktiv", "Worn") : L("Anlegen", "Wear")) { store.setTitle(worn ? "" : key) }
                        .buttonStyle(PillButtonStyle(filled: worn))
                } else if owned {
                    Tag(text: L("Gekauft", "Owned"), color: Ink.accent)
                } else {
                    Button { if store.buy(item) { bought += 1 } } label: {
                        Label("\(item.price)", systemImage: "circle.circle.fill").labelStyle(.titleAndIcon)
                    }
                    .buttonStyle(PillButtonStyle(filled: available && coins >= item.price))
                    .disabled(!available || coins < item.price)
                    .accessibilityLabel(L("Für \(item.price) Münzen kaufen", "Buy for \(item.price) coins"))
                }
            }
        }
        .sensoryFeedback(.success, trigger: bought)
    }
}

/** Frame colour of a shop item by rarity, as on Android. */
func rarityColor(_ r: Shop.Rarity) -> Color {
    switch r {
    case .common: Color(hex: 0x9AA1AA)
    case .rare: Color(hex: 0x4F9FE0)
    case .epic: Color(hex: 0xA56FE0)
    case .legendary: Color(hex: 0xE5A23A)
    case .mythic: Color(hex: 0xE0568C)
    }
}

/** A rarity frame with a die, a bolt, a snowflake, a gem in the colour on sale, or a medal (crown from legendary up). */
struct ShopIcon: View {
    let item: Shop.Item
    var size: CGFloat = 48
    var locked = false

    var body: some View {
        let frame = rarityColor(item.rarity).opacity(locked ? 0.45 : 1)
        let symbol: String = switch item.kind {
        case .reroll: "die.face.5.fill"
        case .boost: "bolt.fill"
        case .freeze: "snowflake"
        case .accent: "diamond.fill"
        case .title: item.rarity >= .legendary ? "crown.fill" : "medal.fill"
        }
        let accent = Accent(rawValue: String(item.key.dropFirst("accent:".count)))
        Image(systemName: locked ? "lock.fill" : symbol)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(item.kind == .accent && !locked ? Color(hex: accent?.tones.ink ?? 0xFFFFFF) : frame)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).fill(frame.opacity(0.16)))
            .overlay(RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).stroke(frame, lineWidth: size * 0.05))
            .accessibilityHidden(true)
    }
}
