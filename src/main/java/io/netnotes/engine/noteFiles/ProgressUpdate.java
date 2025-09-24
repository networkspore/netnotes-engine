package io.netnotes.engine.noteFiles;

public class ProgressUpdate {
        private final int completed;
        private final int total;
        private final String message;
        
        public ProgressUpdate(int completed, int total, String message) {
            this.completed = completed;
            this.total = total;
            this.message = message;
        }
        
        public int getCompleted() { return completed; }
        public int getTotal() { return total; }
        public String getMessage() { return message; }
        
        public double getPercentage() {
            return total > 0 ? (double) completed / total * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Progress: %d/%d (%.1f%%) - %s", completed, total, getPercentage(), message);
        }
    }