import AppKit
import SwiftUI

@MainActor
final class EmbeddedLeonMusicPlayerLauncher {
    static let shared = EmbeddedLeonMusicPlayerLauncher()

    private var process: Process?

    var isRunning: Bool {
        process?.isRunning == true
    }

    func launch(completion: @escaping (Result<Void, Error>) -> Void) {
        if isRunning {
            log("embedded player already running")
            completion(.success(()))
            return
        }
        guard let executableURL = resolveExecutableURL() else {
            log("missing embedded player executable")
            completion(.failure(LauncherError.missingEmbeddedPlayer))
            return
        }
        log("launching embedded player at \(executableURL.path)")
        let playerRootURL = executableURL
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let newProcess = Process()
        newProcess.executableURL = executableURL
        newProcess.currentDirectoryURL = playerRootURL
        let outputURL = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("LeonMusicEmbeddedPlayer.log")
        FileManager.default.createFile(atPath: outputURL.path, contents: nil)
        if let outputHandle = try? FileHandle(forWritingTo: outputURL) {
            newProcess.standardOutput = outputHandle
            newProcess.standardError = outputHandle
        }
        newProcess.terminationHandler = { [weak self, weak newProcess] _ in
            Task { @MainActor in
                if self?.process === newProcess {
                    self?.log("embedded player terminated")
                    self?.process = nil
                }
            }
        }
        do {
            try newProcess.run()
            process = newProcess
            log("embedded player launched pid=\(newProcess.processIdentifier)")
            completion(.success(()))
        } catch {
            log("embedded player launch failed: \(error.localizedDescription)")
            completion(.failure(error))
        }
    }

    func revealEmbeddedPlayer() {
        guard let executableURL = resolveExecutableURL() else { return }
        NSWorkspace.shared.activateFileViewerSelecting([executableURL])
    }

    private func resolveExecutableURL() -> URL? {
        guard let resourceURL = Bundle.main.resourceURL else { return nil }
        let executableURL = resourceURL
            .appendingPathComponent("Contents")
            .appendingPathComponent("MacOS")
            .appendingPathComponent("LeonMusic")
        return FileManager.default.isExecutableFile(atPath: executableURL.path) ? executableURL : nil
    }

    private func log(_ message: String) {
        let line = "\(Date()) \(message)\n"
        let url = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("LeonMusicLauncher.log")
        guard let data = line.data(using: .utf8) else { return }
        if FileManager.default.fileExists(atPath: url.path),
           let handle = try? FileHandle(forWritingTo: url) {
            defer { try? handle.close() }
            try? handle.seekToEnd()
            try? handle.write(contentsOf: data)
        } else {
            try? data.write(to: url)
        }
    }

    enum LauncherError: LocalizedError {
        case missingEmbeddedPlayer

        var errorDescription: String? {
            "未找到内置 LeonMusic 播放器。请重新构建并安装应用。"
        }
    }
}

struct ContentView: View {
    @State private var launchMessage: String = "正在启动 LeonMusic..."
    @State private var lastLaunchError: String?

    var body: some View {
        VStack(spacing: 18) {
            Image(nsImage: NSApp.applicationIconImage)
                .resizable()
                .frame(width: 72, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(spacing: 6) {
                Text("LeonMusic")
                    .font(.title2.weight(.semibold))
                Text(launchMessage)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }

            if let lastLaunchError {
                Text(lastLaunchError)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 360)
            }

            HStack(spacing: 12) {
                Button("打开 LeonMusic") {
                    launchLeonMusic()
                }
                Button("显示内置播放器") {
                    EmbeddedLeonMusicPlayerLauncher.shared.revealEmbeddedPlayer()
                }
            }
        }
        .padding(28)
        .frame(minWidth: 440, minHeight: 280)
        .onAppear {
            launchLeonMusic()
        }
    }

    private func launchLeonMusic() {
        lastLaunchError = nil
        EmbeddedLeonMusicPlayerLauncher.shared.launch { result in
            switch result {
            case .success:
                launchMessage = "LeonMusic 已启动"
            case .failure(let error):
                launchMessage = "启动失败"
                lastLaunchError = error.localizedDescription
            }
        }
    }
}

#Preview {
    ContentView()
}
