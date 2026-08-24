import AppKit
import AppIntents
import SwiftUI
import WidgetKit

private let leonMusicWidgetKind = "LeonMusicNowPlayingWidget"
private let leonMusicAppGroupIdentifier = "group.top.iwesley.lyn.music"
private let leonMusicWidgetCommandRelativePath = "LeonMusicWidget/playback-command.json"
private let leonMusicWidgetOpenURL = URL(string: "leonmusic://open")!

struct LeonMusicWidgetSnapshot: Decodable {
    let hasTrack: Bool
    let title: String?
    let artist: String?
    let album: String?
    let artworkPath: String?
    let lyricsText: String?
    let durationMs: Int64?
    let positionMs: Int64?
    let isPlaying: Bool?
    let canSeek: Bool?
    let hasNext: Bool?
    let hasPrevious: Bool?
    let updatedAtEpochSeconds: Int64?

    static let empty = LeonMusicWidgetSnapshot(
        hasTrack: false,
        title: nil,
        artist: nil,
        album: nil,
        artworkPath: nil,
        lyricsText: nil,
        durationMs: nil,
        positionMs: nil,
        isPlaying: nil,
        canSeek: nil,
        hasNext: nil,
        hasPrevious: nil,
        updatedAtEpochSeconds: nil
    )

    var displayTitle: String {
        title?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? title! : "LeonMusic"
    }

    var displaySubtitle: String {
        [artist, album]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " - ")
    }

    var progressFraction: Double {
        guard let durationMs, durationMs > 0, let positionMs else { return 0 }
        return min(max(Double(positionMs) / Double(durationMs), 0), 1)
    }

    var positionText: String {
        Self.format(milliseconds: positionMs ?? 0)
    }

    var durationText: String {
        Self.format(milliseconds: durationMs ?? 0)
    }

    var displayLyricsText: String? {
        lyricsText?
            .components(separatedBy: .newlines)
            .map { Self.cleanLyricLine($0) }
            .first(where: { !$0.isEmpty })?
            .nilIfBlank
    }

    private static func format(milliseconds: Int64) -> String {
        let totalSeconds = max(Int(milliseconds / 1000), 0)
        return String(format: "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    var artworkImage: NSImage? {
        Self.image(at: artworkPath)
    }

    static func image(at path: String?) -> NSImage? {
        guard let path, !path.isEmpty else { return nil }
        return NSImage(contentsOfFile: path)
    }

    private static func cleanLyricLine(_ line: String) -> String {
        var text = line.trimmingCharacters(in: .whitespacesAndNewlines)
        while text.first == "[" {
            guard let end = text.firstIndex(of: "]") else { break }
            text.removeSubrange(text.startIndex...end)
            text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return text
    }
}

extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

struct LeonMusicWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: LeonMusicWidgetSnapshot
}

struct LeonMusicWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> LeonMusicWidgetEntry {
        LeonMusicWidgetEntry(
            date: Date(),
            snapshot: LeonMusicWidgetSnapshot(
                hasTrack: true,
                title: "LeonMusic",
                artist: "正在播放",
                album: nil,
                artworkPath: nil,
                lyricsText: "你听见山谷里的回声",
                durationMs: 240_000,
                positionMs: 84_000,
                isPlaying: true,
                canSeek: true,
                hasNext: true,
                hasPrevious: true,
                updatedAtEpochSeconds: nil
            )
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (LeonMusicWidgetEntry) -> Void) {
        completion(LeonMusicWidgetEntry(date: Date(), snapshot: loadSnapshot()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LeonMusicWidgetEntry>) -> Void) {
        let now = Date()
        let snapshot = loadSnapshot()
        let entry = LeonMusicWidgetEntry(date: now, snapshot: snapshot)
        let refreshInterval: TimeInterval = snapshot.hasTrack ? 10 : 15
        completion(Timeline(entries: [entry], policy: .after(now.addingTimeInterval(refreshInterval))))
    }

    private func loadSnapshot() -> LeonMusicWidgetSnapshot {
        for url in snapshotURLs() {
            guard let data = try? Data(contentsOf: url),
                  let snapshot = try? JSONDecoder().decode(LeonMusicWidgetSnapshot.self, from: data) else {
                continue
            }
            return snapshot
        }
        return .empty
    }

    private func snapshotURLs() -> [URL] {
        let fallbackURL = FileManager.default
            .homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Group Containers/\(leonMusicAppGroupIdentifier)/LeonMusicWidget/now-playing.json")
        if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: leonMusicAppGroupIdentifier) {
            let containerURL = container.appendingPathComponent("LeonMusicWidget/now-playing.json")
            return containerURL == fallbackURL ? [containerURL] : [containerURL, fallbackURL]
        }
        return [fallbackURL]
    }
}

struct LeonMusicNowPlayingWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: LeonMusicWidgetEntry

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                SmallLeonMusicWidget(snapshot: entry.snapshot)
            case .systemLarge, .systemExtraLarge:
                LargeLeonMusicWidget(snapshot: entry.snapshot)
            default:
                MediumLeonMusicWidget(snapshot: entry.snapshot)
            }
        }
        .widgetURL(leonMusicWidgetOpenURL)
    }
}

struct SmallLeonMusicWidget: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        WidgetBackdrop(snapshot: snapshot) {
            VStack(alignment: .leading, spacing: 6) {
                ArtworkView(path: snapshot.artworkPath)
                    .frame(width: 54, height: 54)
                Spacer(minLength: 0)
                TrackTextBlock(snapshot: snapshot, titleFont: .headline, subtitleFont: .caption)
                PlaybackProgress(snapshot: snapshot)
                PlaybackControls(snapshot: snapshot, compact: true)
            }
            .padding(14)
        }
    }
}

struct MediumLeonMusicWidget: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        WidgetBackdrop(snapshot: snapshot) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(alignment: .top, spacing: 14) {
                    ArtworkView(path: snapshot.artworkPath)
                        .frame(width: 90, height: 90)
                        .shadow(color: .black.opacity(0.28), radius: 12, x: 0, y: 6)
                    VStack(alignment: .leading, spacing: 7) {
                        MediumTrackTextBlock(snapshot: snapshot)
                        MediumLyricsSnippet(snapshot: snapshot)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)
                }
                PlaybackProgress(snapshot: snapshot)
                    .padding(.top, 6)
                HStack(spacing: 10) {
                    Text(snapshot.positionText)
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.72))
                        .frame(width: 40, alignment: .leading)
                    PlaybackControls(snapshot: snapshot, compact: true)
                    Text(snapshot.durationText)
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.white.opacity(0.72))
                        .frame(width: 40, alignment: .trailing)
                }
                .padding(.top, 8)
            }
            .padding(9)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}

struct LargeLeonMusicWidget: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        WidgetBackdrop(snapshot: snapshot) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 16) {
                    ArtworkView(path: snapshot.artworkPath)
                        .frame(width: 112, height: 112)
                        .shadow(color: .black.opacity(0.30), radius: 14, x: 0, y: 7)
                    VStack(alignment: .leading, spacing: 10) {
                        PlaybackBadge(isPlaying: snapshot.isPlaying == true)
                        TrackTextBlock(snapshot: snapshot, titleFont: .title2.weight(.semibold), subtitleFont: .body)
                        LyricsSnippet(snapshot: snapshot)
                        Spacer(minLength: 0)
                    }
                }
                Spacer(minLength: 0)
                PlaybackProgress(snapshot: snapshot)
                HStack {
                    Text(snapshot.positionText)
                    Spacer()
                    Text(snapshot.durationText)
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.white.opacity(0.72))
                PlaybackControls(snapshot: snapshot, compact: false)
            }
            .padding(18)
        }
    }
}

struct WidgetBackdrop<Content: View>: View {
    let snapshot: LeonMusicWidgetSnapshot
    let content: Content

    init(snapshot: LeonMusicWidgetSnapshot, @ViewBuilder content: () -> Content) {
        self.snapshot = snapshot
        self.content = content()
    }

