import SwiftUI
import UIKit
import PhotosUI
import UniformTypeIdentifiers
import UserNotifications
import compose

#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseCrashlytics)
import FirebaseCrashlytics
#endif

@main
struct ComposeApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var host = IosApplicationHost()

    var body: some Scene {
        WindowGroup {
            ContentView(
                graph: host.graph,
                platformServices: host.platformServices
            )
                .ignoresSafeArea(.all)
                .onAppear {
                    host.transition(to: scenePhase)
                }
                .onChange(of: scenePhase) { phase in
                    host.transition(to: phase)
                }
        }
    }
}

private final class IosApplicationHost: ObservableObject {
    enum LifecycleState {
        case created
        case started
        case resumed
        case stopped
    }

    let graph: IosProductionAppGraph
    let platformServices: NativePlatformServices
    private var lifecycleState = LifecycleState.created

    init() {
#if DEBUG
        let isDebug = true
#else
        let isDebug = false
#endif
        platformServices = NativePlatformServiceFactory.make()
        graph = IosProductionAppGraph(
            isDebug: isDebug,
            crashReportingBridge: platformServices.crashReporting
        )
    }

    deinit {
        graph.close()
    }

    func transition(to scenePhase: ScenePhase) {
        switch scenePhase {
        case .active:
            startIfNeeded()
            guard lifecycleState == .started else { return }
            graph.onResume()
            lifecycleState = .resumed
            platformServices.didBecomeActive()
        case .inactive:
            startIfNeeded()
            pauseIfNeeded()
        case .background:
            pauseIfNeeded()
            guard lifecycleState == .started else { return }
            graph.onStop()
            lifecycleState = .stopped
        @unknown default:
            break
        }
    }

    private func startIfNeeded() {
        guard lifecycleState == .created || lifecycleState == .stopped else { return }
        graph.onStart()
        lifecycleState = .started
    }

    private func pauseIfNeeded() {
        guard lifecycleState == .resumed else { return }
        graph.onPause()
        lifecycleState = .started
    }
}

/// iOS production advertising is intentionally unavailable until a separately approved
/// Yandex iOS project and ad unit exist. This bridge must remain no-op and contain no ID.
struct NativeAdvertisingBridge {
    let isSupported = false
    let adUnitID: String? = nil

    func initialize() {
        // Safe-disabled by product contract: do not link or initialize an advertising SDK.
    }
}

struct ContentView: UIViewControllerRepresentable {
    let graph: IosProductionAppGraph
    let platformServices: NativePlatformServices

    func makeUIViewController(context: Context) -> UIViewController {
        let actions = NativePlatformActionHandler(graph: graph, platformServices: platformServices)
        let viewController = SproutPaceViewController(
            content: MainKt.MainViewController(graph: graph, platformActions: actions)
        )
        actions.presenter = viewController
        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Updates will be handled by Compose
    }
}

private final class NativePlatformActionHandler: NSObject, IosPlatformActionBridge {
    private let graph: IosProductionAppGraph
    private let platformServices: NativePlatformServices
    weak var presenter: UIViewController?

    init(graph: IosProductionAppGraph, platformServices: NativePlatformServices) {
        self.graph = graph
        self.platformServices = platformServices
    }

    func chooseDocument(result: IosPlatformActionResult) {
        guard let presenter else { result.fail(); return }
        platformServices.presentRestoreDocumentPicker(from: presenter) { response in
            switch response {
            case .success(nil): break
            case let .success(document?):
                guard document.location.isFileURL else { result.fail(); return }
                result.acceptDocument(location: document.location.absoluteString, displayName: document.displayName)
            case .failure: result.fail()
            }
        }
    }

    func createDocument(
        suggestedName: String,
        mediaType: String,
        result: IosPlatformActionResult
    ) {
        guard let presenter else { result.fail(); return }
        platformServices.presentExportPicker(
            suggestedName: suggestedName,
            mediaType: mediaType,
            from: presenter
        ) { response in
            switch response {
            case .success(nil): break
            case let .success(url?):
                guard url.isFileURL else { result.fail(); return }
                result.accept(value: url.absoluteString)
            case .failure: result.fail()
            }
        }
    }

