import 'dart:math';

class DartJsUnpacker {
  final String source;

  DartJsUnpacker(this.source);

  bool detect() {
    final js = source.replaceAll(' ', '');
    return RegExp(r'eval\(function\(p,a,c,k,e,[rd]').hasMatch(js);
  }

  String? unpack() {
    try {
      final pRegex = RegExp(
        r"\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)",
        dotAll: true,
      );
      final match = pRegex.firstMatch(source);

      if (match != null && match.groupCount >= 4) {
        final payload = match.group(1)?.replaceAll("\\'", "'") ?? '';
        final radixStr = match.group(2);
        final countStr = match.group(3);
        final symtab = match.group(4)?.split('|') ?? [];

        int radix = 36;
        int count = 0;

        try {
          radix = int.parse(radixStr ?? '36');
        } catch (_) {}
        try {
          count = int.parse(countStr ?? '0');
        } catch (_) {}

        if (symtab.length != count) {
          throw Exception("Unknown p.a.c.k.e.r. encoding");
        }

        final unbase = _Unbase(radix);
        final wordRegex = RegExp(r'\b[a-zA-Z0-9_]+\b');

        String decoded = payload;
        decoded = decoded.replaceAllMapped(wordRegex, (m) {
          final word = m.group(0);
          if (word == null) return '';
          final x = unbase.unbase(word);
          String? value;
          if (x < symtab.length && x >= 0) {
            value = symtab[x];
          }
          if (value != null && value.isNotEmpty) {
            return value;
          }
          return word;
        });

        return decoded;
      }
    } catch (e) {
      // Ignore
    }
    return null;
  }
}

class _Unbase {
  final int radix;
  static const _alphabet62 =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  static const _alphabet95 =
      " !\"#\$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

  String? alphabet;
  Map<String, int>? dictionary;

  _Unbase(this.radix) {
    if (radix > 36) {
      if (radix < 62) {
        alphabet = _alphabet62.substring(0, radix);
      } else if (radix >= 63 && radix <= 94) {
        alphabet = _alphabet95.substring(0, radix);
      } else if (radix == 62) {
        alphabet = _alphabet62;
      } else if (radix == 95) {
        alphabet = _alphabet95;
      }

      if (alphabet != null) {
        dictionary = {};
        for (int i = 0; i < alphabet!.length; i++) {
          dictionary![alphabet!.substring(i, i + 1)] = i;
        }
      }
    }
  }

  int unbase(String str) {
    int ret = 0;
    if (alphabet == null) {
      ret = int.parse(str, radix: radix);
    } else {
      final tmp = str.split('').reversed.join('');
      for (int i = 0; i < tmp.length; i++) {
        ret += (pow(radix, i) * (dictionary![tmp.substring(i, i + 1)] ?? 0))
            .toInt();
      }
    }
    return ret;
  }
}
