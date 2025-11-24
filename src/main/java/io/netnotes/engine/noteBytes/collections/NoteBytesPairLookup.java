package io.netnotes.engine.noteBytes.collections;

import io.netnotes.engine.noteBytes.NoteBytes;

public class NoteBytesPairLookup {
    private NoteBytesPair[] array;

    public NoteBytesPairLookup(NoteBytesPair... array){
        this.array = array;
    }

    public NoteBytesPair[] getNoteBytesPairArray(){
        return array;
    }

    public NoteBytes lookup(NoteBytes key) {
    int low = 0;
    int high = array.length - 1;

    while (low <= high) {
        int mid = (low + high) >>> 1;
        NoteBytesPair entry = array[mid];

        int cmp = key.compare(entry.getKey()); //compareBytes(key, entry.key.bytes());
        if (cmp == 0) return entry.getValue();

        if (cmp < 0) {
            high = mid - 1;
        } else {
            low = mid + 1;
        }
    }

    return null;
}
}
