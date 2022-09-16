package graphql.execution.instrumentation;

import graphql.PublicApi;
import graphql.execution.Backdoor;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A simple implementation of {@link InstrumentationContext}
 */
@PublicApi
public class SimpleInstrumentationContext<T> implements InstrumentationContext<T> {

    private static final InstrumentationContext<Object> NO_OP = new InstrumentationContext<Object>() {
        @Override
        public void onDispatched(CompletableFuture<Object> result) {
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
        }
    };

    /**
     * A context that does nothing
     *
     * @param <T> the type needed
     *
     * @return a context that does nothing
     */
    @SuppressWarnings("unchecked")
    public static <T> InstrumentationContext<T> noOp() {
        return (InstrumentationContext<T>) NO_OP;
    }

    /**
     * This creates a no-op {@link InstrumentationContext} if the one pass in is null
     *
     * @param nullableContext a {@link InstrumentationContext} that can be null
     * @param <T>             for two
     *
     * @return a non null {@link InstrumentationContext} that maybe a no-op
     */
    @Nonnull
    public static <T> InstrumentationContext<T> nonNullCtx(InstrumentationContext<T> nullableContext) {
        return nullableContext == null ? noOp() : nullableContext;
    }

    @Nonnull
    public static <T> InstrumentationContext<T> nonNullCtx(Supplier<InstrumentationContext<T>> nullableContext) {
        if (Backdoor.isUseInstrumentation()) {
            return nonNullCtx(nullableContext.get());
        } else {
            return noOp();
        }
    }

    private final BiConsumer<T, Throwable> codeToRunOnComplete;
    private final Consumer<CompletableFuture<T>> codeToRunOnDispatch;

    public SimpleInstrumentationContext() {
        this(null, null);
    }

    private SimpleInstrumentationContext(Consumer<CompletableFuture<T>> codeToRunOnDispatch, BiConsumer<T, Throwable> codeToRunOnComplete) {
        this.codeToRunOnComplete = codeToRunOnComplete;
        this.codeToRunOnDispatch = codeToRunOnDispatch;
    }

    @Override
    public void onDispatched(CompletableFuture<T> result) {
        if (codeToRunOnDispatch != null) {
            codeToRunOnDispatch.accept(result);
        }
    }

    @Override
    public void onCompleted(T result, Throwable t) {
        if (codeToRunOnComplete != null) {
            codeToRunOnComplete.accept(result, t);
        }
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step dispatch.
     *
     * @param codeToRun the code to run on dispatch
     * @param <U>       the generic type
     *
     * @return an instrumentation context
     */
    public static <U> SimpleInstrumentationContext<U> whenDispatched(Consumer<CompletableFuture<U>> codeToRun) {
        return new SimpleInstrumentationContext<>(codeToRun, null);
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step completion.
     *
     * @param codeToRun the code to run on completion
     * @param <U>       the generic type
     *
     * @return an instrumentation context
     */
    public static <U> SimpleInstrumentationContext<U> whenCompleted(BiConsumer<U, Throwable> codeToRun) {
        return new SimpleInstrumentationContext<>(null, codeToRun);
    }

    public static <T> BiConsumer<? super T, ? super Throwable> completeInstrumentationCtxCF(
            InstrumentationContext<T> instrumentationContext, CompletableFuture<T> targetCF) {
        return (result, throwable) -> {
            if (throwable != null) {
                targetCF.completeExceptionally(throwable);
            } else {
                targetCF.complete(result);
            }
            nonNullCtx(instrumentationContext).onCompleted(result, throwable);
        };
    }

}
