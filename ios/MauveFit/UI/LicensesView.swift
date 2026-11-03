import SwiftUI

/** The third-party material bundled with the app and its licences (see THIRD_PARTY_NOTICES.md). */
struct LicensesView: View {
    private static func mit(_ holder: String, data: Bool = false) -> String {
        """
        MIT License

        Copyright (c) \(holder)

        Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation \(data ? "and data files" : "files") (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
        """
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                entry(
                    title: L("Illustrationen: Everkinetic", "Illustrations: Everkinetic"),
                    what: L("Zeichnungen von Start- und Endposition. Lizenz: CC BY-SA 4.0. Geändert: auf höchstens 640 px verkleinert und auf vier Graustufen reduziert; die geänderten Bilder stehen ebenfalls unter CC BY-SA 4.0.",
                            "Drawings of the start and end position. License: CC BY-SA 4.0. Changed: scaled to at most 640 px and reduced to four grey levels; the changed images are shared under CC BY-SA 4.0 as well."),
                    links: [(L("Lizenz CC BY-SA 4.0", "License CC BY-SA 4.0"), Drawings.licenseURL), (L("Quelle", "Source"), Drawings.sourceURL)],
                    license: nil
                )
                entry(
                    title: "react-native-body-highlighter",
                    what: L("Körper- und Muskelformen der Muskelkarte, in Pfade für die App umgewandelt.",
                            "Body and muscle shapes of the muscle map, converted into paths for the app."),
                    links: [(L("Quelle", "Source"), URL(string: "https://github.com/HichamELBSI/react-native-body-highlighter")!)],
                    license: Self.mit("2022 ELABBASSI Hicham")
                )
                entry(
                    title: "exercises-dataset",
                    what: L("Namen, Muskeln, Geräte und Anleitungen der Übungen (nur die Daten, keine Bilder).",
                            "Names, muscles, equipment and instructions of the exercises (the data only, no images)."),
                    links: [(L("Quelle", "Source"), URL(string: "https://github.com/hasaneyldrm/exercises-dataset")!)],
                    license: Self.mit("2026 Hasan Emir Yıldırım", data: true)
                )
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .screen()
        .navigationTitle(L("Open-Source-Lizenzen", "Open-source licences"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func entry(title: String, what: String, links: [(String, URL)], license: String?) -> some View {
        Card(padding: 16) {
            Text(title).font(.fit(.titleMedium)).foregroundStyle(Ink.text)
            Text(what).font(.fit(.bodyMedium)).foregroundStyle(Ink.muted)
            ForEach(links, id: \.1) { label, url in
                Link(label, destination: url).font(.fit(.label))
            }
            if let license {
                Text(license).font(.system(.caption2, design: .monospaced)).foregroundStyle(Ink.muted)
                    .textSelection(.enabled)
            }
        }
    }
}
