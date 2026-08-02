import 'package:html_unescape/html_unescape.dart';
import '../config/tmdb_config.dart';
import '../domain/entity/multimedia_item.dart';
import '../utils/image_fallbacks.dart';

// Reuse unescape from parent or create local
final _unescape = HtmlUnescape();

class TmdbDetails extends MultimediaItem {
  final int runtime;
  final String certification;
  final String director;
  final List<TmdbSeason> seasons;
  final List<TmdbCast> tmdbCast;
  final List<String> genres;
  final List<TmdbVideo> tmdbTrailers;
  final List<TmdbProductionCompany> productionCompanies;
  final String tmdbStatus;
  final int budget;
  final int revenue;
  final String tagline;
  final String originCountry;
  final String originalLanguage;
  final String releaseDateFull;

  TmdbDetails({
    required int id,
    required String mediaType,
    required super.title,
    String? posterPath,
    String? backdropPath,
    required String releaseDate,
    required double voteAverage,
    required String overview,
    super.logoUrl,
    String? genresStr,
    super.imdbId,
    super.source,
    super.nextAiring,
    dynamic sourceItem,
    int? tmdbId,
    required this.runtime,
    required this.certification,
    required this.director,
    required this.seasons,
    required this.tmdbCast,
    required this.genres,
    required this.tmdbTrailers,
    required this.productionCompanies,
    required this.tmdbStatus,
    required this.budget,
    required this.revenue,
    required this.tagline,
    required this.originCountry,
    required this.originalLanguage,
    required this.releaseDateFull,
  }) : super(
         url: '', // Resolved via provider
         posterUrl: posterPath != null
             ? (posterPath.startsWith('http')
                   ? posterPath
                   : '${TmdbConfig.posterSizeUrl}$posterPath')
             : '',
         bannerUrl: backdropPath != null
             ? (backdropPath.startsWith('http')
                   ? backdropPath
                   : '${TmdbConfig.backdropSizeUrl}$backdropPath')
             : null,
         description: overview,
         contentType: MultimediaItem.parseContentType(mediaType),
         tmdbId: tmdbId ?? id,
         score: voteAverage,
         tags: genresStr?.split(' | '),
       );