    var body: some View {
        content
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            ZStack {
                if let image = snapshot.artworkImage {
                    Image(nsImage: image)
                        .resizable()
                        .scaledToFill()
                        .blur(radius: 24)
                        .scaleEffect(1.16)
                    LinearGradient(
                        colors: [
                            .black.opacity(0.70),
                            Color(red: 0.16, green: 0.04, blue: 0.05).opacity(0.52),
                            .black.opacity(0.80)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                } else {
                    LinearGradient(
                        colors: [
                            Color(red: 0.04, green: 0.05, blue: 0.07),
                            Color(red: 0.34, green: 0.05, blue: 0.07),
                            Color(red: 0.09, green: 0.10, blue: 0.12)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
                LinearGradient(
                    colors: [
                        .white.opacity(0.10),
                        .clear,
                        .black.opacity(0.18)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
            .clipped()
        }
        .widgetBackground()
        .foregroundStyle(.white)
    }
}

struct ArtworkView: View {
    let path: String?

    var body: some View {
        ZStack {
            if let image = image {
                Image(nsImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                LinearGradient(
                    colors: [Color(red: 0.09, green: 0.11, blue: 0.13), Color(red: 0.86, green: 0.20, blue: 0.24)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                Image(systemName: "music.note")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.white)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var image: NSImage? {
        LeonMusicWidgetSnapshot.image(at: path)
    }
}

struct TrackTextBlock: View {
    let snapshot: LeonMusicWidgetSnapshot
    let titleFont: Font
    let subtitleFont: Font

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(snapshot.hasTrack ? snapshot.displayTitle : "LeonMusic")
                .font(titleFont)
                .foregroundStyle(.white)
                .lineLimit(2)
            Text(snapshot.hasTrack ? nonEmptySubtitle : "暂无播放")
                .font(subtitleFont)
                .foregroundStyle(.white.opacity(0.74))
                .lineLimit(2)
        }
    }

    private var nonEmptySubtitle: String {
        snapshot.displaySubtitle.isEmpty ? (snapshot.isPlaying == true ? "正在播放" : "已暂停") : snapshot.displaySubtitle
    }
}

struct PlaybackBadge: View {
    let isPlaying: Bool

    var body: some View {
        Label(isPlaying ? "正在播放" : "已暂停", systemImage: isPlaying ? "waveform" : "pause.fill")
            .font(.caption.weight(.semibold))
            .foregroundStyle(isPlaying ? Color(red: 0.48, green: 0.96, blue: 0.68) : .white.opacity(0.72))
            .labelStyle(.titleAndIcon)
    }
}

struct LyricsSnippet: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        if let text = snapshot.displayLyricsText {
            Text(text)
                .font(.caption)
                .foregroundStyle(.white.opacity(0.78))
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.vertical, 5)
                .padding(.horizontal, 7)
                .background(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(Color.black.opacity(0.16))
                )
        }
    }
}

struct MediumTrackTextBlock: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(snapshot.hasTrack ? snapshot.displayTitle : "LeonMusic")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)
            Text(snapshot.hasTrack ? subtitle : "暂无播放")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(.white.opacity(0.74))
                .lineLimit(1)
        }
    }

    private var subtitle: String {
        snapshot.displaySubtitle.isEmpty ? (snapshot.isPlaying == true ? "正在播放" : "已暂停") : snapshot.displaySubtitle
    }
}

struct MediumLyricsSnippet: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        if let lyrics = snapshot.displayLyricsText {
            Text(lyrics)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, alignment: .center)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Color.black.opacity(0.32))
            )
        }
    }
}

struct PlaybackProgress: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        ProgressView(value: snapshot.hasTrack ? snapshot.progressFraction : 0)
            .progressViewStyle(.linear)
            .tint(Color(red: 1.0, green: 0.30, blue: 0.32))
    }
}

struct PlaybackControls: View {
    let snapshot: LeonMusicWidgetSnapshot
    let compact: Bool
    let enlarged: Bool

    init(snapshot: LeonMusicWidgetSnapshot, compact: Bool, enlarged: Bool = false) {
        self.snapshot = snapshot
        self.compact = compact
        self.enlarged = enlarged
    }

    private var controlDiameter: CGFloat {
        compact ? (enlarged ? 26 : 24) : 30
    }

    private var iconSize: CGFloat {
        compact ? (enlarged ? 14 : 13) : 16
    }

