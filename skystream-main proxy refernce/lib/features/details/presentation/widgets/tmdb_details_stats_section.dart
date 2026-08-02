import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';
import '../../../../core/models/tmdb_details.dart';

/// Rich "Movie Details" / "Show Details" block (tagline, status, dates, budget, etc.).
class TmdbDetailsStatsSection extends StatelessWidget {
  const TmdbDetailsStatsSection({
    super.key,
    required this.data,
    required this.isMovie,
  });

  final TmdbDetails data;
  final bool isMovie;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final textColor = theme.colorScheme.onSurface;
    final tagline = data.tagline;
    final status = data.tmdbStatus;
    final releaseDateFull = data.releaseDateFull;
    final originalLanguage = data.originalLanguage;
    final originCountry = data.originCountry;
    final budget = data.budget;
    final revenue = data.revenue;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          isMovie
              ? AppLocalizations.of(context)!.movieDetails
              : AppLocalizations.of(context)!.showDetails,
          style: TextStyle(
            color: textColor,
            fontSize: 24,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 24),
        if (tagline.isNotEmpty) ...[
          _DetailItem(
            AppLocalizations.of(context)!.tagline,
            "\"$tagline\"",
            italic: true,
          ),
          const SizedBox(height: 16),
        ],
        Wrap(
          spacing: 40,
          runSpacing: 24,
          children: [
            _DetailItem(AppLocalizations.of(context)!.status, status),
            _DetailItem(
              isMovie
                  ? AppLocalizations.of(context)!.releaseDate
                  : AppLocalizations.of(context)!.firstAirDate,
              releaseDateFull.isNotEmpty
                  ? () {
                      final parsed = DateTime.tryParse(releaseDateFull);
                      if (parsed != null) {
                        return DateFormat('MMMM d, yyyy').format(parsed);
                      }
                      return releaseDateFull;
                    }()
                  : AppLocalizations.of(context)!.unknown,
            ),
            _DetailItem(
              AppLocalizations.of(context)!.originalLanguage,
              originalLanguage,
            ),
            _DetailItem(
              AppLocalizations.of(context)!.originCountry,
              originCountry,
            ),
            if (budget > 0)
              _DetailItem(
                AppLocalizations.of(context)!.budgetLabel,
                NumberFormat.currency(
                  symbol: '\$',
                  decimalDigits: 0,
                ).format(budget),
              ),
            if (revenue > 0)
              _DetailItem(
                AppLocalizations.of(context)!.revenueLabel,
                NumberFormat.currency(
                  symbol: '\$',
                  decimalDigits: 0,
                ).format(revenue),
              ),
          ],
        ),
      ],
    );
  }
}

class _DetailItem extends StatelessWidget {
  const _DetailItem(this.label, this.value, {this.italic = false});

  final String label;
  final String value;
  final bool italic;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final textColor = theme.colorScheme.onSurface;
    final textSecondary = theme.colorScheme.onSurface.withValues(alpha: 0.7);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(
            color: textSecondary,
            fontSize: 12,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            color: textColor,
            fontSize: 14,
            fontWeight: FontWeight.bold,
            fontStyle: italic ? FontStyle.italic : FontStyle.normal,
          ),
        ),
      ],
    );
  }
}