  factory TmdbDetails.fromJson(Map<String, dynamic> json, String languageCode) {
    // Determine media type
    final String mType =
        (json['media_type'] as String?) ??
        (json['title'] != null ? 'movie' : 'tv');
    final isMovie = mType == 'movie';

    var title = json['title'] != null || json['name'] != null
        ? _unescape.convert((json['title'] ?? json['name']) as String)
        : 'Unknown';
    var overview = json['overview'] != null
        ? _unescape.convert(json['overview'] as String)
        : '';

    // Use English translation if available to avoid empty fields
    if (json['translations'] != null) {
      final translations = List<Map<String, dynamic>>.from(
        ((json['translations'] as Map<String, dynamic>)['translations']
                as List?) ??
            const <dynamic>[],
      );
      final enTrans = translations.firstWhere(
        (t) => t['iso_639_1'] == 'en',
        orElse: () => <String, dynamic>{},
      );
      if (enTrans.isNotEmpty && enTrans['data'] != null) {
        final enData = enTrans['data'] as Map<String, dynamic>;
        final enTitle = (enData['title'] ?? enData['name']) as String?;
        if (enTitle != null && enTitle.isNotEmpty) {
          title = _unescape.convert(enTitle);
        }
        final enOverview = enData['overview'] as String?;
        if (enOverview != null && enOverview.isNotEmpty) {
          overview = _unescape.convert(enOverview);
        }
      }
    }

    final date =
        (json['release_date'] as String?) ??
        (json['first_air_date'] as String?) ??
        '';
    final voteAvg = (json['vote_average'] as num?)?.toDouble() ?? 0.0;

    final runtime = isMovie
        ? (json['runtime'] ?? 0)
        : ((json['episode_run_time'] as List?)?.isNotEmpty == true
              ? json['episode_run_time'][0]
              : 0);

    // Determine Certification
    String certification = isMovie ? "PG-13" : "TV-14";
    if (isMovie) {
      final releaseDates = json['release_dates'] != null
          ? ((json['release_dates'] as Map<String, dynamic>)['results']
                    as List?) ??
                const <dynamic>[]
          : const <dynamic>[];
      if (releaseDates.isNotEmpty) {
        final usRelease = releaseDates.firstWhere(
          (dynamic r) => (r as Map)['iso_3166_1'] == 'US',
          orElse: () => null,
        );
        if (usRelease != null) {
          final certs =
              (usRelease as Map<String, dynamic>)['release_dates'] as List;
          if (certs.isNotEmpty && (certs.first as Map)['certification'] != '') {
            certification =
                (certs.first as Map<String, dynamic>)['certification']
                    as String;
          }
        }
      }
    } else {
      final contentRatings = json['content_ratings'] != null
          ? ((json['content_ratings'] as Map<String, dynamic>)['results']
                    as List?) ??
                const <dynamic>[]
          : const <dynamic>[];
      if (contentRatings.isNotEmpty) {
        final usRating = contentRatings.firstWhere(
          (dynamic r) => (r as Map)['iso_3166_1'] == 'US',
          orElse: () => null,
        );
        if (usRating != null) {
          certification =
              (usRating as Map<String, dynamic>)['rating'] as String;
        }
      }
    }

    final genresList = List<Map<String, dynamic>>.from(
      (json['genres'] as List?) ?? const <dynamic>[],
    );
    final genres = genresList.map((g) => g['name'].toString()).toList();
    final genresStr = genres.join(' | ');

    final seasons = !isMovie
        ? List<Map<String, dynamic>>.from(
            (json['seasons'] as List?) ?? const <dynamic>[],
          ).map(TmdbSeason.fromJson).toList()
        : <TmdbSeason>[];

    final credits =
        (json['credits'] as Map<String, dynamic>?) ?? const <String, dynamic>{};
    final castList = List<Map<String, dynamic>>.from(
      (credits['cast'] as List?) ?? const <dynamic>[],
    );
    final cast = castList.map(TmdbCast.fromJson).toList();

    // Find Director / Creator
    String director = "Unknown";
    final crew = List<Map<String, dynamic>>.from(
      (credits['crew'] as List?) ?? const <dynamic>[],
    );
    if (isMovie) {
      final dir = crew.firstWhere(
        (m) => m['job'] == 'Director',
        orElse: () => <String, dynamic>{'name': 'Unknown'},
      );
      director = dir['name'] as String;
    } else {
      final creators = json['created_by'] as List?;
      if (creators != null && creators.isNotEmpty) {
        director = creators.map((c) => (c as Map)['name'] as String).join(', ');
      }
    }

    final videos = List<Map<String, dynamic>>.from(
      json['videos'] != null
          ? ((json['videos'] as Map<String, dynamic>)['results'] as List?) ??
                const <dynamic>[]
          : const <dynamic>[],
    );
    final tmdbTrailers = videos
        .where(
          (v) =>
              v['site'] == 'YouTube' &&
              (v['type'] == 'Trailer' || v['type'] == 'Teaser'),
        )
        .map((v) => TmdbVideo.fromJson(v))
        .toList();

    final productionCompaniesList = List<Map<String, dynamic>>.from(
      (json['production_companies'] as List?) ?? const <dynamic>[],
    );
    final productionCompanies = productionCompaniesList
        .map(TmdbProductionCompany.fromJson)
        .toList();

    final tmdbStatus = (json['status'] as String?) ?? 'Unknown';
    final budget = json['budget'] as num? ?? 0;
    final revenue = json['revenue'] as num? ?? 0;
    final tagline = (json['tagline'] as String?) ?? '';
    final originCountry = (json['origin_country'] as List?)?.join(', ') ?? 'US';
    final originalLanguage =
        (json['original_language'] as String?)?.toUpperCase() ?? 'EN';

    // Extract IMDB ID
    String? imdbId = json['imdb_id'] as String?;
    if (imdbId == null || imdbId.isEmpty) {
      final externalIds = json['external_ids'] as Map<String, dynamic>?;
      if (externalIds != null) {
        imdbId = externalIds['imdb_id'] as String?;
      }
    }

    return TmdbDetails(
      id: json['id'] as int? ?? 0,
      mediaType: mType,
      title: title,
      posterPath: json['poster_path'] as String?,
      backdropPath: json['backdrop_path'] as String?,
      releaseDate: date,
      voteAverage: voteAvg,
      overview: overview,
      logoUrl:
          json['logo_url'] as String?, // Might be populated before/after this
      genresStr: genresStr,
      runtime: (runtime as num).toInt(),
      certification: certification,
      director: director,
      seasons: seasons,
      tmdbCast: cast,
      genres: genres,
      tmdbTrailers: tmdbTrailers,
      productionCompanies: productionCompanies,
      tmdbStatus: tmdbStatus,
      budget: budget.toInt(),
      revenue: revenue.toInt(),
      tagline: tagline,
      originCountry: originCountry,
      originalLanguage: originalLanguage,
      releaseDateFull: date,
      imdbId: imdbId,
    );
  }