    var body: some View {
        HStack(spacing: compact ? 20 : 14) {
            Spacer(minLength: 0)
            Button(intent: LeonMusicWidgetPreviousTrackIntent()) {
                Image(systemName: "backward.fill")
                    .playbackControlCircle(diameter: controlDiameter)
            }
            .disabled(!snapshot.hasTrack || snapshot.hasPrevious == false)
            PlaybackControlButtonSpacer(compact: compact)
            Button(intent: LeonMusicWidgetTogglePlayPauseIntent()) {
                Image(systemName: snapshot.isPlaying == true ? "pause.fill" : "play.fill")
                    .playbackControlCircle(diameter: controlDiameter, emphasized: true)
            }
            .disabled(!snapshot.hasTrack)
            PlaybackControlButtonSpacer(compact: compact)
            Button(intent: LeonMusicWidgetNextTrackIntent()) {
                Image(systemName: "forward.fill")
                    .playbackControlCircle(diameter: controlDiameter)
            }
            .disabled(!snapshot.hasTrack || snapshot.hasNext == false)
            Spacer(minLength: 0)
        }
        .frame(minHeight: controlDiameter)
        .font(.system(size: iconSize, weight: .semibold))
        .buttonStyle(.plain)
        .foregroundStyle(.white)
    }
}

extension Image {
    func playbackControlCircle(diameter: CGFloat, emphasized: Bool = false) -> some View {
        self
            .frame(width: diameter, height: diameter)
            .background(
                Circle()
                    .fill(emphasized ? Color.white.opacity(0.28) : Color.white.opacity(0.16))
                    .overlay(
                        Circle().stroke(Color.white.opacity(emphasized ? 0.34 : 0.20), lineWidth: 1)
                    )
            )
    }
}

struct PlaybackControlButtonSpacer: View {
    let compact: Bool

    var body: some View {
        if !compact {
            Spacer(minLength: 0)
                .frame(maxWidth: 8)
        }
    }
}

struct LeonMusicWidgetTogglePlayPauseIntent: AppIntent {
    static var title: LocalizedStringResource = "播放或暂停"

    func perform() async throws -> some IntentResult {
        writeLeonMusicWidgetPlaybackCommand("togglePlayPause")
        WidgetCenter.shared.reloadTimelines(ofKind: leonMusicWidgetKind)
        return .result()
    }
}

struct LeonMusicWidgetPreviousTrackIntent: AppIntent {
    static var title: LocalizedStringResource = "上一曲"

    func perform() async throws -> some IntentResult {
        writeLeonMusicWidgetPlaybackCommand("previous")
        WidgetCenter.shared.reloadTimelines(ofKind: leonMusicWidgetKind)
        return .result()
    }
}

struct LeonMusicWidgetNextTrackIntent: AppIntent {
    static var title: LocalizedStringResource = "下一曲"

    func perform() async throws -> some IntentResult {
        writeLeonMusicWidgetPlaybackCommand("next")
        WidgetCenter.shared.reloadTimelines(ofKind: leonMusicWidgetKind)
        return .result()
    }
}

private func writeLeonMusicWidgetPlaybackCommand(_ command: String) {
    guard let url = leonMusicWidgetCommandURL() else { return }
    let payload: [String: Any] = [
        "command": command,
        "createdAtEpochSeconds": Int64(Date().timeIntervalSince1970)
    ]
    guard let data = try? JSONSerialization.data(withJSONObject: payload, options: []) else { return }
    try? FileManager.default.createDirectory(
        at: url.deletingLastPathComponent(),
        withIntermediateDirectories: true
    )
    try? data.write(to: url, options: .atomic)
}

private func leonMusicWidgetCommandURL() -> URL? {
    if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: leonMusicAppGroupIdentifier) {
        return container.appendingPathComponent(leonMusicWidgetCommandRelativePath)
    }
    return FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent("Library/Group Containers/\(leonMusicAppGroupIdentifier)/\(leonMusicWidgetCommandRelativePath)")
}

extension View {
    func widgetBackground() -> some View {
        containerBackground(for: .widget) {
            Color.clear
        }
    }
}

@main
struct LeonMusicWidgetBundle: WidgetBundle {
    var body: some Widget {
        LeonMusicNowPlayingWidget()
    }
}

struct LeonMusicNowPlayingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: leonMusicWidgetKind, provider: LeonMusicWidgetProvider()) { entry in
            LeonMusicNowPlayingWidgetView(entry: entry)
        }
        .configurationDisplayName("LeonMusic")
        .description("查看当前播放的歌曲。")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}
