import '../../../../core/domain/entity/multimedia_item.dart';

sealed class HomeState {
  const HomeState();
}

class HomeLoading extends HomeState {
  const HomeLoading();
}

class HomeNoProvider extends HomeState {
  const HomeNoProvider();
}

class HomeOffline extends HomeState {
  const HomeOffline();
}

class HomeError extends HomeState {
  final String message;
  const HomeError(this.message);
}

class HomeSuccess extends HomeState {
  final Map<String, List<MultimediaItem>> data;
  const HomeSuccess(this.data);
}
