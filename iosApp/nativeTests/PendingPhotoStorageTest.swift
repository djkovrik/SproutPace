import Foundation

@main
enum PendingPhotoStorageTest {
    static func check(_ condition: Bool) { precondition(condition) }
    static func main() throws {
        let fm = FileManager.default
        let workspace = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let root = workspace.appendingPathComponent("pending")
        try fm.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: workspace) }
        let source = workspace.appendingPathComponent("source.jpg")
        let bytes = Data([1, 2, 3])
        try bytes.write(to: source)
        let a = try PendingPhotoStorage.copy(source: source, root: root)
        do {
            _ = try PendingPhotoStorage.copy(source: workspace.appendingPathComponent("missing.jpg"), root: root)
            fatalError("Expected unreadable source failure")
        } catch is CocoaError { }
        check(try Data(contentsOf: a) == bytes)
        check(try fm.contentsOfDirectory(atPath: root.path).count == 1)
        for cancel in [false, true] {
            do {
                _ = try PendingPhotoStorage.copy(source: source, root: root, copyItem: { _, destination in
                    try Data([9]).write(to: destination)
                    throw CocoaError(cancel ? .userCancelled : .fileWriteOutOfSpace)
                })
                fatalError("Expected failed or cancelled copy")
            } catch is CocoaError { }
            check(try Data(contentsOf: a) == bytes)
            check(try fm.contentsOfDirectory(atPath: root.path).count == 1)
        }
        var checks = 0
        do {
            _ = try PendingPhotoStorage.copy(source: source, root: root, checkActive: {
                checks += 1
                if checks == 2 { throw CocoaError(.userCancelled) }
            })
            fatalError("Expected cancellation after copy")
        } catch is CocoaError { }
        check(try fm.contentsOfDirectory(atPath: root.path).count == 1)
        let b = try PendingPhotoStorage.copy(source: source, root: root)
        precondition(a != b)
        check(try Data(contentsOf: a) == bytes)
        check(try Data(contentsOf: b) == bytes)
        try fm.removeItem(at: a) // Editor accepts B, then releases A.
        precondition(fm.fileExists(atPath: b.path))
        try fm.removeItem(at: b) // Explicit remove/cancel releases the pending selection.
        check(try fm.contentsOfDirectory(atPath: root.path).isEmpty)
        print("PendingPhotoStorage: failure, partial copy, cancellation, replacement and cleanup PASS")
    }
}