    func choosePhoto(result: IosPlatformActionResult) {
        guard let presenter else { result.fail(); return }
        platformServices.presentPhotoPicker(from: presenter) { response in
            switch response {
            case .success(nil): break
            case let .success(url?):
                guard url.isFileURL else { result.fail(); return }
                result.accept(value: url.lastPathComponent)
            case .failure: result.fail()
            }
        }
    }

    func discardPendingPhoto(token: String) {
        platformServices.discardPendingPhoto(token: token)
    }

    func requestNotificationPermission() {
        graph.requestNotificationPermission()
    }

    func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func openPrivacyPolicy() {
        guard
            let rawValue = Bundle.main.object(forInfoDictionaryKey: "SproutPacePrivacyPolicyURL") as? String,
            !rawValue.isEmpty,
            !rawValue.contains("$("),
            let url = URL(string: rawValue),
            url.scheme?.lowercased() == "https",
            url.host?.isEmpty == false
        else { return }
        UIApplication.shared.open(url)
    }
}

private final class SproutPaceViewController: UIViewController {
    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        traitCollection.userInterfaceStyle == .dark ? .lightContent : .darkContent
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content.view)
        NSLayoutConstraint.activate([
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        content.didMove(toParent: self)
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard previousTraitCollection?.userInterfaceStyle != traitCollection.userInterfaceStyle else { return }
        setNeedsStatusBarAppearanceUpdate()
    }
}

enum NativeNotificationPermissionState {
    case unknown
    case checking
    case requesting
    case granted
    case denied
    case unavailable
}

struct NativePhysicalReminder {
    let id: String
    let fireDate: Date
    let titleLocalizationKey: String
    let bodyLocalizationKey: String
}

final class NativePlatformServices: NSObject, PHPickerViewControllerDelegate, UIDocumentPickerDelegate {
    private let notificationCenter = UNUserNotificationCenter.current()
    private var permissionObserver: ((NativeNotificationPermissionState) -> Void)?
    private var photoCompletion: ((Result<URL?, Error>) -> Void)?
    struct ImportedBackupDocument {
        let location: URL
        let displayName: String
    }

    private var documentCompletion: ((Result<ImportedBackupDocument?, Error>) -> Void)?
    private var destinationCompletion: ((Result<URL?, Error>) -> Void)?
    private var destinationTemporaryFile: URL?

    let crashReporting = NativeCrashReportingBridge()

    func observeNotificationPermission(_ observer: @escaping (NativeNotificationPermissionState) -> Void) {
        permissionObserver = observer
        checkNotificationPermission(completion: observer)
    }

