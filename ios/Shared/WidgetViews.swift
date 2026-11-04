import SwiftUI
import UIKit

/** The widget's palette: the app's iron and chalk tones, following the system appearance. */
enum WidgetInk {
    private static func tone(_ dark: UInt32, _ light: UInt32) -> Color {
        Color(uiColor: UIColor { t in
            let hex = t.userInterfaceStyle == .light ? light : dark
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        })
    }

    static let bg = tone(0x111315, 0xF2F3F5)
    static let surface = tone(0x1A1D21, 0xFFFFFF)
    static let raised = tone(0x23272C, 0xE8EAED)
    static let text = tone(0xEDEFF2, 0x121417)
    static let muted = tone(0xA6ACB4, 0x4E555E)
    static let onAccent = tone(0x111315, 0xF2F3F5)
    static let mauve = tone(0xC5A9E6, 0x7955A0)
    static func accent(_ s: WidgetSnapshot) -> Color { tone(s.accentDark, s.accentLight) }
}

enum WidgetSize { case small, medium, large }

/**
 * The next workout (or the running one) and this week's progress, like the Android widget: one
 * line with the week count (small), the card with its button (medium), and the week with the
 * challenge and the weekday strip (large).
 */
struct NextWidgetView: View {
    let s: WidgetSnapshot
    let size: WidgetSize

    var body: some View {
        if s.running {
            running
        } else {
            switch size {
            case .small: small
            case .medium: VStack(alignment: .leading, spacing: 0) { head; Spacer(minLength: 8); weekBar(strip: false) }
            case .large: VStack(alignment: .leading, spacing: 0) { head; Spacer(minLength: 8); challenge; Spacer(minLength: 10); weekBar(strip: true) }
            }
        }
    }

    private var accent: Color { WidgetInk.accent(s) }

    private func eyebrow(_ text: String) -> some View {
        Text(text).font(.caption.weight(.semibold)).foregroundStyle(accent).lineLimit(1)
    }

    private func pill(_ label: String, _ link: String) -> some View {
        Link(destination: URL(string: link)!) {
            Text(label).font(.subheadline.weight(.bold)).foregroundStyle(WidgetInk.onAccent)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(Capsule().fill(accent))
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            eyebrow(s.eyebrow)
            Text(s.title ?? s.empty).font(.headline).foregroundStyle(WidgetInk.text).lineLimit(2)
            Spacer(minLength: 4)
            Text("\(s.week)/\(s.goal)").font(.system(.title, design: .rounded).weight(.bold)).foregroundStyle(accent)
            Text(s.weekLabel).font(.caption2).foregroundStyle(WidgetInk.muted).lineLimit(1)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var head: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                eyebrow(s.eyebrow)
                Text(s.title ?? s.empty).font(.title3.weight(.bold)).foregroundStyle(WidgetInk.text).lineLimit(2)
                Text(s.line).font(.footnote).foregroundStyle(WidgetInk.muted).lineLimit(2)
            }
            Spacer(minLength: 0)
            if let button = s.button { pill(button, s.buttonLink) }
        }
    }

    private var challenge: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(s.challengeTitle).font(.footnote.weight(.medium)).foregroundStyle(WidgetInk.text).lineLimit(1)
                Spacer(minLength: 8)
                Text(s.challengeLabel).font(.footnote.weight(.bold)).foregroundStyle(s.challengeDone ? accent : WidgetInk.mauve).lineLimit(1)
            }
            bar(s.challengeFraction, WidgetInk.mauve)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(WidgetInk.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func bar(_ fraction: Double, _ color: Color) -> some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(WidgetInk.raised)
                Capsule().fill(color).frame(width: g.size.width * min(1, max(0, fraction)))
            }
        }
        .frame(height: 6)
    }

    private func weekBar(strip: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if strip {
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { i in
                        VStack(spacing: 4) {
                            Text(s.weekdayLetters[i]).font(.caption2).foregroundStyle(i == s.todayIndex ? WidgetInk.text : WidgetInk.muted)
                            Circle().fill(s.trained[i] ? accent : WidgetInk.raised)
                                .overlay(Circle().stroke(i == s.todayIndex && !s.trained[i] ? accent : .clear, lineWidth: 1.5))
                                .frame(width: 22, height: 22)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            HStack {
                Text(s.weekLabel).font(.footnote).foregroundStyle(WidgetInk.muted).lineLimit(1)
                Spacer()
                Text("\(s.week)/\(s.goal)").font(.subheadline.weight(.bold)).foregroundStyle(accent)
            }
            bar(Double(s.week) / Double(max(1, s.goal)), accent)
        }
    }

    /** A workout runs: the rest counting down, or the set to do now, and the sets done. */
    private var running: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                eyebrow(s.restEndsAt != nil ? (size == .small ? s.restShort : s.restLabel) : s.eyebrow)
                Spacer(minLength: 4)
                if size != .small, let button = s.button { pill(button, s.buttonLink) }
            }
            if let end = s.restEndsAt, end > Date() {
                Text(timerInterval: Date()...end, countsDown: true)
                    .font(.system(size: size == .small ? 30 : 36, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(WidgetInk.text)
            } else {
                Text(s.title ?? "").font(size == .small ? .headline : .title3.weight(.bold)).foregroundStyle(WidgetInk.text).lineLimit(2)
            }
            Text(s.line).font(.footnote).foregroundStyle(accent).lineLimit(2)
            Spacer(minLength: 4)
            HStack {
                Text(s.setsLabel).font(.footnote).foregroundStyle(WidgetInk.muted)
                Spacer()
                Text("\(s.setsDone)/\(s.setsTotal)").font(.subheadline.weight(.bold)).foregroundStyle(accent)
            }
            bar(Double(s.setsDone) / Double(max(1, s.setsTotal)), accent)
            // The large widget has room for the week as well.
            if size == .large { weekBar(strip: true).padding(.top, 14) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}
