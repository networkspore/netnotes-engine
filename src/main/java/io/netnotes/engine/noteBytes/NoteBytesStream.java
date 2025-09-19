
package io.netnotes.engine.noteBytes;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;

public class NoteBytesStream implements Stream<NoteBytes>{
    
    private Stream<NoteBytes> m_stream = null;

    public NoteBytesStream(byte[] bytes) throws IOException{
        init(bytes);
    }


    public NoteBytesStream(NoteBytes noteBytes) throws IOException{
        init(noteBytes.get());
    }


    public NoteBytesStream(NoteBytesReader reader, int length) throws IOException{
        init(reader, length);
    }

    public NoteBytesStream(NoteBytesReader reader) throws IOException{
        init(reader);
    }


    public static Stream<NoteBytes> getStream(NoteBytesReader reader) throws IOException{
        
        Stream.Builder<NoteBytes> noteBytesBuilder = Stream.builder();
        NoteBytes value = reader.nextNoteBytes();

        while(value != null){
            noteBytesBuilder.accept(value);
            value = reader.nextNoteBytes();
        }
        return noteBytesBuilder.build();
    }

    public static Stream<NoteBytes> getStream(NoteBytesReader reader, int length) throws IOException{
         // Parse plugin entries
        int bytesRemaining = length;
        Stream.Builder<NoteBytes> noteBytesBuilder = Stream.builder();
        while (bytesRemaining > 0) {
            NoteBytes value = reader.nextNoteBytes();
            
            if (value == null) {
                throw new IOException("Unexpected end of stream");
            }
            
            noteBytesBuilder.add(value);
            
            bytesRemaining -= value.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        }

        return noteBytesBuilder.build();
    }

    public static Stream<NoteBytes> getStream(byte[] bytes) throws IOException{
        Stream.Builder<NoteBytes> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes);
            offset += (5 + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    }

    public void init(NoteBytesReader reader) throws IOException{
        if(m_stream != null){
            m_stream.close();
        }
        m_stream = getStream(reader);
    }
    public void init(NoteBytesReader reader, int length) throws IOException{
        if(m_stream != null){
            m_stream.close();
        }
        m_stream = getStream(reader, length);
    }
    public void init(byte[] bytes) throws IOException{
        if(m_stream != null){
            m_stream.close();
        }
        m_stream = getStream(bytes);
    }

    public int rawByteLength(){
    
        return m_stream.mapToInt(NoteBytes::byteLength).sum();
    }

    public int noteBytesArrayByteLength(){
       
        
        return rawByteLength() + (int)(m_stream.count() * NoteBytesMetaData.STANDARD_META_DATA_SIZE);
    }


    public NoteBytes getNoteBytes() {

        return getNoteBytesArray();
    }

    public NoteBytes getNoteBytesArray() {

        byte[] bytes = new byte[noteBytesArrayByteLength()];
        NoteBytes[] noteBytes = getAsArray();
        int offset = 0;
        for(NoteBytes value : noteBytes) {
            offset = NoteBytes.writeNote(value, bytes, offset);
        }
        return new NoteBytesArray(bytes);
    }

    public NoteBytesMap getAsNoteBytesMap(){
        return new NoteBytesMap(NoteBytesStreamSpliterator.pairStream(m_stream));
    }


    public NoteBytes[] getAsArray(){
        return m_stream.toArray(NoteBytes[]::new);
    }


    public List<NoteBytes> getAsList(){
        return m_stream.toList();
    }


    @Override
    public void close() {
        m_stream.close();
    }

    @Override
    public boolean isParallel() {
        return m_stream.isParallel();
    }

 
    @Override
    public Iterator<NoteBytes> iterator() {
        return m_stream.iterator();
    }

    @Override
    public Stream<NoteBytes> onClose(Runnable closeHandler) {
        return m_stream.onClose(closeHandler);
    }

    @Override
    public Stream<NoteBytes> parallel() {
        return m_stream.parallel();
    }

    @Override
    public Stream<NoteBytes> sequential() {
        return m_stream.sequential();
    }