  @override
  TmdbDetails copyWith({
    String? title,
    String? url,
    String? posterUrl,
    String? bannerUrl,
    String? logoUrl,
    String? description,
    MultimediaContentType? contentType,
    List<Episode>? episodes,
    String? provider,
    Map<String, String>? headers,
    int? year,
    double? score,
    int? duration,
    ShowStatus? status,
    List<String>? tags,
    String? contentRating,
    List<Actor>? cast,
    List<Trailer>? trailers,
    List<MultimediaItem>? recommendations,
    Map<String, String>? syncData,
    String? playbackPolicy,
    bool? isAdult,
    NextAiring? nextAiring,
    List<StreamResult>? streams,
    int? tmdbId,
    String? imdbId,
    String? source,
    // Subclass specific fields:
    int? id,
    String? mediaType,
    String? releaseDate,
    double? voteAverage,
    String? overview,
    String? genresStr,
    int? runtime,
    String? certification,
    String? director,
    List<TmdbSeason>? seasons,
    List<TmdbCast>? tmdbCast,
    List<String>? genres,
    List<TmdbVideo>? tmdbTrailers,
    List<TmdbProductionCompany>? productionCompanies,
    String? tmdbStatus,
    int? budget,
    int? revenue,
    String? tagline,
    String? originCountry,
    String? originalLanguage,
    String? releaseDateFull,
  }) {
    return TmdbDetails(
      id: id ?? this.id,
      mediaType:
          mediaType ??
          (contentType != null
              ? contentType.name
              : (this.contentType == MultimediaContentType.movie
                    ? 'movie'
                    : 'tv')),
      title: title ?? this.title,
      posterPath: posterUrl ?? this.posterUrl,
      backdropPath: bannerUrl ?? this.bannerUrl,
      releaseDate: releaseDate ?? this.releaseDateFull,
      voteAverage: voteAverage ?? score ?? this.score ?? 0.0,
      overview: overview ?? description ?? this.description ?? '',
      logoUrl: logoUrl ?? this.logoUrl,
      genresStr: genresStr ?? this.genresStr,
      imdbId: imdbId ?? this.imdbId,
      source: source ?? this.source,
      nextAiring: nextAiring ?? this.nextAiring,
      tmdbId: tmdbId ?? this.tmdbId,
      runtime: runtime ?? this.runtime,
      certification: certification ?? this.certification,
      director: director ?? this.director,
      seasons: seasons ?? this.seasons,
      tmdbCast: tmdbCast ?? this.tmdbCast,
      genres: genres ?? this.genres,
      tmdbTrailers: tmdbTrailers ?? this.tmdbTrailers,
      productionCompanies: productionCompanies ?? this.productionCompanies,
      tmdbStatus: tmdbStatus ?? this.tmdbStatus,
      budget: budget ?? this.budget,
      revenue: revenue ?? this.revenue,
      tagline: tagline ?? this.tagline,
      originCountry: originCountry ?? this.originCountry,
      originalLanguage: originalLanguage ?? this.originalLanguage,
      releaseDateFull: releaseDateFull ?? this.releaseDateFull,
    );
  }
}

class TmdbSeason {
  final int seasonNumber;
  final String name;
  final String? posterPath;
  final int episodeCount;
  final String? airDate;

  TmdbSeason({
    required this.seasonNumber,
    required this.name,
    this.posterPath,
    required this.episodeCount,
    this.airDate,
  });

  factory TmdbSeason.fromJson(Map<String, dynamic> json) {
    return TmdbSeason(
      seasonNumber: (json['season_number'] as int?) ?? 0,
      name: json['name'] != null
          ? _unescape.convert(json['name'] as String)
          : '',
      posterPath: json['poster_path'] as String?,
      episodeCount: (json['episode_count'] as int?) ?? 0,
      airDate: json['air_date'] as String?,
    );
  }

  String? get posterImageUrl =>
      AppImageFallbacks.tmdbPoster(posterPath, label: name);
}

class TmdbCast {
  final String name;
  final String character;
  final String? profilePath;

  TmdbCast({required this.name, required this.character, this.profilePath});

  factory TmdbCast.fromJson(Map<String, dynamic> json) {
    return TmdbCast(
      name: json['name'] != null
          ? _unescape.convert(json['name'] as String)
          : 'Unknown',
      character: json['character'] != null
          ? _unescape.convert(json['character'] as String)
          : '',
      profilePath: json['profile_path'] as String?,
    );
  }

  String? get profileImageUrl =>
      AppImageFallbacks.tmdbProfile(profilePath, label: name);
}

class TmdbVideo {
  final String key;
  final String type;
  final String name;

  TmdbVideo({required this.key, required this.type, required this.name});

  factory TmdbVideo.fromJson(Map<String, dynamic> json) {
    return TmdbVideo(
      key: (json['key'] as String?) ?? '',
      type: (json['type'] as String?) ?? '',
      name: (json['name'] as String?) ?? 'Trailer',
    );
  }
}

class TmdbProductionCompany {
  final String name;
  final String? logoPath;

  TmdbProductionCompany({required this.name, this.logoPath});

  factory TmdbProductionCompany.fromJson(Map<String, dynamic> json) {
    return TmdbProductionCompany(
      name: (json['name'] as String?) ?? '',
      logoPath: json['logo_path'] as String?,
    );
  }

  String? get logoImageUrl => AppImageFallbacks.tmdbLogo(logoPath, label: name);
}
