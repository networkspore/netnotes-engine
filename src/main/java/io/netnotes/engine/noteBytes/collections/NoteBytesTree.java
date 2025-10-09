package io.netnotes.engine.noteBytes.collections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesNode;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteBytesTree{
    
    private NoteBytesNode m_root = null;

    private int m_size = 0;
    
    public NoteBytesTree() {
        this(new byte[0]);
    }

    public NoteBytesTree(byte[] bytes) {
        deserialize(bytes);
    }

    public void insert(NoteBytes data) throws InterruptedException {
        m_root = insertRec(m_root, data);
        m_size++;
    }

    private NoteBytesNode insertRec(NoteBytesNode root, NoteBytes data) {
        if (root == null) {
            return new NoteBytesNode(data);
        }
        int comparison = compareNoteBytes(data, root.getData());
        if (comparison < 0) {
            root.setLeft(insertRec(root.getLeft(), data));
        } else if (comparison > 0) {
            root.setRight(insertRec(root.getRight(), data));
        }
        return root;
    }
    private int compareNoteBytes(NoteBytes a, NoteBytes b) {
        byte[] bytesA = a.get();
        byte[] bytesB = b.get();
        
        int minLength = Math.min(bytesA.length, bytesB.length);
        
        for (int i = 0; i < minLength; i++) {
            int comparison = Byte.compare(bytesA[i], bytesB[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        
        return Integer.compare(bytesA.length, bytesB.length);
    }
    public boolean contains(NoteBytes data) throws InterruptedException {
     
        return containsRec(m_root, data);
        
    }
    private boolean containsRec(NoteBytesNode root, NoteBytes data) {
        if (root == null) {
            return false;
        }
        int comparison = compareNoteBytes(data, root.getData());
        if (comparison == 0) {
            return true;
        }
        if (comparison < 0) {
            return containsRec(root.getLeft(), data);
        }
        return containsRec(root.getRight(), data);
    }

    public void remove(NoteBytes data) throws InterruptedException {
        m_root = removeRec(m_root, data); 
    }

    private NoteBytesNode removeRec(NoteBytesNode root, NoteBytes data) {
        if (root == null) {
            return null;
        }
        int comparison = compareNoteBytes(data, root.getData());
        if (comparison < 0) {
            root.setLeft(removeRec(root.getLeft(), data));
        } else if (comparison > 0) {
            root.setRight(removeRec(root.getRight(), data));
        } else {
            m_size--;
            
            if (root.getLeft() == null) {
                return root.getRight();
            } else if (root.getRight() == null) {
                return root.getLeft();
            }
            root.setData(minValue(root.getRight()));
            root.setRight(removeRec(root.getRight(), root.getData()));
        }
        return root;
    }
    private NoteBytes minValue(NoteBytesNode root) {
        NoteBytes minv = root.getData();
        while (root.getLeft() != null) {
            minv = root.getLeft().getData();
            root = root.getLeft();
        }
        return minv;
    }
    public List<NoteBytes> inOrderTraversal() throws InterruptedException {
        List<NoteBytes> result = new ArrayList<>();
      
        inOrderRec(m_root, result);
       
        return result;
    }
    private void inOrderRec(NoteBytesNode root, List<NoteBytes> result) {
        if (root != null) {
            inOrderRec(root.getLeft(), result);
            result.add(root.getData());
            inOrderRec(root.getRight(), result);
        }
    }


    public void setRoot(NoteBytesNode root) {
        m_root = root;
    }

    public NoteBytesNode getRoot() {
        return m_root;
    }

  
    public byte[] serialize() {
        if (m_root == null) {
            return new byte[0];
        }
        
        try (UnsynchronizedByteArrayOutputStream outputStream = new UnsynchronizedByteArrayOutputStream()) {
            serializeNode(m_root, outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }


    private void serializeNode(NoteBytesNode node, UnsynchronizedByteArrayOutputStream outputStream) throws IOException {
        if (node == null) {
            // Write null marker (0)
            outputStream.write(0);
            return;
        }
        
        // Write node marker (1)
        outputStream.write(1);
        
        // Write node data
        NoteBytes data = node.getData();
        byte[] dataBytes = data.get();
        byte type = data.getType();
        
        // Write type
        outputStream.write(type);
        
        // Write length
        byte[] lengthBytes = ByteDecoding.intToBytesBigEndian(dataBytes.length);
        outputStream.write(lengthBytes);
        
        // Write data content
        outputStream.write(dataBytes);
        
        // Recursively serialize left and right nodes
        serializeNode(node.getLeft(), outputStream);
        serializeNode(node.getRight(), outputStream);
    }

    private void deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        
        int offset = 0;
        while (offset < bytes.length) {
            // Read node data
            NoteBytes nodeData = NoteBytes.readNote(bytes, offset);
            offset += 5 + nodeData.byteLength(); // 1 byte type + 4 bytes length + content
            
            // Read left child flag
            boolean hasLeft = ByteDecoding.bytesToBoolean(bytes, offset);
            offset += 1;
            
            // Read right child flag  
            boolean hasRight = ByteDecoding.bytesToBoolean(bytes, offset);
            offset += 1;
            
            // Create current node
            NoteBytesNode currentNode = new NoteBytesNode(nodeData);
            
            if (m_root == null) {
                m_root = currentNode;
            }
            
            // Recursively deserialize left subtree if exists
            if (hasLeft) {
                NoteBytes leftData = NoteBytes.readNote(bytes, offset);
                offset += 5 + leftData.byteLength();
                currentNode.setLeft(new NoteBytesNode(leftData));
            }
            
            // Recursively deserialize right subtree if exists
            if (hasRight) {
                NoteBytes rightData = NoteBytes.readNote(bytes, offset); 
                offset += 5 + rightData.byteLength();
                currentNode.setRight(new NoteBytesNode(rightData));
            }
        }
    }


    public int size() {
        return m_size;
    }

    public boolean isEmpty() {
        return m_size == 0;
    }


    public void clear() {
     
        m_root = null;
        m_size = 0;
   
    }
}