    @Override
    public Spliterator<NoteBytes> spliterator() {
        return m_stream.spliterator();
    }

    @Override
    public Stream<NoteBytes> unordered() {
        return m_stream.unordered();
    }

    @Override
    public boolean allMatch(Predicate<? super NoteBytes> predicate) {
        return m_stream.allMatch(predicate);
    }

    @Override
    public boolean anyMatch(Predicate<? super NoteBytes> predicate) {
        return m_stream.anyMatch(predicate);
    }

    @Override
    public <R, A> R collect(Collector<? super NoteBytes, A, R> collector) {
        return m_stream.collect(collector);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super NoteBytes> accumulator,
                        BiConsumer<R, R> combiner) {
        return m_stream.collect(supplier, accumulator, combiner);
    }

    @Override
    public long count() {
        return m_stream.count();
    }

    @Override
    public Stream<NoteBytes> distinct() {
        return m_stream.distinct();
    }

    @Override
    public Stream<NoteBytes> filter(Predicate<? super NoteBytes> predicate) {
        return m_stream.filter(predicate);
    }

    @Override
    public Optional<NoteBytes> findAny() {
        return m_stream.findAny();
    }

    @Override
    public Optional<NoteBytes> findFirst() {
        return m_stream.findFirst();
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super NoteBytes, ? extends Stream<? extends R>> mapper) {
        return m_stream.flatMap(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super NoteBytes, ? extends DoubleStream> mapper) {
        return m_stream.flatMapToDouble(mapper);
    }

    @Override
    public IntStream flatMapToInt(Function<? super NoteBytes, ? extends IntStream> mapper) {
        return m_stream.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super NoteBytes, ? extends LongStream> mapper) {
        return m_stream.flatMapToLong(mapper);
    }

    @Override
    public void forEach(Consumer<? super NoteBytes> action) {
        m_stream.forEach(action);
    }

    @Override
    public void forEachOrdered(Consumer<? super NoteBytes> action) {
        m_stream.forEachOrdered(action);
    }

    @Override
    public Stream<NoteBytes> limit(long maxSize) {
        return m_stream.limit(maxSize);
    }

    @Override
    public <R> Stream<R> map(Function<? super NoteBytes, ? extends R> mapper) {
        return m_stream.map(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super NoteBytes> mapper) {
        return m_stream.mapToDouble(mapper);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super NoteBytes> mapper) {
        return m_stream.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super NoteBytes> mapper) {
        return m_stream.mapToLong(mapper);
    }

    @Override
    public Optional<NoteBytes> max(Comparator<? super NoteBytes> comparator) {
        return m_stream.max(comparator);
    }

    @Override
    public Optional<NoteBytes> min(Comparator<? super NoteBytes> comparator) {
        return m_stream.min(comparator);
    }

    @Override
    public boolean noneMatch(Predicate<? super NoteBytes> predicate) {
        return m_stream.noneMatch(predicate);
    }

    @Override
    public Stream<NoteBytes> peek(Consumer<? super NoteBytes> action) {
        return m_stream.peek(action);
    }

    @Override
    public Optional<NoteBytes> reduce(BinaryOperator<NoteBytes> accumulator) {
        return m_stream.reduce(accumulator);
    }

    @Override
    public NoteBytes reduce(NoteBytes identity, BinaryOperator<NoteBytes> accumulator) {
        return m_stream.reduce(identity, accumulator);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super NoteBytes, U> accumulator, BinaryOperator<U> combiner) {
        return m_stream.reduce(identity, accumulator, combiner);
    }

    @Override
    public Stream<NoteBytes> skip(long n) {
        return m_stream.skip(n);
    }

    @Override
    public Stream<NoteBytes> sorted() {
        return m_stream.sorted();
    }

    @Override
    public Stream<NoteBytes> sorted(Comparator<? super NoteBytes> comparator) {
        return m_stream.sorted(comparator);
    }

    @Override
    public Object[] toArray() {
        return m_stream.toArray();
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return m_stream.toArray(generator);
    }

}
