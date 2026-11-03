import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1
        )
    }

    /** One colour for the dark "iron" palette and one for the light "chalk" palette. */
    static func tone(_ dark: UInt32, _ light: UInt32) -> UIColor {
        UIColor { $0.userInterfaceStyle == .light ? UIColor(hex: light) : UIColor(hex: dark) }
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
}

/**
 * Accent choices of the Android app (ui/Theme.kt); each has a tone for ink and one for paper so
 * it keeps contrast in both. The shop's premium accents are not ported.
 */
enum Accent: String, CaseIterable, Identifiable {
    case copper, sand, sage, blue, mauve, lime
    // Bought in the shop (Shop.premiumAccents).
    case gold, ruby, emerald, ocean, rose

    /** The accents everyone has; the others are unlocked in the shop. */
    static let free: [Accent] = [.copper, .sand, .sage, .blue, .mauve, .lime]
    var premium: Bool { !Accent.free.contains(self) }

    var id: String { rawValue }

    var label: String {
        switch self {
        case .copper: L("Kupfer", "Copper")
        case .sand: L("Sand", "Sand")
        case .sage: L("Salbei", "Sage")
        case .blue: L("Blau", "Blue")
        case .mauve: L("Mauve", "Mauve")
        case .lime: L("Lime", "Lime")
        case .gold: L("Gold", "Gold")
        case .ruby: L("Rubin", "Ruby")
        case .emerald: L("Smaragd", "Emerald")
        case .ocean: L("Ozean", "Ocean")
        case .rose: L("Rosé", "Rose")
        }
    }

    var tones: (ink: UInt32, paper: UInt32) {
        switch self {
        case .copper: (0xDB9062, 0x9E542A)
        case .sand: (0xD9B88B, 0x8C5D30)
        case .sage: (0x9DC3A3, 0x497151)
        case .blue: (0x8FB3D2, 0x3D6180)
        case .mauve: (0xC5A9E6, 0x7955A0)
        case .lime: (0xD5F64E, 0x557100)
        // Light variants tuned to 4.5:1 like the rest, as on Android.
        case .gold: (0xE8C15A, 0x826412)
        case .ruby: (0xEE7F8E, 0xCB1B33)
        case .emerald: (0x5FD3A2, 0x207652)
        case .ocean: (0x5FC4E8, 0x156F8F)
        case .rose: (0xE6A0C4, 0xB83076)
        }
    }

    static func of(_ key: String) -> Accent { Accent(rawValue: key) ?? .copper }
}

/**
 * Colours of a strength gym, as on Android: cool graphite like iron and rubber flooring in dark
 * mode, chalk-white in light mode, with one accent the user picks. Every colour follows the
 * system (or the theme setting) by itself. Text on an accent fill uses [bg].
 */
enum Ink {
    /** Set from the settings by the app before anything is drawn. */
    nonisolated(unsafe) static var accentChoice: Accent = .copper

    static var bgUI: UIColor { .tone(0x111315, 0xF2F3F5) }
    static var surfaceUI: UIColor { .tone(0x1A1D21, 0xFFFFFF) }
    static var textUI: UIColor { .tone(0xEDEFF2, 0x121417) }
    static var mutedUI: UIColor { .tone(0xA6ACB4, 0x4E555E) }
    static var accentUI: UIColor { .tone(accentChoice.tones.ink, accentChoice.tones.paper) }

    static var bg: Color { Color(uiColor: bgUI) }
    static var surface: Color { Color(uiColor: surfaceUI) }
    static var raised: Color { Color(uiColor: .tone(0x23272C, 0xE8EAED)) }
    static var line: Color { Color(uiColor: .tone(0x2E3338, 0xD5D9DE)) }
    static var lineStrong: Color { Color(uiColor: .tone(0x4A5058, 0x9AA1AA)) }
    static var text: Color { Color(uiColor: textUI) }
    static var muted: Color { Color(uiColor: mutedUI) }
    static var faint: Color { Color(uiColor: .tone(0x899099, 0x626972)) }
    /** What the muscle maps sit on. */
    static var stage: Color { Color(uiColor: .tone(0x0C0D0F, 0xE8EAED)) }
    /** Body regions that are not trained. */
    static var body: Color { Color(uiColor: .tone(0x2E3338, 0xC9CED4)) }
    static var coral: Color { Color(uiColor: .tone(0xEE8F6A, 0xA84D2A)) }
    static var red: Color { Color(uiColor: .tone(0xF0736A, 0xB3362C)) }
    static var accent: Color { Color(uiColor: accentUI) }
    /** Secondary signal colour (rest, recovery, water). */
    static var mauve: Color { Color(uiColor: .tone(0xC5A9E6, 0x7955A0)) }
}

