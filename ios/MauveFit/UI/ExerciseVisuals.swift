import SwiftUI

/**
 * A body silhouette with the exercise's target muscles in the accent and the secondary ones in a
 * lighter tint. With [zoom], it closes in on what is trained, for small thumbnails.
 */
struct MuscleMap: View {
    let primary: Set<String>
    let secondary: Set<String>
    let side: BodySide
    var zoom = false

    init(exercise: Exercise?, side: BodySide? = nil, zoom: Bool = false) {
        primary = MuscleRegions.regions(exercise.map { [$0.target] } ?? [])
        secondary = MuscleRegions.regions(exercise?.secondary ?? []).subtracting(primary)
        self.side = side ?? exercise.map(MuscleRegions.side) ?? .front
        self.zoom = zoom
    }

    var body: some View {
        Canvas { ctx, size in
            let map = BodyMap.shared[side]
            let focus = zoom ? focusRect(map, aspect: size.width / max(1, size.height)) : CGRect(origin: .zero, size: BodyMap.size)
            let scale = min(size.width / focus.width, size.height / focus.height)
            ctx.translateBy(x: (size.width - focus.width * scale) / 2 - focus.minX * scale,
                            y: (size.height - focus.height * scale) / 2 - focus.minY * scale)
            ctx.scaleBy(x: scale, y: scale)
            for (slug, path) in map.parts {
                let color = primary.contains(slug) ? Ink.accent : secondary.contains(slug) ? Ink.accent.opacity(0.42) : Ink.body
                ctx.fill(path, with: .color(color))
            }
            ctx.stroke(map.outline, with: .color(Ink.lineStrong.opacity(0.6)), lineWidth: 1.2 / scale)
        }
        .accessibilityHidden(true)
    }

    /** The trained regions with room around them, at the view's aspect, kept inside the body. */
    private func focusRect(_ map: BodyMap.Side, aspect: CGFloat) -> CGRect {
        let full = CGRect(origin: .zero, size: BodyMap.size)
        let wanted = primary.isEmpty ? secondary : primary
        let box = wanted.compactMap { map.parts[$0]?.boundingRect }.reduce(CGRect.null) { $0.union($1) }
        guard !box.isNull else { return full }
        // At least a third of the body tall, so a single small muscle still shows where it sits.
        var h = max(box.height * 1.5, box.width * 1.5 / aspect, full.height / 3)
        var w = h * aspect
        if w > full.width { w = full.width; h = w / aspect }
        var r = CGRect(x: box.midX - w / 2, y: box.midY - h / 2, width: w, height: h)
        r.origin.x = min(max(r.minX, 0), full.width - w)
        r.origin.y = min(max(r.minY, 0), full.height - h)
        return r
    }
}

/** The small picture of an exercise in lists: its muscle map, closed in on what it trains. */
struct ExerciseThumb: View {
    let exercise: Exercise?
    var size: CGFloat = 56

    var body: some View {
        MuscleMap(exercise: exercise, zoom: true)
            .padding(size * 0.06)
            .frame(width: size, height: size)
            .background(Ink.stage)
            .clipShape(RoundedRectangle(cornerRadius: size >= 48 ? 16 : 12, style: .continuous))
    }
}

/**
 * The top of an exercise's page: the Everkinetic drawings of the start and end position on a
 * light card where there are any, and the muscle map from the front and the back.
 */
struct ExerciseHero: View {
    let exercise: Exercise

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let files = Drawings.shared.images[exercise.id] {
                VStack(spacing: 8) {
                    HStack(spacing: 12) {
                        ForEach(Array(files.enumerated()), id: \.offset) { i, name in
                            VStack(spacing: 4) {
                                if let img = Drawings.shared.load(name) {
                                    Image(uiImage: img).resizable().interpolation(.high).scaledToFit()
                                        .frame(maxWidth: .infinity, maxHeight: 240)
                                }
                                if files.count > 1 {
                                    Text(i == 0 ? L("Start", "Start") : L("Ende", "End"))
                                        .font(.fit(.labelSmall)).foregroundStyle(Color(white: 0.35))
                                }
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity)
                // White in both themes: the drawings are black line art on white.
                .background(Color.white, in: RoundedRectangle(cornerRadius: Radius.panel, style: .continuous))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(L("Zeichnung von Start- und Endposition", "Drawing of the start and end position"))
                Link(destination: Drawings.licenseURL) {
                    Text(L("Illustrationen: Everkinetic, CC BY-SA 4.0", "Illustrations: Everkinetic, CC BY-SA 4.0"))
                        .font(.fit(.labelSmall)).foregroundStyle(Ink.faint).underline()
                }
            }
            HStack(spacing: 0) {
                MuscleMap(exercise: exercise, side: .front)
                MuscleMap(exercise: exercise, side: .back)
            }
            .frame(height: 260)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(Ink.stage, in: RoundedRectangle(cornerRadius: Radius.panel, style: .continuous))
            .overlay(alignment: .bottomLeading) {
                HStack(spacing: 12) {
                    legend(Ink.accent, L("Ziel", "Target"))
                    if !(exercise.secondary.isEmpty) { legend(Ink.accent.opacity(0.42), L("Mit dabei", "Also works")) }
                }
                .padding(12)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("Muskelkarte: ", "Muscle map: ") + ([exercise.target] + exercise.secondary).map(Labels.muscle).joined(separator: ", "))
        }
    }

    private func legend(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(text).font(.fit(.labelSmall)).foregroundStyle(Ink.muted)
        }
    }
}
