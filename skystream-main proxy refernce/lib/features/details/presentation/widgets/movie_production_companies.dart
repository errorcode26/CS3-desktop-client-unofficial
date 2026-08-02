import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../../shared/widgets/desktop_scroll_wrapper.dart';
import '../../../../core/utils/responsive_breakpoints.dart';
import '../../../../core/models/tmdb_details.dart';

class MovieProductionCompanies extends StatefulWidget {
  final List<TmdbProductionCompany> productionCompanies;
  final Color? textColor;
  final Color? textSecondary;

  final bool isLoading;

  const MovieProductionCompanies({
    super.key,
    required this.productionCompanies,
    this.isLoading = false,
    this.textColor,
    this.textSecondary,
  });

  @override
  State<MovieProductionCompanies> createState() =>
      _MovieProductionCompaniesState();
}

class _MovieProductionCompaniesState extends State<MovieProductionCompanies> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.isLoading && widget.productionCompanies.isEmpty) {
      return const SizedBox.shrink();
    }

    final isDesktop = context.isDesktop;
    final displayCount = widget.isLoading
        ? 4
        : widget.productionCompanies.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (isDesktop) ...[
          Text(
            "Production",
            style: TextStyle(
              color: widget.textColor,
              fontSize: 24,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 50, // Reduced for TV
            child: DesktopScrollWrapper(
              controller: _scrollController,
              child: ListView.separated(
                controller: _scrollController,
                clipBehavior: Clip.none,
                scrollDirection: Axis.horizontal,
                itemCount: displayCount,
                separatorBuilder: (_, _) => const SizedBox(width: 24),
                itemBuilder: (context, index) => widget.isLoading
                    ? _buildShimmerItem(context)
                    : _buildDesktopItem(context, index),
              ),
            ),
          ),
          const SizedBox(height: 50),
        ] else ...[
          Text(
            "PRODUCTION",
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurface,
              fontSize: 16,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.0,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 50,
            child: ListView.builder(
              clipBehavior: Clip.none,
              scrollDirection: Axis.horizontal,
              itemCount: displayCount,
              itemBuilder: (context, index) => widget.isLoading
                  ? _buildShimmerItem(context)
                  : _buildMobileItem(context, index),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ],
    );
  }

  Widget _buildDesktopItem(BuildContext context, int index) {
    final c = widget.productionCompanies[index];
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final hasLogo = c.logoImageUrl != null && c.logoImageUrl!.isNotEmpty;

    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: isDark ? Colors.white : Colors.black.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: hasLogo
          ? CachedNetworkImage(
              imageUrl: c.logoImageUrl!,
              height: 20,
              fit: BoxFit.contain,
              placeholder: (_, _) => const SizedBox(width: 20, height: 20),
              errorWidget: (_, _, _) => _buildTextLogo(c.name, isDark, 8),
            )
          : _buildTextLogo(c.name, isDark, 8),
    );
  }

  Widget _buildMobileItem(BuildContext context, int index) {
    final company = widget.productionCompanies[index];
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final hasLogo =
        company.logoImageUrl != null && company.logoImageUrl!.isNotEmpty;

    return Container(
      margin: const EdgeInsets.only(right: 16),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: isDark ? Colors.white : Colors.black.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: hasLogo
          ? CachedNetworkImage(
              imageUrl: company.logoImageUrl!,
              fit: BoxFit.contain,
              width: 100,
              placeholder: (_, _) => const SizedBox.shrink(),
              errorWidget: (_, _, _) =>
                  _buildTextLogo(company.name, isDark, 10),
            )
          : _buildTextLogo(company.name, isDark, 10),
    );
  }

  Widget _buildTextLogo(String name, bool isDark, double fontSize) {
    return Center(
      child: Text(
        name,
        style: TextStyle(
          color: isDark ? Colors.black : Colors.black87,
          fontSize: fontSize,
          fontWeight: FontWeight.bold,
        ),
        textAlign: TextAlign.center,
      ),
    );
  }

  Widget _buildShimmerItem(BuildContext context) {
    return Container(
      width: 100,
      margin: const EdgeInsets.only(right: 16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
    );
  }
}
