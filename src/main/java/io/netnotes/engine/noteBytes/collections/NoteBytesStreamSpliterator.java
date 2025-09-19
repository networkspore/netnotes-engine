package io.netnotes.engine.noteBytes.collections;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesPair;

public class NoteBytesStreamSpliterator implements Spliterator<NoteBytesPair> {
    private final Spliterator<NoteBytes> source;

    public NoteBytesStreamSpliterator(Spliterator<NoteBytes> source) {
        this.source = source;
    }

    @Override
    public boolean tryAdvance(Consumer<? super NoteBytesPair> action) {
        final NoteBytes[] buffer = new NoteBytes[2];

        if (!source.tryAdvance(nb -> buffer[0] = nb)) {
            return false; // no first element → nothing left
        }
        if (!source.tryAdvance(nb -> buffer[1] = nb)) {
            throw new IllegalArgumentException("Odd number of pairs");
           // buffer[1] = null; // odd count → pad with null (optional behavior)
        }

        action.accept(new NoteBytesPair(buffer[0], buffer[1]));
        return true;
    }

    @Override
    public Spliterator<NoteBytesPair> trySplit() {
        // For simplicity, disable parallelism
        return null;
    }

    @Override
    public long estimateSize() {
        long est = source.estimateSize();
        return est == Long.MAX_VALUE ? est : (est + 1) / 2;
    }

    @Override
    public int characteristics() {
        return source.characteristics() & ~Spliterator.SIZED;
    }

    public static Stream<NoteBytesPair> pairStream(Stream<NoteBytes> base) {
        return StreamSupport.stream(new NoteBytesStreamSpliterator(base.spliterator()), false);
    }
}
