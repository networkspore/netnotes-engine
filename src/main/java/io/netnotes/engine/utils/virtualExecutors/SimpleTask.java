package io.netnotes.engine.utils.virtualExecutors;


public  class SimpleTask extends SerializedTask<Void> {
    final Runnable runnable;
    
    public SimpleTask(Runnable r) 
    {  
        super(null, null);
        runnable = r;
    }

    public Runnable getRunnable(){
        return runnable;
    }

    
    @Override
    public Void call() throws Exception {
        if(!isStarted()){
            this.isStarted = true;
            runnable.run();
            return null;
        }else{
            return null;
        }
    }
}
