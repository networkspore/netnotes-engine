package io.netnotes.engine.utils.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.utils.FreeMemory;
import io.netnotes.engine.utils.Ping;


public class LinuxShellHelpers{
    
    public static CompletableFuture<Ping> pingIP(String ip, int pingTimes, ExecutorService execService){

        return CompletableFuture.supplyAsync(()->{

            String[] cmd = {"bash", "-c", "ping -c 3 " + ip};
            Ping ping = new Ping(false, "", -1);

            String line;
            try{
            Process proc = Runtime.getRuntime().exec(cmd);
        
            try(
                BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            ){

                while ((line = wmicStderr.readLine()) != null) {
                    ping.setError(ping.getError() + line + " ");
                }

                if(!ping.getError().equals(""))
                {
                    return ping;
                }
                
            }catch(IOException e){
                ping.setError(e.toString());
                return ping;
            }
            
            try(
        
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            ){

            
                String timeString = "time=";

                while (true) {
                
                    line = stdInput.readLine();
            

                    if(line == null){
                        break;
                    }

                    int indexOftimeString = line.indexOf(timeString);

                    if (line.indexOf("service not known") > -1) {
                        ping.setAvailable(false);
                        ping.setError("Unreachable");
                        break;
                    }

                    if (line.indexOf("timed out") > -1) {

                        ping.setAvailable(false);
                        ping.setError( "Timed out");
                        break;
                    }

                    if (indexOftimeString > -1) {
                        int lengthOftime = timeString.length();

                        int indexOfms = line.indexOf("ms");

                        ping.setAvailable(true);

                        String time = line.substring(indexOftimeString + lengthOftime, indexOfms).trim();
    

                        ping.setAvgPing(Double.parseDouble(time));
                    
                    }

                    String avgString = "min/avg/max/mdev = ";
                    int indexOfAvgString = line.indexOf(avgString);

                    if (indexOfAvgString > -1) {
                        int lengthOfAvg = avgString.length();

                        String avgStr = line.substring(indexOfAvgString + lengthOfAvg);
                        int slashIndex = avgStr.indexOf("/");

                        avgStr = avgStr.substring(slashIndex+1, avgStr.indexOf("/",slashIndex + 1) ).trim();
                    
                        ping.setAvailable(true);
                        ping.setAvgPing(Double.parseDouble(avgStr));
                
                    }

                }
            }catch(IOException e){
                ping.setError(e.getMessage());    
            }
        }catch(IOException e){
            throw new CompletionException("Executing cmd failed", e);
        }
        

            return ping;
       }, execService);
     
    }

