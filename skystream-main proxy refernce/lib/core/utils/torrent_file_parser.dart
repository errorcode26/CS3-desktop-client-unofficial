import 'dart:io';
import 'dart:typed_data';
import 'dart:convert';
import 'package:crypto/crypto.dart';

class TorrentFileParser {
  /// Reads a .torrent file and returns a Magnet link.
  static Future<String> getMagnetLink(String filePath) async {
    final file = File(filePath);
    if (!await file.exists()) {
      throw Exception("File not found: $filePath");
    }

    final bytes = await file.readAsBytes();
    final decoded = _decode(bytes);

    if (decoded is! Map) {
      throw Exception("Invalid torrent file format");
    }

    final info = decoded['info'];
    if (info == null || info is! Map) {
      throw Exception("Invalid torrent file: missing info dictionary");
    }

    // Calculate Info Hash => SHA1 of the 'info' dictionary value (without decoding/re-encoding if possible, but we need raw bytes)
    // Since we decoded it, we need to locate the raw bytes of the 'info' dictionary in the file optimally.
    // simpler approach: re-encode the info map to bencode and hash it.

    final encodedInfo = _encode(info);
    final digest = sha1.convert(encodedInfo);
    final hashHex = digest.toString();

    // Construct Magnet Link matches standard format
    // magnet:?xt=urn:btih:<HASH>&dn=<NAME>

    String name = "Unknown";
    if (info.containsKey('name')) {
      // Name is bytes usually
      final nameBytes = info['name'];
      if (nameBytes is Uint8List) {
        try {
          name = utf8.decode(nameBytes);
        } catch (_) {
          name = String.fromCharCodes(nameBytes);
        }
      } else if (nameBytes is String) {
        name = nameBytes;
      }
    }

    final encodedName = Uri.encodeComponent(name);
    return "magnet:?xt=urn:btih:$hashHex&dn=$encodedName";
  }

  // --- Bencode Decoder ---

  static dynamic _decode(Uint8List bytes) {
    int index = 0;

    dynamic decodeNext() {
      if (index >= bytes.length) return null;
      final char = bytes[index];

      // Integer: i<number>e
      if (char == 105) {
        // 'i'
        index++;
        final end = bytes.indexOf(101, index); // 'e'
        if (end == -1) throw Exception("Invalid integer");
        final str = String.fromCharCodes(bytes.sublist(index, end));
        index = end + 1;
        return int.parse(str);
      }
      // List: l<items>e
      else if (char == 108) {
        // 'l'
        index++;
        final list = <dynamic>[];
        while (bytes[index] != 101) {
          // 'e'
          list.add(decodeNext());
        }
        index++;
        return list;
      }
      // Dictionary: d<key><value>e
      else if (char == 100) {
        // 'd'
        index++;
        final map = <String, dynamic>{};
        while (bytes[index] != 101) {
          // 'e'
          // Keys is always strings (byte strings)
          final keyDynamic = decodeNext();
          String key;
          if (keyDynamic is Uint8List) {
            try {
              key = utf8.decode(keyDynamic);
            } catch (_) {
              key = String.fromCharCodes(keyDynamic);
            }
          } else if (keyDynamic is String) {
            key = keyDynamic;
          } else {
            throw Exception("Dictionary Key must be string, got $keyDynamic");
          }

          final value = decodeNext();
          map[key] = value;
        }
        index++;
        return map;
      }
      // String: <length>:<contents>
      else if (char >= 48 && char <= 57) {
        // '0'-'9'
        final colon = bytes.indexOf(58, index); // ':'
        if (colon == -1) throw Exception("Invalid string length");
        final lenStr = String.fromCharCodes(bytes.sublist(index, colon));
        final len = int.parse(lenStr);
        index = colon + 1;
        final data = bytes.sublist(index, index + len);
        index += len;
        // Return Uint8List for strings to preserve binary data (like pieces or hashes)
        return data;
      }

      throw Exception("Unexpected character: $char at index $index");
    }

    return decodeNext();
  }

  // --- Bencode Encoder (needed for InfoHash calculation) ---

  static Uint8List _encode(dynamic data) {
    final BytesBuilder builder = BytesBuilder();

    void encodeRecursive(dynamic obj) {
      if (obj is int) {
        builder.addByte(105); // i
        builder.add(utf8.encode(obj.toString()));
        builder.addByte(101); // e
      } else if (obj is String) {
        final bytes = utf8.encode(obj);
        builder.add(utf8.encode(bytes.length.toString()));
        builder.addByte(58); // :
        builder.add(bytes);
      } else if (obj is Uint8List) {
        builder.add(utf8.encode(obj.length.toString()));
        builder.addByte(58); // :
        builder.add(obj);
      } else if (obj is List) {
        builder.addByte(108); // l
        for (final item in obj) {
          encodeRecursive(item);
        }
        builder.addByte(101); // e
      } else if (obj is Map) {
        builder.addByte(100); // d
        // Keys must be sorted strings
        final keys = obj.keys.toList();
        keys.sort((a, b) => a.toString().compareTo(b.toString()));

        for (final key in keys) {
          encodeRecursive(key); // Encoded as string
          encodeRecursive(obj[key]);
        }
        builder.addByte(101); // e
      } else {
        throw Exception("Unsupported type for bencoding: ${obj.runtimeType}");
      }
    }

    encodeRecursive(data);
    return builder.toBytes();
  }
}
