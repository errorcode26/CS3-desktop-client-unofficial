package publicip

type result[T any] struct {
	Ok  T
	Err error
}

func resultFromTuple[T any](t T, err error) result[T] {
	return result[T]{t, err}
}
