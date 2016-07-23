package proxyhttp;


import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 *
 * @author  alexandre maldémé
 *          benoit mouton
 */

public class ProxyThread extends Thread
{
    // Socket for the client.
    private Socket client = null;
    
    // Verbosity level of debug messages.
    int debugLevel;
    
    // Constructor.
    public ProxyThread(Socket socket, int debug) 
    {
        // Call the default constructor of Thread().
        super("ProxyThread");
        this.client = socket;
        this.debugLevel = debug;
    }
    
    
    public void run()
    {
        /*
            1) Get the input HTTP request of the client.
            2) Send the HTTP request to the remote web server.
            3) Get the response from the web server.
            4) Send the server's response to the client.
        */
        
        try
        { 
            // Create the output stream on the client's socket.
            OutputStream client_out = client.getOutputStream();
            PrintWriter client_pout = new PrintWriter(new OutputStreamWriter(client_out), true);
            
            // Create the stream reader on the client's socket.
            BufferedReader client_in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            

            String inputLine;               // HTTP request.
            String UserAgent = "";          // HTTP User-Agent's request.
            String hostName = "";           // Remote hostname.
            String urlAsked = "";           // URL of the webpage asked by client.
            int count = 0;                  // Counter used to browse HTTP header.
            int hostPort = 80;              // Connection port (default: 80 for HTTP).
            
            URL url_ = null;
            
            
            /* Get proxy filtering informations from the file config.properties. */
            Properties prop = new Properties();
            InputStream input = new FileInputStream("config.properties");
            String[] allowedClients;                // Clients allowed to reach blacklisted websites.
            String[] hostBlacklisted;               // Blacklisted hosts and websites (domain names).
            String[] forbiddenExtensions;           // Filenames extension undownloadable.
            String[] hostPortBlacklisted;           // Blacklisted host ports
            String ligne;

            // Load config file.
            prop.load(input);


            // Get blacklisted websites in one single line CSV.
            ligne = prop.getProperty("blacklisted_websites");
            // Split blacklisted websites in a String[].
            hostBlacklisted = ligne.split(";");

            // Get allowed clients from config file.
            ligne = prop.getProperty("allowed_clients");
            //Split allowed hosts in a String[].
            allowedClients = ligne.split(";");

            // Get forbidden files extensions.
            ligne = prop.getProperty("forbidden_extensions");
            forbiddenExtensions = ligne.split(";");

            // Get blacklisted hostports.
            ligne = prop.getProperty("blacklisted_port");
            hostPortBlacklisted = ligne.split(";");

            // Get the number of client allowed to be connected to the Internet.
            ligne = prop.getProperty("nbr_connection_internet");


            if(debugLevel > 0)
                System.out.println("Addresses allowed to reach blacklisted content : \n"
                    +   Arrays.toString(allowedClients) + "\n");

            System.out.println("Connection established with: " + client.getInetAddress().getHostAddress());
            
            /** Start recepting HTTP request of the client. **/
            
            if(debugLevel > 0)
                System.out.println("Start recepting HTTP request of the client.");
            
            // The number of remote port is present in the HTTP header only if it is different of those by default (80).
            // See RFC-2616-14.23: "Hypertext Transfer Protocol -- HTTP/1.1"
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
            // "A "host" without any trailing port information implies the default port for the service requested (e.g., "80" for an HTTP URL)."
            
            // Read the stream of the request line by line.
            while ((inputLine = client_in.readLine()) != null)
            {
                if(debugLevel > 0)
                    System.out.println("* * * " + inputLine);
                
                // Get the User-Agent.
                if ( inputLine.startsWith("User-Agent") )
                    UserAgent = inputLine;
                
                try 
                {
                    // Get the first line of the HTTP request (ex: GET http://alexasr.tk/ HTTP/1.1)
                    StringTokenizer tok = new StringTokenizer(inputLine);
                    tok.nextToken();
                } 
                catch (Exception e) 
                {
                    break;
                }
                // Get the URL of the first line in the HTTP request. (ex: http://kernel.org/)
                if (count == 0) 
                {          
                    String[] tokens = inputLine.split(" ");
                    urlAsked = tokens[1];  
                    
                    // If URL contains ':[nombre]', get the port number following.
                    Matcher m = Pattern.compile(":[0-9]+").matcher(urlAsked);               
                    if(m.find())
                    {
                        // '\\D' means numerical string.
                        hostPort = Integer.parseInt(urlAsked.substring(urlAsked.lastIndexOf(":") + 1).replaceAll("\\D+", ""));
                    }
                }
                // Get the hostname of the remote web server. (ex: kernel.org)
                if( inputLine.startsWith("Host"))
                {
                    String[] tokens = inputLine.split(" ");
                    hostName = tokens[1];     
                }
                count++;
            }
            
            
            if(debugLevel > 0)
            {
                System.out.println("End recepting HTTP request of the client.");
                // Print the URL, the hostname and the hostport.
                System.out.println(client.getInetAddress().getHostAddress() + ": requête pour : " + urlAsked);
                System.out.println(client.getInetAddress().getHostAddress() + ": hôte distant : " + hostName );
                System.out.println(client.getInetAddress().getHostAddress() + ": port distant : " + hostPort );
            }
            
            
            /** End recepting HTTP request of the client. **/
 
            
            try
            {
                
                /** 
                 * Blacklist de l'hôte:
                 *  On parcourt le champ 'blacklist' dans le fichier config.properties au format CSV (séparé par des ';').
                 *      ex: www.foo.com;bar.fr;www.exemple.org ...
                 *  On split la chaîne pour récupérer les noms d'hôtes (www.foo.com
                 *                                                      bar.fr
                 *                                                      www.exemple.org ...)
                 *  Si le nom d'hôte de la requête correspond à l'un de ceux backlistés, alors erreur.
                 * 
                 * 
                 * On fait pareil avant pour le port.
                 * 
                 */
                
                // If hostport is blacklisted.
                if(checkBlacklistHostPort(hostPortBlacklisted, hostPort))
                {
                    System.err.println("/!\\ Port " + hostPort + " is blacklisted. Cancelling request.");
                    client_pout.println("/!\\ Port " + hostPort + " is blacklisted. Cancelling request.");
                    client.close();
                    ProxyHTTP.clientConnectedToProxy--;
                    System.out.println("End of connection to the proxy from " + client.getInetAddress().getHostAddress());
                    System.out.println("Number of connected client(s) : " + ProxyHTTP.clientConnectedToProxy);
                    return;
                }

                // If hostName isn't blacklisted.
                if(!checkBlacklistHostnames(hostBlacklisted, hostName))
                {
                    url_ = new URL(urlAsked);
                    if(debugLevel > 0)
                        System.out.println("Connection from " + client.getInetAddress().getHostAddress() + " for " + hostName + " accepted.\n");
                }
                else // Hostname is blacklisted.
                {
                    // If client is allowed to access blacklisted hostnames.
                    if(checkAllowedHost(allowedClients, client.getInetAddress().getHostAddress()))
                    {
                        url_ = new URL(urlAsked);
                        if(debugLevel > 0)
                            System.out.println("Connection from " + client.getInetAddress().getHostAddress() + " accepted.");
                    } 

                    else
                    {
                        System.err.println("/!\\ " + hostName + " is blacklisted. Cancelling request.");
                        client_pout.println("/!\\ " + hostName + " is blacklisted. Cancelling request.");
                        client.close();
                        ProxyHTTP.clientConnectedToProxy--;
                        System.out.println("End of connection to the proxy from " + client.getInetAddress().getHostAddress());
                        System.out.println("Number of connected client(s) : " + ProxyHTTP.clientConnectedToProxy);
                        return;
                    }
                }
                
                
                
                /** Start sending request to remote server + receive response of the server + sending response to the client. **/
                
                if(url_ != null)
                {
                    // If file extension is blacklisted.
                    if(checkFileExtension(forbiddenExtensions, urlAsked))
                    {
                        if(debugLevel > 0)
                            System.err.println("Downloading " + Arrays.toString(forbiddenExtensions) + " is forbidden.");
                    }
                    else
                    {

                        if(debugLevel > 0)
                            System.out.println("Sending a request for URL: " + urlAsked);

                        // Connection to the website asked by the client.
                        HttpURLConnection connection = (HttpURLConnection)url_.openConnection();
                        HttpURLConnection httpConn = (HttpURLConnection)connection;
                        connection.setRequestMethod("GET");
                        connection.setDoOutput(false);
                        httpConn.setRequestProperty("Accept","*/*");

                        // Some servers won't respond if User-Agent isn't informed.
                        if ( ! UserAgent.isEmpty())
                        {    String[] a = UserAgent.split(":", 2);
                            httpConn.setRequestProperty(a[0], a[1]);
                        }


                        if(debugLevel > 0)
                        {
                            // Print HTTP header properties.
                            for (String header : httpConn.getRequestProperties().keySet()) 
                            {
                                if (header != null) 
                                {
                                    for (String value : httpConn.getRequestProperties().get(header)) 
                                    {
                                        System.out.println("\t" + header + ":" + value);
                                    }
                                }
                            }

                            System.out.println("\tType is: " + connection.getContentType());
                            System.out.println("\tContent length: " + connection.getContentLength());
                            System.out.println("\tAllowed user interaction: " + connection.getAllowUserInteraction());
                            System.out.println("\tContent encoding: " + connection.getContentEncoding());
                            System.out.println("\tContent type: " + connection.getContentType());
                            System.out.println("\n");
                        }

                        try
                        {
                            // Copy response stream (input) of the remote server to the output stream of the client.
                            // This method create an intern buffer to not use "BufferedInputStream"
                            org.apache.commons.io.IOUtils.copy(connection.getInputStream(), client_out);
                        }
                        catch(FileNotFoundException e) // In case of unavailable file on the server (ex: 404 error).
                        {
                            System.err.println("FILENOTFOUNDEXCEPTION: " + e);
                            e.printStackTrace();
                        }
                    }
                }
                /** End sending request to remote server + receive response of the server + sending response to the client. **/
     
            }
            catch (Exception e) 
            {
                System.err.println("Exception error: " + e);
                e.printStackTrace();
            }
            
            
            // Close ressources
            if (client_in != null) 
            {
                client_in.close();
            }
            if (client_out != null) 
            {
                client_out.close();
            }
            if (client_pout != null) 
            {
                client_pout.close();
            }
            if (client != null) 
            {
                ProxyHTTP.clientConnectedToProxy--;
                System.out.println("End of connection to the proxy from " + client.getInetAddress().getHostAddress());
                System.out.println("Number of connected client(s) : " + ProxyHTTP.clientConnectedToProxy);
                client.close();
            }
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }
    
    // Return true if hostname is blacklisted.
    private boolean checkBlacklistHostnames(String[] blacklist, String hostname)
    {
        for(String domain : blacklist)
        {
            // True if equals, false if not.
            if(hostname.equals(domain))
                return true;
        }
        return false;
    }


    // Return true if host port is blacklisted.
    private boolean checkBlacklistHostPort(String[] blacklist, int hostport)
    {
        for(String port : blacklist)
        {
            // True if equals, false if not.          
            if(Integer.toString(hostport).equals(port))
                return true;
        }
        return false;
    }


    // Return true if @IP is an allowed client.
    private boolean checkAllowedHost(String[] allowed, String host)
    {
        for(String address : allowed)
        {
            if(host.equals(address))
            {
                System.out.println(address + " autorisé à accéder au site.");
                return true;
            }
        }
        return false;
    }

    // Return true if pattern matches.
    private boolean checkFileExtension(String[] pattern, String req)
    {
        for(String match : pattern)
        {
            Matcher m = Pattern.compile(match).matcher(req);

            if(m.find())
            {
				System.err.println("/!\\ " + match + " file detected.");
                return true;
            }
        }
        return false;
    }
}
