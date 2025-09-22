package io.netnotes.engine.noteFile;

import java.io.File;
import java.util.List;

import io.netnotes.engine.noteFile.FileStreamUtils.BulkUpdateConfig;
import io.netnotes.engine.noteFile.FileStreamUtils.UpdateStrategy;

public class DiskSpaceValidation {
        private boolean m_isValid;
        private final int m_numberOfFiles;
        private final long m_totalFileSizes;
        private final long m_requiredSpace;
        private final long m_availableSpace;
        private final long m_bufferSpace;
        
        public DiskSpaceValidation(
            boolean isSpaceAvaialble,
            int numberOfFiles,
            long totalFileSizes,
            long requiredSpace,
            long availableSpace,
            long bufferSpace
        ) {
            m_isValid = isSpaceAvaialble;
            m_numberOfFiles = numberOfFiles;
            m_totalFileSizes = totalFileSizes;
            m_requiredSpace = requiredSpace;
            m_availableSpace = availableSpace;
            m_bufferSpace = bufferSpace;
        }
   
        public boolean isValid(){
            return m_isValid;
        }
        
        public int getNumberOfFiles() {
            return m_numberOfFiles;
        }
        public long getTotalFileSizes() {
            return m_totalFileSizes;
        }
        public long getRequiredSpace() {
            return m_requiredSpace;
        }
        public long getAvailableSpace() {
            return m_availableSpace;
        }
        public long getBufferSpace() {
            return m_bufferSpace;
        }

          public static int getIndexSmallerThan(long[] largestBatchSizes,long newFileSize){
        
        for(int i = 0; i<largestBatchSizes.length ; i++){
            long size = largestBatchSizes[i];
            if(size < newFileSize){
                return i;
            }
        }
        return -1;
    }

    public static void updateLargestBatchSizes(long[] largestBatchSizes, long newFileSize){
        long currentSize = newFileSize;
        int i = getIndexSmallerThan(largestBatchSizes, currentSize);
        while(i != -1){
            long oldSize = largestBatchSizes[i];

            largestBatchSizes[i] = currentSize;
            currentSize = oldSize;
            i = getIndexSmallerThan(largestBatchSizes, currentSize);
        }
        
    }

    public static long getLargestBatchSize(long[] largestBatchSizes){
        if(largestBatchSizes == null){
            return -1;
        }else{
            long size = 0;
            for(long fileSize : largestBatchSizes){
                size += fileSize;
            }
            return size;
        }
    }
 
    public static DiskSpaceValidation validateDiskSpace(BulkUpdateConfig config, List<File> fileList) throws Exception {
        long totalFileSizes = 0;
        long largestFileSize = 0;
        long[] largestBatchSizes = config.getStrategy() == UpdateStrategy.BATCHED ? new long[config.getBatchSize()] : null;

        for (File file : fileList) {
            long fileLength = file.length();
            largestFileSize = largestFileSize < fileLength ? fileLength : largestFileSize;
            if(config.getStrategy() == UpdateStrategy.BATCHED){
                updateLargestBatchSizes(largestBatchSizes, fileLength);
            }
        }
        

        File referenceDir = fileList.get(0).getParentFile();
        long availableSpace = referenceDir.getFreeSpace();
        long requiredSpace = fileList.isEmpty() ? 0 : calculateRequiredSpace(totalFileSizes, largestFileSize,largestBatchSizes, config);
        long bufferSpace = (long) (availableSpace * config.getDiskSpaceBuffer());
        
    
        return new DiskSpaceValidation(fileList.isEmpty()  ? true : bufferSpace >= requiredSpace , fileList.size(), totalFileSizes, requiredSpace, availableSpace, bufferSpace);
    }

    public static long calculateRequiredSpace(long totalFileSizes, long largestFileSize, long[] largestBatchSizes, BulkUpdateConfig config) {
        switch (config.getStrategy()) {
            case UpdateStrategy.SEQUENTIAL:
                return largestFileSize;
            case UpdateStrategy.BATCHED:
                return getLargestBatchSize(largestBatchSizes);
            case UpdateStrategy.PARALLEL:
                return totalFileSizes;
            default:
                return totalFileSizes;
        }
    }
}