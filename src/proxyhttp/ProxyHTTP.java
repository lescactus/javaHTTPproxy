package proxyhttp;

import java.io.*;
import java.net.*;
import java.util.Properties;

/**
 *
 * @author  alexandre maldémé
 *          benoit mouton
 */

// Classe serveur Proxy.
public class ProxyHTTP /*extends Thread */
{
    static int clientConnectedToProxy = 0;
    static int numberProxy_max;   
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        int localport;
        int debugLevel;                 // Verbosity level of debug messages:
                                        // 0 = No message. 
                                        // 1 = Verbose (default).
        ServerSocket server = null;

        try 
        {
            // Number of parameters is incorrect.
            if(args.length != 2)
                throw new IllegalArgumentException("Incorrect number of parameters");
            
            // Get the listening port.
            localport = Integer.parseInt(args[0]);
            
            // Get the debug level.
            debugLevel = Integer.parseInt(args[1]);
     
            try
            {
                // Create a listening TCP socket on the local port given in parameters.
                server = new ServerSocket(localport); 
        
                // Get the maximal number of clients allowed to connect to the server.
                getMaxClientConnectionProxy();
                
                System.out.println("Server is running on port " + localport);
                System.out.println("Debug verbosity level: " + debugLevel);
                System.out.println("Max number of connections to the proxy = " + numberProxy_max);
                System.out.println("\n\n");
            }
            catch(IOException ex)
            {
                System.err.println(ex);
            }
            while(true)
            {
                try
                { 
                    // If number of connected clients to the proxy is bigger than the maximal number of allowed clients.
                    if( !(clientConnectedToProxy > numberProxy_max) )
                    {
                        // Run a new Thread.
                        new ProxyThread(server.accept(), debugLevel).start();
                        
                        // Incrementing the number of connected clients
                        clientConnectedToProxy++;
                        if(debugLevel > 0)
                            System.out.println("New connection to the proxy. " +  clientConnectedToProxy + 
                                " client(s) connected to the proxy.");
                    }
                    else
                    {
                        System.err.println("The connection to the proxy from " + server.getInetAddress().getHostAddress() +
                            " cannot be established due to numberProxy_max reached."); 
                    }
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                }
            }   
        }
        catch(Exception ex)
        {
            System.err.println(ex);
            System.err.println("USAGE: java ProxyHTTP <port number> <debug level>");
            System.err.println("  <port number>   The port this service listens on.");
            System.err.println("  <debug level>   The level of verbosity of the proxy.");
            System.err.println("                  0 = No message.");
            System.err.println("                  1 = Verbose.");
        }
    }
    
    // Get the number of client allowed to be connected to the Proxy.
    public static void getMaxClientConnectionProxy()
    {
        Properties prop = new Properties();
        try
        { 
            InputStream input = new FileInputStream("config.properties");
            prop.load(input);
            numberProxy_max = Integer.parseInt(prop.getProperty("nbr_connection_proxy"));
            return;
        }
        catch(IOException ex) 
        {
            ex.printStackTrace();
        }
        // Default.
        numberProxy_max = 128;
    }
    
}
