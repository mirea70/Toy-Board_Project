package toy.board.read.cache;

@FunctionalInterface
public interface OptimizedCacheOriginDataSupplier<T> {
    T get() throws Throwable;
}
