import '../../../../core/domain/entity/multimedia_item.dart';

sealed class LibraryState {
  const LibraryState();
}

class LibraryLoading extends LibraryState {
  const LibraryLoading();
}

class LibraryEmpty extends LibraryState {
  const LibraryEmpty();
}

class LibrarySuccess extends LibraryState {
  final List<MultimediaItem> items;
  const LibrarySuccess(this.items);
}

class LibraryError extends LibraryState {
  final String message;
  const LibraryError(this.message);
}
