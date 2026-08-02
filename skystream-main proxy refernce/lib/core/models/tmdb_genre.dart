import 'package:html_unescape/html_unescape.dart';

class TmdbGenre {
  static final _unescape = HtmlUnescape();
  final int id;
  final String name;

  const TmdbGenre({required this.id, required this.name});

  TmdbGenre withName(String newName) => TmdbGenre(id: id, name: newName);

  factory TmdbGenre.fromJson(Map<String, dynamic> json) {
    return TmdbGenre(
      id: json['id'] as int,
      name: json['name'] != null
          ? _unescape.convert(json['name'] as String)
          : '',
    );
  }
}
