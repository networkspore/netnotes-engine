package io.netnotes.engine.io.process;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.noteBytes.processing.NoteBytesWriter;

public class ChannelWriter{
        private final StreamChannel channel;
        private NoteBytesWriter writer = null;
        private final SerializedVirtualExecutor writeExec = new SerializedVirtualExecutor();
        private final CompletableFuture<NoteBytesWriter> writeFuture;
        
        public ChannelWriter(StreamChannel channel){
            this.channel = channel;
            NoteBytesWriter writer = new NoteBytesWriter(channel.getChannelStream());
           
            writeFuture = channel.getReadyFuture()
                .thenApply((futureFinished)->{
                    this.writer = writer;
                return writer;
            }); 
            
        }

        public CompletableFuture<NoteBytesWriter> getReadyWriter(){
            return writeFuture;
        }

        public void shutdown(){
            if(!writeExec.isShutdown()){
                writeExec.shutdown();
            }
            StreamUtils.safeClose(channel);
        }

        public StreamChannel getStreamChannel(){
            return channel;
        }

        public NoteBytesWriter getWriter(){
            return writer;
        }

        public SerializedVirtualExecutor getWriteExec(){
            return writeExec;
        }
        
    }