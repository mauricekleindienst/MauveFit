import CoreText
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

    var id: String { rawValue }

    var label: String {
        switch self {
        case .copper: L("Kupfer", "Copper")
        case .sand: L("Sand", "Sand")
        case .sage: L("Salbei", "Sage")
        case .blue: L("Blau", "Blue")
        case .mauve: L("Mauve", "Mauve")
        case .lime: L("Lime", "Lime")
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
    /** What the exercise pictures sit on: black in dark mode (they are inverted), their own white in light mode. */
    static var stage: Color { Color(uiColor: .tone(0x000000, 0xFFFFFF)) }
    static var coral: Color { Color(uiColor: .tone(0xEE8F6A, 0xA84D2A)) }
    static var red: Color { Color(uiColor: .tone(0xF0736A, 0xB3362C)) }
    static var accent: Color { Color(uiColor: accentUI) }
    /** Secondary signal colour (rest, recovery, water). */
    static var mauve: Color { Color(uiColor: .tone(0xC5A9E6, 0x7955A0)) }
}

/**
 * Archivo, the Android app's one typeface, set at different widths: extra-wide and heavy like a
 * scoreboard for titles and numbers, normal width for reading. Copied from the Android resources
 * at build time; the system font stands in if it is missing.
 */
enum Archivo {
    nonisolated(unsafe) private static let base: CTFontDescriptor? = {
        guard let url = Bundle.main.url(forResource: "archivo", withExtension: "ttf"),
              let all = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor] else { return nil }
        return all.first
    }()
    nonisolated(unsafe) private static var cache: [String: UIFont] = [:]

    private static let weightAxis = NSNumber(value: 0x7767_6874) as CFNumber // "wght"
    private static let widthAxis = NSNumber(value: 0x7764_7468) as CFNumber // "wdth"

    static func ui(_ size: CGFloat, weight: CGFloat, width: CGFloat = 100) -> UIFont {
        let key = "\(size)-\(weight)-\(width)"
        if let f = cache[key] { return f }
        let font: UIFont
        if var d = base {
            d = CTFontDescriptorCreateCopyWithVariation(d, weightAxis, weight)
            d = CTFontDescriptorCreateCopyWithVariation(d, widthAxis, width)
            font = CTFontCreateWithFontDescriptor(d, size, nil) as UIFont
        } else {
            let w: UIFont.Weight = weight >= 800 ? .heavy : weight >= 700 ? .bold : weight >= 600 ? .semibold : weight >= 500 ? .medium : .regular
            font = .systemFont(ofSize: size, weight: w, width: width > 110 ? .expanded : .standard)
        }
        cache[key] = font
        return font
    }
}

/** The Android type scale (FitTheme): Material sizes with Archivo at the widths per role. */
enum FitType {
    case display, title, headline, headlineSmall, titleLarge, titleMedium, titleSmall
    case body, bodyMedium, bodySmall, label, labelSmall, number, numberLarge

    var spec: (size: CGFloat, weight: CGFloat, width: CGFloat, style: UIFont.TextStyle) {
        switch self {
        case .display: (45, 800, 125, .largeTitle)
        case .title: (32, 800, 125, .largeTitle)
        case .headline: (26, 700, 125, .title1)
        case .headlineSmall: (21, 700, 114, .title2)
        case .titleLarge: (19, 650, 114, .title3)
        case .titleMedium: (16, 600, 100, .headline)
        case .titleSmall: (14, 600, 100, .subheadline)
        case .body: (16, 400, 100, .body)
        case .bodyMedium: (14, 400, 100, .subheadline)
        case .bodySmall: (12, 400, 100, .footnote)
        case .label: (14, 600, 100, .subheadline)
        case .labelSmall: (12, 500, 100, .caption1)
        // Figures in tables and counters: semi-wide, so digits read at a glance mid-set.
        case .number: (17, 700, 112, .body)
        case .numberLarge: (34, 700, 112, .largeTitle)
        }
    }

    /** Scaled with the system text size, like Material's sp. */
    var uiFont: UIFont {
        let s = spec
        return UIFontMetrics(forTextStyle: s.style).scaledFont(for: Archivo.ui(s.size, weight: s.weight, width: s.width))
    }
}

extension Font {
    static func fit(_ t: FitType) -> Font { Font(t.uiFont as CTFont) }
}

/** Radii by rank, as on Android: heroes curve most, panels less, rows least. */
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

/** Capsule button: the accent at full strength for the main action, a quiet tonal fill otherwise. */
struct PillButtonStyle: ButtonStyle {
    var filled = true
    var color: Color = Ink.accent
    var wide = false
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.fit(.titleMedium).weight(filled ? .bold : .semibold))
            .lineLimit(1)
            .padding(.horizontal, 18)
            .padding(.vertical, wide ? 17 : 12)
            .frame(maxWidth: wide ? .infinity : nil)
            .foregroundStyle(filled ? Ink.bg : color)
            .background(Capsule().fill(filled ? color : Ink.raised))
            .opacity(enabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.55), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PillButtonStyle {
    static var pill: PillButtonStyle { PillButtonStyle() }
    static var pillOutline: PillButtonStyle { PillButtonStyle(filled: false, color: Ink.text) }
    static var primary: PillButtonStyle { PillButtonStyle(wide: true) }
    static var ghost: PillButtonStyle { PillButtonStyle(filled: false, color: Ink.text, wide: true) }
}

/** Small heading above a block, like the Android Eyebrow. */
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

/** Screen title: the wide heading first, one plain line of context under it. */
struct ScreenTitle<Trailing: View>: View {
    let title: String
    var eyebrow: String = ""
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.fit(.title)).tracking(-0.8).foregroundStyle(Ink.text)
                    .lineLimit(1).minimumScaleFactor(0.6)
                if !eyebrow.isEmpty { Text(eyebrow).font(.fit(.body)).foregroundStyle(Ink.muted) }
            }
            Spacer(minLength: 8)
            trailing
        }
        .padding(.top, 12)
        .padding(.bottom, 6)
    }
}

extension ScreenTitle where Trailing == EmptyView {
    init(title: String, eyebrow: String = "") { self.init(title: title, eyebrow: eyebrow) { EmptyView() } }
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

/**
 * The exercise still from the dataset on the stage colour. In dark mode the white picture is
 * inverted and its hue turned back, so it becomes a dark X-ray look with the target muscles
 * still red, like the Android "smart invert".
 */
struct ExerciseMedia: View {
    let exercise: Exercise?
    var radius: CGFloat = 16
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ZStack {
            Ink.stage
            AsyncImage(url: exercise?.imageUrl) { phase in
                if let img = phase.image {
                    if scheme == .dark {
                        img.resizable().scaledToFit().colorInvert().hueRotation(.degrees(180))
                    } else {
                        img.resizable().scaledToFit()
                    }
                } else {
                    Image(systemName: "dumbbell.fill").foregroundStyle(Ink.faint)
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

struct ExerciseThumb: View {
    let exercise: Exercise?
    var size: CGFloat = 56

    var body: some View {
        ExerciseMedia(exercise: exercise, radius: size >= 48 ? 16 : 12).frame(width: size, height: size)
    }
}

extension View {
    /** The common screen background. */
    func screen() -> some View {
        self.scrollContentBackground(.hidden)
            .background(Ink.bg.ignoresSafeArea())
    }

    /** A tab's root: no system bar, the screen draws its own wide title. */
    func tabRoot() -> some View {
        self.screen().toolbar(.hidden, for: .navigationBar)
    }
}
