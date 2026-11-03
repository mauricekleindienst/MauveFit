import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255
        )
    }
}

/**
 * The Android app's "iron" palette: cool graphite like iron and rubber flooring, with the sage
 * accent. Text on an accent fill uses [bg], which contrasts with every accent.
 */
enum Ink {
    static let bg = Color(hex: 0x111315)
    static let surface = Color(hex: 0x1A1D21)
    static let raised = Color(hex: 0x23272C)
    static let line = Color(hex: 0x2E3338)
    static let lineStrong = Color(hex: 0x4A5058)
    static let text = Color(hex: 0xEDEFF2)
    static let muted = Color(hex: 0xA6ACB4)
    static let faint = Color(hex: 0x899099)
    static let coral = Color(hex: 0xEE8F6A)
    static let red = Color(hex: 0xF0736A)
    static let accent = Color(hex: 0x9DC3A3)
    /** Secondary signal colour (rest, recovery). */
    static let mauve = Color(hex: 0xC5A9E6)
}

/** A rounded surface with a hairline border, the basic block of every screen. */
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Ink.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Ink.line, lineWidth: 1))
    }
}

/** Capsule button: filled with the accent for the main action, outlined otherwise. */
struct PillButtonStyle: ButtonStyle {
    var filled = true
    var color: Color = Ink.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.subheadline, design: .rounded).weight(.semibold))
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .foregroundStyle(filled ? Ink.bg : color)
            .background(Capsule().fill(filled ? color : Color.clear))
            .overlay(Capsule().stroke(filled ? Color.clear : Ink.lineStrong, lineWidth: 1))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(duration: 0.2), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PillButtonStyle {
    static var pill: PillButtonStyle { PillButtonStyle() }
    static var pillOutline: PillButtonStyle { PillButtonStyle(filled: false, color: Ink.text) }
}

/** Small label above a block, in caps like the Android section titles. */
struct SectionTitle: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.system(.caption, design: .rounded).weight(.semibold))
            .tracking(1.2)
            .foregroundStyle(Ink.faint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }
}

/** A big number with a caption, for stat rows. */
struct StatTile: View {
    let value: String
    let label: String
    var color: Color = Ink.text

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.system(.title2, design: .rounded).weight(.bold)).foregroundStyle(color)
                .lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.caption).foregroundStyle(Ink.muted).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
                Capsule().fill(color).frame(width: g.size.width * min(1, max(0, fraction)))
            }
        }
        .frame(height: 8)
    }
}

/** The exercise still from the dataset, on black like the Android "stage". */
struct ExerciseThumb: View {
    let exercise: Exercise?
    var size: CGFloat = 48

    var body: some View {
        AsyncImage(url: exercise?.imageUrl) { phase in
            if let img = phase.image {
                img.resizable().scaledToFit()
            } else {
                Image(systemName: "dumbbell.fill").foregroundStyle(Ink.faint)
            }
        }
        .frame(width: size, height: size)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

extension View {
    /** The common screen background and scroll padding. */
    func screen() -> some View {
        self.scrollContentBackground(.hidden)
            .background(Ink.bg.ignoresSafeArea())
    }
}