    public CompletableFuture<Boolean> sendTermSig(String jarName, ExecutorService execService) {
        
        return CompletableFuture.supplyAsync(()->{
            String execString = "kill $(ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}')";

            String[] cmd = new String[]{ "bash", "-c", execString};
            try{
            Process proc = Runtime.getRuntime().exec(cmd);

            BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            //String wmicerr = null;


            BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            // String wmicInput = null;
            boolean gotInput = false;

            while ((wmicStdInput.readLine()) != null) {
            
            
                gotInput = true;
            }
           // String errStr = "";
            while (stdErr.readLine() != null) {

                
                gotInput = false;
            }

            proc.waitFor();

    
            if(gotInput){
                return true;
            }else{
                return false;
            }
            
        }catch(IOException e){
            throw new CompletionException("Failed to execute",e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Thread interrupted while waiting to complete",e);
        }
                
            
        }, execService);
    }

    public boolean sendTermSig(String jarName) {
        try {
          //  File logFile = new File("wmicTerminate-log.txt");
     
            //String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
            String execString = "kill $(ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}')";

            String[] cmd = new String[]{ "bash", "-c", execString};

            Process proc = Runtime.getRuntime().exec(cmd);

            BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            //String wmicerr = null;


            BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

           // String wmicInput = null;
            boolean gotInput = false;

            while ((wmicStdInput.readLine()) != null) {
            
            
                gotInput = true;
            }
            String errStr = "";
            while ((errStr = stdErr.readLine()) != null) {

                Files.writeString(new File("netnotes-log.txt").toPath(), "\nsig term err: " + errStr + "\n'" + execString + "'", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                gotInput = false;
            }

            proc.waitFor();

            if (gotInput) {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
        return false;
    }

     public static void open(File file) throws IOException {
      
        String path = file.getCanonicalPath().replace("\"", "\\\"");
        String[] cmd = { "sh", "-c", "xdg-open \"" + path + "\"" };
        
        Runtime.getRuntime().exec(cmd);
  
    }


    public static FreeMemory getFreeMemory() {
        try{ 
            String[] cmd = new String[]{ "sh", "-c",  "cat /proc/meminfo | awk '{print $1,$2}'"};

            Process proc = Runtime.getRuntime().exec(cmd);


            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        

            long swapTotal = -1;
            long swapFree = -1;
            long memFree = -1;
            long memAvailable = -1;
            long memTotal = -1;

            String s = null;

            String delimiter = ": ";
            int delimiterSize = delimiter.length();

            while ((s = stdInput.readLine()) != null) {
                
                int spaceIndex = s.indexOf(delimiter);
                
                
                String rowStr = s.substring(0, spaceIndex);
                long value = Long.parseLong(s.substring(spaceIndex + delimiterSize ));
                
                switch(rowStr){
                    case "SwapTotal":
                        swapTotal = value;
                    break;
                    case "SwapFree":
                        swapFree = value;
                    break;
                    case "MemTotal":
                        memTotal = value;
                    break;
                    case "MemFree":
                        memFree = value;
                    break;
                    case "MemAvailable":
                        memAvailable = value;
                    break;
                }

            }

            String errStr = stdErr.readLine();
            
            proc.waitFor();

            if(errStr == null){
                return new FreeMemory(swapTotal, swapFree, memFree, memAvailable, memTotal);
            }
        }catch(IOException | InterruptedException e){
            try {
                Files.writeString(new File("netnotes-log.txt").toPath(), "\nUtils getFreeMemory:" + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e1) {

            }
        }

        return null;
    }

    public static String[] getShellCmd(String cmd){
        
        return new String[]{"bash", "-c", cmd};
    }


    public static boolean sendKillSig(String jarName) {
        try {
          //  File logFile = new File("wmicTerminate-log.txt");
     
            //String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
            String execString = "kill $(ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}')";

            String[] cmd = new String[]{ "bash", "-c", execString};

            Process wmicProc = Runtime.getRuntime().exec(cmd);

            BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(wmicProc.getErrorStream()));
            //String wmicerr = null;


         

            BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(wmicProc.getInputStream()));

           // String wmicInput = null;
            boolean gotInput = false;

            while ((wmicStdInput.readLine()) != null) {
            
            
                gotInput = true;
            }

       
            while ((wmicStderr.readLine()) != null) {

               // Files.writeString(logFile.toPath(), "\nwmic err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return false;
            }

            wmicProc.waitFor();

            if (gotInput) {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
        return false;
    }


    public static String[] findPIDs(String jarName){
        try {
            //  File logFile = new File("wmicTerminate-log.txt");
              //Get-Process | Where {$_.ProcessName -Like "SearchIn*"}
           //   String[] wmicCmd = {"powershell", "Get-Process", "|", "Where", "{$_.ProcessName", "-Like", "'*" +  jarname+ "*'}"};
            String execString = "ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}'";
            String[] cmd = new String[]{ "bash", "-c", execString};
            Process proc = Runtime.getRuntime().exec(cmd);
  
              BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
              //String pserr = null;
  
  
              ArrayList<String> pids = new ArrayList<>();
  
              BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
  
              String input = null;
                 
              while ((input = stdInput.readLine()) != null) {
                input.trim();
                pids.add(input);
                  
              }
              
              String  pserr = null;
              while ((pserr = stderr.readLine()) != null) {
                  try {
                      Files.writeString(new File("netnotes-log.txt").toPath(), "\nutils: " + execString + ": " + pserr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  } catch (IOException e1) {
                  
                  }
                 // Files.writeString(logFile.toPath(), "\nps err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  
              }
  
              proc.waitFor();
              if( pids.size() > 0){
             
                  String[] pidArray = new String[pids.size()];
  
                  pidArray =  pids.toArray(pidArray);
                  
                  return pidArray;
              }else{
                  return null;
              }
              
  
          } catch (Exception e) {
                try {
                  Files.writeString(new File("netnotes-log.txt").toPath(), "\npsPID: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
              } catch (IOException e1) {
               
              }
               
              return null;
          }

    }
    /*
    public static boolean sendTermSig(String pid){
        try {
            //  File logFile = new File("wmicTerminate-log.txt");
       
              //String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
              String[] cmd = new String[]{ "bash", "-c",  "kill -SIGTERM " + pid};

              Process wmicProc = Runtime.getRuntime().exec(cmd);
  
              BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(wmicProc.getErrorStream()));
  
              BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(wmicProc.getInputStream()));
  
             // String wmicInput = null;
              boolean gotInput = false;
  
              while ((wmicStdInput.readLine()) != null) {
              
              
                  gotInput = true;
              }
  
              while ((wmicStderr.readLine()) != null) {
  
                 // Files.writeString(logFile.toPath(), "\nwmic err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  return false;
              }
  
              wmicProc.waitFor();
  
              if (gotInput) {
                  return true;
              }
  
          } catch (Exception e) {
              return false;
          }
          return false;
    }*/
}