/**
 * The type scale: San Francisco in the system's text styles, so it scales with Dynamic Type and
 * sits right next to the native large titles. Titles and numbers are heavy and rounded.
 */
enum FitType {
    case display, title, headline, headlineSmall, titleLarge, titleMedium, titleSmall
    case body, bodyMedium, bodySmall, label, labelSmall, number, numberLarge

    var font: Font {
        switch self {
        case .display: .system(.largeTitle, design: .rounded).weight(.heavy)
        case .title: .system(.largeTitle).weight(.bold)
        case .headline: .system(.title, design: .rounded).weight(.bold)
        case .headlineSmall: .system(.title2, design: .rounded).weight(.bold)
        case .titleLarge: .system(.title3).weight(.semibold)
        case .titleMedium: .system(.headline)
        case .titleSmall: .system(.subheadline).weight(.semibold)
        case .body: .system(.body)
        case .bodyMedium: .system(.subheadline)
        case .bodySmall: .system(.footnote)
        case .label: .system(.subheadline).weight(.semibold)
        case .labelSmall: .system(.caption).weight(.medium)
        // Figures in tables and counters: rounded and even-width, so digits read at a glance mid-set.
        case .number: .system(.body, design: .rounded).weight(.bold).monospacedDigit()
        case .numberLarge: .system(.largeTitle, design: .rounded).weight(.bold).monospacedDigit()
        }
    }
}

extension Font {
    static func fit(_ t: FitType) -> Font { t.font }
}

/** Radii by rank: heroes curve most, panels less, rows least. */
enum Radius {
    static let hero: CGFloat = 32
    static let panel: CGFloat = 24
    static let row: CGFloat = 18
}

/** Tonal panel: set apart by surface colour alone, no outline. */
struct Card<Content: View>: View {
    var padding: CGFloat = 20
    var color: Color = Ink.surface
    var radius: CGFloat = Radius.panel
    var spacing: CGFloat = 10
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

/**
 * Capsule button. On iOS 26 it is Liquid Glass: tinted ("prominent") glass for the main action,
 * clear glass otherwise. Earlier systems get a filled or tonal capsule.
 */
struct PillButtonStyle: PrimitiveButtonStyle {
    var filled = true
    var color: Color = Ink.accent
    var wide = false

    func makeBody(configuration: Configuration) -> some View {
        if #available(iOS 26, *) {
            let label = configuration.label
                .font(.fit(.titleMedium))
                .lineLimit(wide ? 2 : 1)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: wide ? .infinity : nil)
                .padding(.vertical, wide ? 6 : 0)
            if filled {
                // The page colour on the tint, as on Android: white was hard to read on the light dark-mode accents.
                Button(role: configuration.role, action: configuration.trigger) { label.foregroundStyle(Ink.bg) }
                    .buttonStyle(.glassProminent).tint(color).controlSize(wide ? .large : .regular)
            } else {
                Button(role: configuration.role, action: configuration.trigger) { label.foregroundStyle(color) }
                    .buttonStyle(.glass).controlSize(wide ? .large : .regular)
            }
        } else {
            Button(role: configuration.role, action: configuration.trigger) { configuration.label }
                .buttonStyle(LegacyPill(filled: filled, color: color, wide: wide))
        }
    }
}

/** The capsule before iOS 26: filled with the colour, or a quiet tonal fill. */
private struct LegacyPill: ButtonStyle {
    let filled: Bool
    let color: Color
    let wide: Bool
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.fit(.titleMedium).weight(filled ? .bold : .semibold))
            .lineLimit(wide ? 2 : 1)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18)
            .padding(.vertical, wide ? 16 : 12)
            .frame(maxWidth: wide ? .infinity : nil)
            .foregroundStyle(filled ? Ink.bg : color)
            .background(Capsule().fill(filled ? color : Ink.raised))
            .opacity(enabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.55), value: configuration.isPressed)
    }
}

extension PrimitiveButtonStyle where Self == PillButtonStyle {
    static var pill: PillButtonStyle { PillButtonStyle() }
    static var pillOutline: PillButtonStyle { PillButtonStyle(filled: false, color: Ink.text) }
    static var primary: PillButtonStyle { PillButtonStyle(wide: true) }
    static var ghost: PillButtonStyle { PillButtonStyle(filled: false, color: Ink.text, wide: true) }
}

