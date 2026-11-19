import Foundation

/// A pending import owns only its new file. The editor disposes the previous token after acceptance.
enum PendingPhotoStorage {
    static func copy(
        source: URL,
        root: URL,
        checkActive: () throws -> Void = {},
        copyItem: (URL, URL) throws -> Void = { try FileManager.default.copyItem(at: $0, to: $1) }
    ) throws -> URL {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let suffix = String(source.pathExtension.filter { $0.isLetter || $0.isNumber }.prefix(12))
        let name = UUID().uuidString + (suffix.isEmpty ? "" : ".\(suffix)")
        let destination = root.appendingPathComponent(name, isDirectory: false)
        do {
            try checkActive()
            try copyItem(source, destination)
            try checkActive()
            return destination
        } catch {
            if FileManager.default.fileExists(atPath: destination.path) {
                do { try FileManager.default.removeItem(at: destination) }
                catch let cleanupError {
                    throw NSError(domain: "PendingPhotoStorage", code: 1,
                        userInfo: [NSUnderlyingErrorKey: error, "cleanupError": cleanupError])
                }
            }
            throw error
        }
    }
}
