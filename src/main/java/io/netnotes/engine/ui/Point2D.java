package io.netnotes.engine.ui;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class Point2D extends SpatialPoint<Point2D> {
    private final int x;
    private final int y;

    public Point2D(int x, int y){
        this.x = x;
        this.y = y;
    }

    public int getX(){
        return x;
    }

    public int getY(){
        return y;
    }

    public static Point2D fromNoteBytes(NoteBytes noteBytes){
        byte type = noteBytes.getType();
        if(type == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            NoteBytesObject nbo = noteBytes instanceof NoteBytesObject obj ? obj : noteBytes.getAsNoteBytesObject();
            NoteBytesPair xBytes = nbo.get(Keys.X);
            NoteBytesPair yBytes = nbo.get(Keys.Y);

            int x = xBytes != null ? xBytes.getAsInt() : 0;
            int y = yBytes != null ? yBytes.getAsInt() : 0;

            return new Point2D(x, y);
        } 

        throw new IllegalArgumentException("[Point2D.fromNoteBytes] expected NoteBytesObject got: " + type);
    }

    @Override
    public NoteBytesObject toNoteBytes() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y)
        });
    }

    @Override
    public Point2D subtract(Point2D point) {
        return new Point2D(this.x - point.x, this.y - point.y);
    }

    @Override
    public Point2D add(Point2D point) {
        return new Point2D(this.x + point.x, this.y + point.y);
    }
    
    
}
