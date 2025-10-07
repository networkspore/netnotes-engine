package io.netnotes.engine.utils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.apache.commons.io.FilenameUtils;

public class FileRoot {
    
    private long m_rootsTimeStamp = 0;
    private File[] m_roots = null;

    public boolean findPathPrefixInRoots(String filePathString){
        File roots[] = getRoots();
        return findPathPrefixInRoots(roots, filePathString);
    }

    public File[] getRoots(){
    
        if((System.currentTimeMillis() - m_rootsTimeStamp) > 1000){
            m_roots = File.listRoots();
            m_rootsTimeStamp = System.currentTimeMillis();
            return m_roots;
        }else{
            return m_roots;
        }
    }


    public boolean findPathPrefixInRoots(File roots[], String filePathString){
        

        if(roots != null && roots.length > 0 && filePathString != null && filePathString.length() > 0){

            String appDirPrefix = FilenameUtils.getPrefix(filePathString);

            for(int i = 0; i < roots.length; i++){
                String rootString = roots[i].getAbsolutePath();

                if(rootString.startsWith(appDirPrefix)){
                    return true;
                }
            }
        }

        return false;
    }

    public CompletableFuture<Optional<String>> checkDriveForPath(File file, ExecutorService execService) {

        return CompletableFuture.supplyAsync(()->{
      
            if(file == null){
                return Optional.empty();
            }

            try{
                String path = file.getCanonicalPath();
                if(findPathPrefixInRoots(path)){
                    return Optional.of(path);
                }else{
                    return Optional.empty();
                }
            }catch(IOException e){
                return Optional.empty();
            }
             
        }, execService);
    }

}
