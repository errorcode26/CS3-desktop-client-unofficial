class TorrentStatus {
  final String title;
  final String status; // "Downloading", "Seeding", etc.
  final double downloadSpeed; // Bytes per second
  final double uploadSpeed;
  final int seeds;
  final int peers;
  final int totalSize;
  final int bytesRead; // Downloaded

  TorrentStatus({
    required this.title,
    required this.status,
    required this.downloadSpeed,
    required this.uploadSpeed,
    required this.seeds,
    required this.peers,
    required this.totalSize,

    required this.bytesRead,
    required this.data,
  });

  final Map<dynamic, dynamic> data;

  factory TorrentStatus.fromMap(Map<dynamic, dynamic> map) {
    return TorrentStatus(
      title: map['title']?.toString() ?? "Unknown",
      status: map['stat_string']?.toString() ?? "Unknown",
      downloadSpeed: (map['download_speed'] as num?)?.toDouble() ?? 0.0,
      uploadSpeed: (map['upload_speed'] as num?)?.toDouble() ?? 0.0,
      seeds: (map['connected_seeders'] as num?)?.toInt() ?? 0,
      peers: (map['active_peers'] as num?)?.toInt() ?? 0,
      totalSize: (map['torrent_size'] as num?)?.toInt() ?? 0,
      bytesRead: (map['bytes_read'] as num?)?.toInt() ?? 0,
      data: map,
    );
  }

  String get speedString {
    return "${(downloadSpeed / 1024 / 1024).toStringAsFixed(2)} MB/s";
  }

  double get progress {
    if (totalSize == 0) return 0.0;
    return bytesRead / totalSize;
  }
}
