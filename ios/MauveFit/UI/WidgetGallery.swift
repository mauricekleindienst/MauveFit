import SwiftUI

/**
 * The home screen widget's three sizes drawn inside the app, with the sample data: WidgetKit
 * renders on the home screen, where the screenshot test cannot reach. Only under -demoWidgets.
 */
struct WidgetGallery: View {
    @Environment(Store.self) private var store

    var body: some View {
        let s = WidgetSync.snapshot(store.data)
        ScrollView {
            VStack(spacing: 18) {
                HStack(spacing: 18) {
                    tile(s, .small, width: 158, height: 158)
                    Spacer(minLength: 0)
                }
                tile(s, .medium, width: 338, height: 158)
                tile(s, .large, width: 338, height: 354)
            }
            .padding(20)
            .padding(.top, 40)
        }
        .background(Color(white: 0.45).ignoresSafeArea())
        .accessibilityIdentifier("widgetGallery")
    }

    private func tile(_ s: WidgetSnapshot, _ size: WidgetSize, width: CGFloat, height: CGFloat) -> some View {
        NextWidgetView(s: s, size: size)
            .padding(16)
            .frame(width: width, height: height)
            .background(WidgetInk.bg, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
