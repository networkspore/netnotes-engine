
package io.netnotes.engine.noteBytes.collections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteBytesArrayList implements List<NoteBytes>{
    private ArrayList<NoteBytes> m_list = null;


    public NoteBytesArrayList(byte[] bytes) throws IOException{
        init(bytes);
    }

    public NoteBytesArrayList(){
       m_list = new ArrayList<>();
    }

    public NoteBytesArrayList(NoteBytes noteBytes) throws IOException{
        init(noteBytes.get());
    }


    public NoteBytesArrayList(NoteBytesReader reader, int length) throws IOException{
        init(reader, length);
    }

    public NoteBytesArrayList(NoteBytesReader reader) throws IOException{
        init(reader);
    }


    public static ArrayList<NoteBytes> getList(NoteBytesReader reader) throws IOException{
         // Parse plugin entries
 
        ArrayList<NoteBytes> list = new ArrayList<>();
        NoteBytes value = reader.nextNoteBytes();

        while (value == null) {
          
            list.add(value);
            
            value = reader.nextNoteBytes();
        }
        return list;
    }

    public static ArrayList<NoteBytes> getList(NoteBytesReader reader, int length) throws IOException{
         // Parse plugin entries
        int bytesRemaining = length;
        ArrayList<NoteBytes> list = new ArrayList<>();
        while (bytesRemaining > 0) {
            NoteBytes value = reader.nextNoteBytes();
            
            if (value == null) {
                throw new IOException("Unexpected end of stream");
            }
            
            list.add(value);
            
            bytesRemaining -= value.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        }

        return list;
    }

    public static  ArrayList<NoteBytes> getList(byte[] bytes) throws IOException{
        int length = bytes.length;
        ArrayList<NoteBytes> list = new ArrayList<>();
        if(length > 0){
            int offset = 0;
            while(offset < length) {
                NoteBytes value = NoteBytes.readNote(bytes, offset);
                offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + value.byteLength()); // 1 byte type + 4 bytes length
                list.add(value);
            }
           
        }
        return list;
    }

    public void init(NoteBytesReader reader) throws IOException{
        if(m_list != null){
            clear();
        }
        m_list = getList(reader);
    }
    public void init(NoteBytesReader reader, int length) throws IOException{
        if(m_list != null){
            clear();
        }
        m_list = getList(reader, length);
    }
    public void init(byte[] bytes) throws IOException{
        if(m_list != null){
            clear();
        }
        m_list = getList(bytes);
    }

    public int rawByteLength(){
        int length = 0; 
        for(NoteBytes value : m_list) {
            length += value.byteLength();
        }
        return length;
    }

    public int byteLength_w_MetaData(){
        int length = 0; 
        for(NoteBytes value : m_list) {
            length += value.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        }
        return length;
    }


    public NoteBytes getNoteBytes() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(NoteBytes value : m_list) {
            offset = NoteBytes.writeNote(value, bytes, offset);
        }
        return new NoteBytes(bytes, ByteDecoding.NOTE_BYTES_ARRAY);
    }

    public NoteBytesArray getNoteBytesArray() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(NoteBytes value : m_list) {
            offset = NoteBytes.writeNote(value, bytes, offset);
        }
        return new NoteBytesArray(bytes);
    }

    @Override
    public boolean add(NoteBytes arg0) {
        return m_list.add(arg0);
    }

    @Override
    public void add(int arg0, NoteBytes arg1) {
        m_list.add(arg0, arg1);
    }

    @Override
    public boolean addAll(Collection<? extends NoteBytes> c) {
        return  m_list.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends NoteBytes> c) {
       return  m_list.addAll(index, c);
    }

    @Override
    public void clear() {
        m_list.clear();
    }

    @Override
    public boolean contains(Object o) {
        return m_list.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return m_list.containsAll(c);
    }

    @Override
    public NoteBytes get(int index) {
        return m_list.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return m_list.indexOf(o);
    }

    @Override
    public boolean isEmpty() {
        return m_list.isEmpty();
    }

    @Override
    public Iterator<NoteBytes> iterator() {
        return m_list.iterator();
    }

    @Override
    public int lastIndexOf(Object o) {
        return m_list.lastIndexOf(o);
    }

    @Override
    public ListIterator<NoteBytes> listIterator() {
       return m_list.listIterator();
    }

    @Override
    public ListIterator<NoteBytes> listIterator(int index) {
        return m_list.listIterator(index);
    }

    @Override
    public boolean remove(Object o) {
        return m_list.remove(o);
    }

    @Override
    public NoteBytes remove(int index) {
        return m_list.remove(index);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return m_list.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return m_list.retainAll(c);
    }

    @Override
    public NoteBytes set(int arg0, NoteBytes arg1) {
        return m_list.set(arg0, arg1);
    }

    @Override
    public int size() {
        return m_list.size();
    }

    @Override
    public List<NoteBytes> subList(int fromIndex, int toIndex) {
        return m_list.subList(fromIndex, toIndex);
    }

    @Override
    public Object[] toArray() {
        return m_list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] arg0) {
        return m_list.toArray(arg0);
    }


    public NoteBytes[] toArray(NoteBytes[] arg0) {
        return m_list.toArray(arg0);
    }

}
