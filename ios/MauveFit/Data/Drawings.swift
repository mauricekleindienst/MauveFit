import UIKit

/**
 * Line drawings from Everkinetic (github.com/everkinetic/data, CC BY-SA 4.0), bundled with the
 * app: a start and an end position for the exercises everkinetic.json matches by hand. Scaled
 * to 640 px and reduced to four grey levels to keep the app small.
 */
final class Drawings: @unchecked Sendable {
    static let shared = Drawings(bundle: .main)

    static let licenseURL = URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!
    static let sourceURL = URL(string: "https://github.com/everkinetic/data")!

    private struct Match: Decodable {
        struct Item: Decodable { let id: String }
        let images: [String]
        let exercises: [Item]
    }

    private let bundle: Bundle
    /** Exercise id to the drawing files, start position first. */
    let images: [String: [String]]

    init(bundle: Bundle) {
        self.bundle = bundle
        let matches = bundle.url(forResource: "everkinetic", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }
            .flatMap { try? JSONDecoder().decode([Match].self, from: $0) } ?? []
        var out: [String: [String]] = [:]
        for m in matches {
            // "relaxation" is the start position, "tension" the end.
            let files = m.images.sorted { a, _ in a.contains("relaxation") }.map { "ek-" + $0.replacingOccurrences(of: ".png", with: "") }
            for e in m.exercises { out[e.id] = files }
        }
        images = out
    }

    func has(_ exerciseId: String) -> Bool { images[exerciseId] != nil }

    func load(_ name: String) -> UIImage? {
        bundle.path(forResource: name, ofType: "png").flatMap(UIImage.init(contentsOfFile:))
    }
}
