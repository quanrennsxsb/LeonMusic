import AppKit
import SwiftUI
import WidgetKit

private let leonMusicWidgetKind = "LeonMusicNowPlayingWidget"
private let leonMusicAppGroupIdentifier = "group.top.iwesley.lyn.music"

struct LeonMusicWidgetSnapshot: Decodable {
    let hasTrack: Bool
    let title: String?
    let artist: String?
    let album: String?
    let artworkPath: String?
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

    private static func format(milliseconds: Int64) -> String {
        let totalSeconds = max(Int(milliseconds / 1000), 0)
        return String(format: "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
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
        let entry = LeonMusicWidgetEntry(date: now, snapshot: loadSnapshot())
        completion(Timeline(entries: [entry], policy: .after(now.addingTimeInterval(60))))
    }

    private func loadSnapshot() -> LeonMusicWidgetSnapshot {
        guard let url = snapshotURL(), let data = try? Data(contentsOf: url) else {
            return .empty
        }
        return (try? JSONDecoder().decode(LeonMusicWidgetSnapshot.self, from: data)) ?? .empty
    }

    private func snapshotURL() -> URL? {
        if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: leonMusicAppGroupIdentifier) {
            return container.appendingPathComponent("LeonMusicWidget/now-playing.json")
        }
        return FileManager.default
            .homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Group Containers/\(leonMusicAppGroupIdentifier)/LeonMusicWidget/now-playing.json")
    }
}

struct LeonMusicNowPlayingWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: LeonMusicWidgetEntry

    var body: some View {
        switch family {
        case .systemSmall:
            SmallLeonMusicWidget(snapshot: entry.snapshot)
        case .systemLarge, .systemExtraLarge:
            LargeLeonMusicWidget(snapshot: entry.snapshot)
        default:
            MediumLeonMusicWidget(snapshot: entry.snapshot)
        }
    }
}

struct SmallLeonMusicWidget: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ArtworkView(path: snapshot.artworkPath)
                .frame(width: 54, height: 54)
            Spacer(minLength: 0)
            TrackTextBlock(snapshot: snapshot, titleFont: .headline, subtitleFont: .caption)
            PlaybackProgress(snapshot: snapshot)
        }
        .padding(14)
        .widgetBackground()
    }
}

struct MediumLeonMusicWidget: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        HStack(spacing: 14) {
            ArtworkView(path: snapshot.artworkPath)
                .frame(width: 96, height: 96)
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    PlaybackBadge(isPlaying: snapshot.isPlaying == true)
                    Spacer()
                    Text(snapshot.positionText)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                TrackTextBlock(snapshot: snapshot, titleFont: .title3.weight(.semibold), subtitleFont: .callout)
                Spacer(minLength: 0)
                PlaybackProgress(snapshot: snapshot)
            }
        }
        .padding(16)
        .widgetBackground()
    }
}

struct LargeLeonMusicWidget: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 16) {
                ArtworkView(path: snapshot.artworkPath)
                    .frame(width: 112, height: 112)
                VStack(alignment: .leading, spacing: 10) {
                    PlaybackBadge(isPlaying: snapshot.isPlaying == true)
                    TrackTextBlock(snapshot: snapshot, titleFont: .title2.weight(.semibold), subtitleFont: .body)
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
            .foregroundStyle(.secondary)
        }
        .padding(18)
        .widgetBackground()
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
        guard let path, !path.isEmpty else { return nil }
        return NSImage(contentsOfFile: path)
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
                .lineLimit(2)
            Text(snapshot.hasTrack ? nonEmptySubtitle : "暂无播放")
                .font(subtitleFont)
                .foregroundStyle(.secondary)
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
            .foregroundStyle(isPlaying ? Color.green : Color.secondary)
            .labelStyle(.titleAndIcon)
    }
}

struct PlaybackProgress: View {
    let snapshot: LeonMusicWidgetSnapshot

    var body: some View {
        ProgressView(value: snapshot.hasTrack ? snapshot.progressFraction : 0)
            .progressViewStyle(.linear)
            .tint(.red)
    }
}

extension View {
    func widgetBackground() -> some View {
        containerBackground(for: .widget) {
            Color(nsColor: .windowBackgroundColor)
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
    }
}