    func checkNotificationPermission(
        completion: @escaping (NativeNotificationPermissionState) -> Void
    ) {
        completion(.checking)
        notificationCenter.getNotificationSettings { settings in
            let state: NativeNotificationPermissionState
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                state = .granted
            case .denied:
                state = .denied
            case .notDetermined:
                state = .unknown
            @unknown default:
                state = .unavailable
            }
            DispatchQueue.main.async { completion(state) }
        }
    }

    func requestNotificationPermission(
        completion: @escaping (NativeNotificationPermissionState) -> Void
    ) {
        completion(.requesting)
        notificationCenter.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in
            self.checkNotificationPermission(completion: completion)
        }
    }

    func didBecomeActive() {
        guard let observer = permissionObserver else { return }
        checkNotificationPermission(completion: observer)
    }

    func reconcileReminders(
        desired: [NativePhysicalReminder],
        completion: @escaping (Error?) -> Void
    ) {
        notificationCenter.getPendingNotificationRequests { pending in
            let desiredIds = Set(desired.map(\.id))
            let obsolete = pending.map(\.identifier).filter { !desiredIds.contains($0) }
            self.notificationCenter.removePendingNotificationRequests(withIdentifiers: obsolete)
            let group = DispatchGroup()
            var firstError: Error?
            for reminder in desired where reminder.fireDate > Date() {
                group.enter()
                let content = UNMutableNotificationContent()
                content.title = NSLocalizedString(reminder.titleLocalizationKey, comment: "")
                content.body = NSLocalizedString(reminder.bodyLocalizationKey, comment: "")
                content.sound = .default
                let interval = max(1, reminder.fireDate.timeIntervalSinceNow)
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
                let request = UNNotificationRequest(identifier: reminder.id, content: content, trigger: trigger)
                self.notificationCenter.add(request) { error in
                    if firstError == nil { firstError = error }
                    group.leave()
                }
            }
            group.notify(queue: .main) { completion(firstError) }
        }
    }

    func presentPhotoPicker(
        from presenter: UIViewController,
        completion: @escaping (Result<URL?, Error>) -> Void
    ) {
        guard photoCompletion == nil else {
            completion(.failure(CocoaError(.fileReadUnknown)))
            return
        }
        photoCompletion = completion
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    func presentRestoreDocumentPicker(
        from presenter: UIViewController,
        completion: @escaping (Result<ImportedBackupDocument?, Error>) -> Void
    ) {
        guard documentCompletion == nil else {
            completion(.failure(CocoaError(.fileReadUnknown)))
            return
        }
        documentCompletion = completion
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: [.zip, .data],
            asCopy: true
        )
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    func presentExportPicker(
        suggestedName: String,
        mediaType: String,
        from presenter: UIViewController,
        completion: @escaping (Result<URL?, Error>) -> Void
    ) {
        guard destinationCompletion == nil, !suggestedName.isEmpty, !mediaType.isEmpty else {
            completion(.failure(CocoaError(.fileWriteUnknown)))
            return
        }
        do {
            let temporaryFile = try Self.createExportPlaceholder(suggestedName: suggestedName)
            destinationTemporaryFile = temporaryFile
            destinationCompletion = completion
            let picker = UIDocumentPickerViewController(forExporting: [temporaryFile], asCopy: true)
            picker.delegate = self
            presenter.present(picker, animated: true)
        } catch {
            completion(.failure(error))
        }
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let completion = photoCompletion else { return }
        photoCompletion = nil
        guard let provider = results.first?.itemProvider else {
            completion(.success(nil))
            return
        }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, error in
            if let error {
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }
            guard let url else {
                DispatchQueue.main.async { completion(.failure(CocoaError(.fileReadUnknown))) }
                return
            }
            do {
                let imported = try Self.copyIntoPendingPhotoStorage(source: url)
                DispatchQueue.main.async { completion(.success(imported)) }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }

    func documentPicker(
        _ controller: UIDocumentPickerViewController,
        didPickDocumentsAt urls: [URL]
    ) {
        if let completion = documentCompletion {
            documentCompletion = nil
            guard let source = urls.first else {
                completion(.success(nil))
                return
            }
            do {
                completion(.success(ImportedBackupDocument(
                    location: try Self.copyIntoOwnedStorage(source: source), displayName: source.lastPathComponent
                )))
            } catch {
                completion(.failure(error))
            }
            return
        }
        if let completion = destinationCompletion {
            destinationCompletion = nil
            let selected = urls.first
            removeDestinationPlaceholder(preserving: selected)
            completion(.success(selected))
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        if let completion = documentCompletion {
            documentCompletion = nil
            completion(.success(nil))
        }
        if let completion = destinationCompletion {
            destinationCompletion = nil
            removeDestinationPlaceholder()
            completion(.success(nil))
        }
    }

    func eraseImportedFiles() throws {
        let root = try Self.importDirectory()
        for file in try FileManager.default.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: nil
        ) {
            try FileManager.default.removeItem(at: file)
        }
    }

    func discardPendingPhoto(token: String) {
        guard Self.isSafePendingToken(token) else { return }
        let target = try? Self.pendingPhotoDirectory().appendingPathComponent(token, isDirectory: false)
        if let target, FileManager.default.fileExists(atPath: target.path) {
            try? FileManager.default.removeItem(at: target)
        }
    }

    func cancelAllKnownReminders(_ identifiers: [String]) {
        notificationCenter.removePendingNotificationRequests(withIdentifiers: identifiers)
        notificationCenter.removeDeliveredNotifications(withIdentifiers: identifiers)
    }

    private static func copyIntoPendingPhotoStorage(source: URL) throws -> URL {
        let root = try pendingPhotoDirectory()
        for existing in try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) {
            try FileManager.default.removeItem(at: existing)
        }
        let extensionPart = source.pathExtension.filter { $0.isLetter || $0.isNumber }
        let name = UUID().uuidString + (extensionPart.isEmpty ? "" : ".\(extensionPart)")
        let destination = root.appendingPathComponent(name, isDirectory: false)
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        try FileManager.default.copyItem(at: source, to: destination)
        return destination
    }

    private static func copyIntoOwnedStorage(source: URL) throws -> URL {
        let root = try importDirectory()
        let extensionPart = source.pathExtension.filter { $0.isLetter || $0.isNumber }
        let name = UUID().uuidString + (extensionPart.isEmpty ? "" : ".\(extensionPart)")
        let destination = root.appendingPathComponent(name, isDirectory: false)
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        try FileManager.default.copyItem(at: source, to: destination)
        return destination
    }

    private static func createExportPlaceholder(suggestedName: String) throws -> URL {
        let safeName = URL(fileURLWithPath: suggestedName).lastPathComponent
        guard !safeName.isEmpty, safeName != ".", safeName != ".." else {
            throw CocoaError(.fileWriteInvalidFileName)
        }
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let root = support
            .appendingPathComponent("SproutPace", isDirectory: true)
            .appendingPathComponent("Exports", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let placeholder = root.appendingPathComponent(safeName, isDirectory: false)
        try Data().write(to: placeholder, options: .atomic)
        return placeholder
    }

    private func removeDestinationPlaceholder(preserving selected: URL? = nil) {
        guard let temporaryFile = destinationTemporaryFile else { return }
        destinationTemporaryFile = nil
        if let selected, temporaryFile.standardizedFileURL == selected.standardizedFileURL { return }
        try? FileManager.default.removeItem(at: temporaryFile)
    }

    private static func importDirectory() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let root = support
            .appendingPathComponent("SproutPace", isDirectory: true)
            .appendingPathComponent("Imports", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }

    private static func pendingPhotoDirectory() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let root = support
            .appendingPathComponent("SproutPace", isDirectory: true)
            .appendingPathComponent("PendingPhotos", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }

    private static func isSafePendingToken(_ token: String) -> Bool {
        guard !token.isEmpty, token.count <= 128, token != ".", token != ".." else { return false }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-")
        return token.unicodeScalars.allSatisfy(allowed.contains)
    }
}

final class NativeCrashReportingBridge: IosCrashReportingBridge {
    var isConfigured: Bool {
#if canImport(FirebaseCore) && canImport(FirebaseCrashlytics)
        Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
#else
        false
#endif
    }

    func setCollectionEnabled(enabled_ enabled: Bool) -> Bool {
#if canImport(FirebaseCore) && canImport(FirebaseCrashlytics)
        guard isConfigured else { return false }
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        return true
#else
        return false
#endif
    }

    func recordReviewedTechnicalFailure(typeName: String) -> Bool {
#if canImport(FirebaseCore) && canImport(FirebaseCrashlytics)
        guard isConfigured else { return false }
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        let error = NSError(
            domain: "SproutPace.TechnicalFailure",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: typeName]
        )
        Crashlytics.crashlytics().record(error: error)
        return true
#else
        return false
#endif
    }
}

enum NativePlatformServiceFactory {
    static func make() -> NativePlatformServices {
        NativePlatformServices()
    }
}
