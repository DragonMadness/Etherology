package ru.feytox.etherology.util.misc;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

@RequiredArgsConstructor(staticName = "of")
public class CompletableStream<T> extends CompletableFuture<Stream<T>> {

    private final CompletableFuture<Stream<T>> future;

    public <M> CompletableStream<M> thenMapAsync(Function<? super T, ? extends M> mapper) {
        return new CompletableStream<>(future.thenApplyAsync(stream -> stream.map(mapper)));
    }

    public <M> CompletableStream<M> thenFlatMapAsync(Function<? super T, ? extends Stream<M>> mapper) {
        return new CompletableStream<>(future.thenApplyAsync(stream -> stream.flatMap(mapper)));
    }
}