/** Small heading above a block. */
struct SectionTitle: View {
    let text: String
    var color: Color = Ink.muted
    var body: some View {
        Text(text)
            .font(.fit(.titleSmall))
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 10)
    }
}

/**
 * A tab's title row: the large bold title with its action on the same line, centred on the
 * title, and one line of context under it. Replaces the system bar, which put the button in a
 * row of its own above the title.
 */
struct TabHeader<Trailing: View>: View {
    let title: String
    var subtitle: String = ""
    @ViewBuilder var trailing: Trailing

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .center) {
                Text(title).font(.fit(.title)).foregroundStyle(Ink.text)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                trailing
            }
            if !subtitle.isEmpty { Text(subtitle).font(.fit(.body)).foregroundStyle(Ink.muted) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }
}

/** A round icon button for the title row: Liquid Glass on iOS 26, thin material before it. */
struct GlassIconButton: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Group {
            if #available(iOS 26, *) {
                Button(action: action) {
                    Image(systemName: icon).font(.system(size: 18, weight: .semibold)).frame(width: 30, height: 30)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
            } else {
                Button(action: action) {
                    Image(systemName: icon).font(.system(size: 18, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .buttonStyle(.plain)
            }
        }
        .tint(Ink.accent)
        .foregroundStyle(Ink.accent)
        .accessibilityLabel(label)
    }
}

/** A round icon button on the raised tone. */
struct RoundButton: View {
    let icon: String
    let label: String
    var size: CGFloat = 40
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(Ink.text)
                .frame(width: size, height: size)
                .background(Circle().fill(Ink.raised))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/** A big number with a caption; a tile of its own unless [boxed] is off. */
struct StatTile: View {
    let value: String
    let label: String
    var color: Color = Ink.text
    var boxed = true

    var body: some View {
        let inner = VStack(alignment: .leading, spacing: 2) {
            // The wide face runs out of room fast in a third of the screen; long values step down.
            Text(value).font(.fit(value.count > 5 ? .headlineSmall : .headline)).foregroundStyle(color)
                .lineLimit(1).minimumScaleFactor(0.5)
                .frame(height: 34, alignment: .leading)
            Text(label).font(.fit(.labelSmall)).foregroundStyle(Ink.muted).lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // Three tiles share a row; beyond this size the labels would break inside words.
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        if boxed {
            inner.padding(16).background(Ink.surface, in: RoundedRectangle(cornerRadius: Radius.panel, style: .continuous))
        } else {
            inner
        }
    }
}

/** Filter chip: raised when off, the accent when on. */
struct Chip: View {
    let text: String
    var on = false
    var icon: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if let icon { Image(systemName: icon).font(.caption) }
                Text(text)
            }
            .font(.fit(.label))
            .padding(.horizontal, 16).padding(.vertical, 9)
            .foregroundStyle(on ? Ink.bg : Ink.text)
            .background(Capsule().fill(on ? Ink.accent : Ink.raised))
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: on)
    }
}

/** Small read-only tag. */
struct Tag: View {
    let text: String
    var color: Color = Ink.muted
    var body: some View {
        Text(text).font(.fit(.labelSmall)).foregroundStyle(color)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(Capsule().fill(Ink.raised))
    }
}

/** A thin progress bar in the accent. */
struct Meter: View {
    let fraction: Double
    var color: Color = Ink.accent

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(Ink.raised)
                Capsule().fill(color).frame(width: g.size.width * min(1, max(0, fraction.isFinite ? fraction : 0)))
            }
        }
        .frame(height: 6)
        .animation(.easeOut(duration: 0.5), value: fraction)
    }
}

extension View {
    /** The common screen background. */
    func screen() -> some View {
        self.scrollContentBackground(.hidden)
            .background(Ink.bg.ignoresSafeArea())
    }

    /**
     * The floating control layer (rest timer, workout bar): Liquid Glass on iOS 26, a thin
     * material before it. Content cards never get this, as the HIG asks.
     */
    /** A tab's root: no system bar, the screen draws its own title row (TabHeader). */
    func tabRoot() -> some View {
        self.screen().toolbar(.hidden, for: .navigationBar)
    }

    /** Lets glass pieces next to each other blend and morph together (iOS 26). */
    @ViewBuilder
    func glassGroup(spacing: CGFloat = 12) -> some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: spacing) { self }
        } else {
            self
        }
    }

    @ViewBuilder
    func floatingGlass<S: Shape>(in shape: S) -> some View {
        if #available(iOS 26, *) {
            self.glassEffect(.regular, in: shape)
        } else {
            self.background(.ultraThinMaterial, in: shape)
        }
    }
}
