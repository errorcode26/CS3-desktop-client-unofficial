import 'package:flutter/material.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';

class CustomBottomNavBar extends StatelessWidget {
  final int currentIndex;
  final void Function(int) onTap;

  const CustomBottomNavBar({
    super.key,
    required this.currentIndex,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    // Glassmorphism-style background if desired, but native NavBarr is safer for initial setup.
    // We will use a highly customized container to give it that floating/premium look.

    return Container(
      decoration: BoxDecoration(color: Theme.of(context).colorScheme.surface),
      child: NavigationBar(
        selectedIndex: currentIndex,
        onDestinationSelected: onTap,
        backgroundColor: Colors.transparent,
        indicatorColor: Theme.of(
          context,
        ).colorScheme.primary.withValues(alpha: 0.15),
        elevation: 0,
        labelBehavior:
            NavigationDestinationLabelBehavior.alwaysHide, // Cleaner look
        height: 65,
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.home_outlined),
            selectedIcon: Icon(
              Icons.home,
              color: Theme.of(context).colorScheme.primary,
            ),
            label: AppLocalizations.of(context)!.home,
          ),
          NavigationDestination(
            icon: const Icon(Icons.search_outlined),
            selectedIcon: Icon(
              Icons.search,
              color: Theme.of(context).colorScheme.primary,
            ),
            label: AppLocalizations.of(context)!.search,
          ),
          NavigationDestination(
            icon: const Icon(Icons.explore_outlined),
            selectedIcon: Icon(
              Icons.explore,
              color: Theme.of(context).colorScheme.primary,
            ),
            label: AppLocalizations.of(context)!.explore,
          ),
          NavigationDestination(
            icon: const Icon(Icons.video_library_outlined),
            selectedIcon: Icon(
              Icons.video_library,
              color: Theme.of(context).colorScheme.primary,
            ),
            label: AppLocalizations.of(context)!.library,
          ),
          NavigationDestination(
            icon: const Icon(Icons.settings_outlined),
            selectedIcon: Icon(
              Icons.settings,
              color: Theme.of(context).colorScheme.primary,
            ),
            label: AppLocalizations.of(context)!.settings,
          ),
        ],
      ),
    );
  }
}
