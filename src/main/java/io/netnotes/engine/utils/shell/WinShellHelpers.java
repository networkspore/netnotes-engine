package io.netnotes.engine.utils.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

public class WinShellHelpers {
   
  

    public static String[] findPIDs(String jarName) {
        try {

            String[] cmd = {
                "powershell",
                "-Command",
                "Get-WmiObject Win32_Process | Where-Object { $_.CommandLine -Like '*" + jarName + "*' } | Select-Object ProcessId"
            };

            Process psProc = Runtime.getRuntime().exec(cmd);

            BufferedReader psStderr = new BufferedReader(new InputStreamReader(psProc.getErrorStream()));
            //String pserr = null;


            ArrayList<String> pids = new ArrayList<>();

            BufferedReader psStdInput = new BufferedReader(new InputStreamReader(psProc.getInputStream()));

            String psInput = null;
           // boolean gotInput = false;
            //   int pid = -1;
               
            while ((psInput = psStdInput.readLine()) != null) {
              //  
              //  gotInput = true;
                psInput.trim();
                if(!psInput.equals("") && !psInput.startsWith("ProcessId") && !psInput.startsWith("---------")){
                    
                    pids.add(psInput);
                }
            }
            
            String  pserr = null;
            while ((pserr = psStderr.readLine()) != null) {
                try {
                    Files.writeString(new File("netnotes-log.txt").toPath(), "\npsPID err: " + pserr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {
                
                }
               // Files.writeString(logFile.toPath(), "\nps err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
            }

            psProc.waitFor();
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

     public static void psStopProcess(String pid){
        try {
            String[] cmd = {
                "powershell",
                "-Command",
                "Stop-Process -Id " + pid
            };
            Process psProc = Runtime.getRuntime().exec(cmd);
            psProc.waitFor();
        } catch (Exception e) {
            
        }
   
    }

    public static void cmdTaskKill(String pid) throws IOException, InterruptedException{
        
        String[] taskKillCmd = {
            "cmd", "/c", "taskkill", "/PID " + pid
        };
        Process psProc = Runtime.getRuntime().exec(taskKillCmd);
        psProc.waitFor();
      
    }


    public static boolean wmicTerminate(String jarName) {
        try {
          //  File logFile = new File("wmicTerminate-log.txt");
     
            String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
            Process wmicProc = Runtime.getRuntime().exec(wmicCmd);

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


    public static void open(File file) throws IOException {
        if (file != null && file.exists()) {
            String path = file.getCanonicalPath();
            String[] cmd = { "cmd", "/c", "start", "\"\"", "\"" + path + "\"" };
            Runtime.getRuntime().exec(cmd);
        }
    }

 
    public static boolean sendKillSig(String jarName) {
        return wmicTerminate(jarName);
    }
}
