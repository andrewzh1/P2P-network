import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public class Node implements Runnable
{
    //Class constants.
    private final static int MAX_HOP_COUNT = 16;
    private final static int PORT = 5000;

    //Instance variables and constants.
    private final String homeDirectory;
    private List<String> neighbors;
    private HashMap<String, Integer> searches;
    private HashMap<String, String> previousSearches;
    private List<String> receivedReplies;
    private final ScheduledExecutorService scheduler;
    private long searchStartTime;

    //Initializes all instance variables.
    public Node(String homeDirectory)
    {
        this.homeDirectory = homeDirectory;
        this.neighbors = new ArrayList<>();
        this.searches = new HashMap<>();
        this.previousSearches = new HashMap<>();
        this.receivedReplies = new ArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.searchStartTime = System.currentTimeMillis();
    }

    @Override
    public void run()
    {
        new Thread(this::startServer).start();
    }

    public void addNeighbor(String neighbor)
    {
        neighbors.add(neighbor);
    }

    //The node joins the network as a neighbor of the "host" node.
    public void joinNetwork(String host)
    {
        try
        {
            Socket socket = new Socket(host, PORT);
            String neighbor = socket.getInetAddress().getHostAddress();
            addNeighbor(neighbor);
            System.out.println("Server neighbors: " + neighbors);
            socket.close();
        }
        catch(IOException e)
        {
            System.err.println("Error joining network! " + e);
        }
    }

    //Client listens for commands issued by the user until program termination.
    public void clientLoop()
    {
        Scanner scan = new Scanner(System.in);
        while(scan.hasNextLine())
        {
            String line = scan.nextLine();
            String[] tokens = line.split(" ");
            if(tokens[0].equalsIgnoreCase("search"))
            {
                search(tokens[1], 1);
            }
            else if(tokens[0].equalsIgnoreCase("download"))
            {
                download(Integer.parseInt(tokens[1]));
            }
            else if(tokens[0].equalsIgnoreCase("exit"))
            {
                departNode();
            }
            else
            {
                System.out.println("Invalid command.");
            }
        }
        scan.close();
    }

    //Starts the server and listens for any incoming connections, which are handled by clientHandler().
    public void startServer()
    {
        try
        {
            ServerSocket socket = new ServerSocket(PORT);
            while(true)
            {
                Socket client = socket.accept();
                if(!neighbors.contains(client.getInetAddress().getHostAddress()))
                {
                    addNeighbor(client.getInetAddress().getHostAddress());
                    System.out.println("Server neighbors: " + neighbors);
                }
                else
                {
                    clientHandler(client);
                }
                client.close();
            }
        }
        catch(IOException e)
        {
            System.err.println("Error starting server! " + e);
            e.printStackTrace();
        }
    }

    //Handles all requests that a client can make. Additionally, handles replies received from other servers.
    public void clientHandler(Socket client)
    {
        try
        {
            InputStream in = client.getInputStream();
            BufferedReader read = new BufferedReader(new InputStreamReader(in));
            String line;
            while((line = read.readLine()) != null) //Reads in requests.
            {
                String[] tokens = line.split("\t");

                //Handle node departures.
                if(tokens[0].equals("leaving"))
                {
                    if(tokens.length > 1)
                    {
                        String[] leavingNodeNeighbors = tokens[1].split(" ");
                        neighbors.addAll(Arrays.asList(leavingNodeNeighbors));
                    }
                    neighbors.remove(client.getInetAddress().getHostAddress());
                    System.out.println("Server neighbors: " + neighbors);
                }

                //Process replies from other servers.
                else if(tokens.length == 3)
                {
                    processReply(tokens[0], tokens[1], tokens[2]);
                }

                //Send file to client to download.
                else if(tokens.length == 1)
                {
                    sendFile(new File("/home/013/a/ax/axz210027/" + homeDirectory + "/" + tokens[0]), client);
                }

                //Handle client search request and forward if necessary. Ignore if duplicate request.
                else
                {
                    int hopCount = Integer.parseInt(tokens[1]);

                    if(previousSearches.containsKey(tokens[0]))
                    {
                        return;
                    }
                    previousSearches.put(tokens[0], client.getInetAddress().getHostAddress());
                    scheduler.schedule(() ->
                    {
                        previousSearches.remove(tokens[0]);
                    }, MAX_HOP_COUNT, TimeUnit.SECONDS);
                    String fileKeyword = localSearch(tokens[0]);
                    if(!fileKeyword.equals("File not found."))
                    {
                        sendReply(tokens[0], fileKeyword, client.getLocalAddress().getHostAddress());
                    }
                    else if(hopCount > 0)
                    {
                        forwardRequest(tokens[0], hopCount);
                    }
                }
            }
        }
        catch(IOException e)
        {
            System.err.println("Error handling client! " + e);
            e.printStackTrace();
        }
    }

    //Forwards search request to all neighbors.
    private void forwardRequest(String searchID, int hopCount)
    {
        for(String n: neighbors)
        {
            try
            {
                Socket socket = new Socket(n, PORT);
                PrintWriter pout = new PrintWriter(socket.getOutputStream(), true);
                pout.println(searchID + "\t" + (hopCount - 1));
                socket.close();
            }
            catch (IOException e)
            {
                System.err.println("Error forwarding request: " + e.getMessage());
            }
        }
    }

    //Searches for the file locally by checking availableFiles.txt.
    private String localSearch(String searchIdentifier) throws IOException
    {
        String[] tokens = searchIdentifier.split(" ");
        String keyword = tokens[1];
        File checkFile = new File("/home/013/a/ax/axz210027/" + homeDirectory + "/availableFiles.txt");
        Scanner scan = new Scanner(checkFile);
        while(scan.hasNextLine())
        {
            String line = scan.nextLine();
            String[] elements = line.split("\\s+");
            for(String s: elements)
            {
                if(s.equalsIgnoreCase(keyword))
                {
                    return line;
                }
            }
        }
        return "File not found.";
    }

    //Client initiates search request starting at hop count 1 all the way to hop count 16.
    private void search(String keyword, int hopCount)
    {
        if(hopCount > MAX_HOP_COUNT)
        {
            System.out.println("Search terminated at hop count 16 with no results found.");
            return;
        }
        while(!receivedReplies.isEmpty())
        {
            receivedReplies.remove(0);
        }
        searchStartTime = System.currentTimeMillis();
        try
        {
            if(!searches.containsKey(keyword))
            {
                searches.put(keyword, 0);
            }
            else
            {
                searches.put(keyword, searches.get(keyword) + 1);
            }
            String searchID = InetAddress.getLocalHost().getHostAddress() + " " + keyword + " " + searches.get(keyword);
            previousSearches.put(searchID, InetAddress.getLocalHost().getHostAddress());
            forwardRequest(searchID, hopCount);

            //Wait hop count seconds for all replies to arrive.
            //Then display replies or start a new search with increased hop count.
            scheduler.schedule(() ->
            {
                if (!receivedReplies.isEmpty())
                {
                    System.out.println("File found at hop count: " + hopCount);
                    displayReplies();
                }
                else
                {
                    System.out.println("Search timed out at hop count " + hopCount + ". Retrying.");
                    search(keyword, hopCount * 2);
                }
            }, hopCount, TimeUnit.SECONDS);
        }
        catch (IOException e)
        {
            System.err.println("Error starting search: " + e.getMessage());
        }
    }

    //Determine what to do with received reply. If initiator, consume it.
    //Otherwise, forward to first node that sent the corresponding search request.
    private void processReply(String searchID, String fileKeyword, String location)
    {
        try
        {
            String initiator = searchID.split(" ")[0];
            if(InetAddress.getLocalHost().getHostAddress().equals(initiator))
            {
                long timeElapsed = System.currentTimeMillis() - searchStartTime;
                receivedReplies.add(fileKeyword + " " + location + "  " + timeElapsed + "ms");
            }
            else
            {
                sendReply(searchID, fileKeyword, location);
            }
        }
        catch (IOException e)
        {
            System.err.println("Error starting search: " + e.getMessage());
        }
    }

    //Sends reply to first node that sent the corresponding search request.
    private void sendReply(String searchID, String fileKeyword, String location)
    {
        try
        {
            Socket socket = new Socket(previousSearches.get(searchID), PORT);
            PrintWriter pout = new PrintWriter(socket.getOutputStream(), true);
            pout.println(searchID + "\t" + fileKeyword + "\t" + location);
            socket.close();
        }
        catch (IOException e)
        {
            System.err.println("Error starting search: " + e.getMessage());
        }
    }

    //Displays all received replies.
    public void displayReplies()
    {
        System.out.print("\n" + receivedReplies.size());
        if(receivedReplies.size() == 1)
        {
            System.out.println(" reply was received.");
        }
        else
        {
            System.out.println(" replies were received.");
        }
        int i = 1;
        for(String reply: receivedReplies)
        {
            System.out.println(i++ + ": " + reply);
        }
        System.out.println("Which reply would you like to choose to download? Please enter the command download <replyNum>");
    }

    //Downloads the file from the server listed in the selected reply.
    private void download(int replyNum)
    {
        if(replyNum > receivedReplies.size() || receivedReplies.size() == 0)
        {
            System.out.println("Cannot download file. No such reply has been received.");
        }
        else if(new File("/home/013/a/ax/axz210027/" + homeDirectory + "/" + receivedReplies.get(replyNum - 1).split(" ")[0]).isFile())
        {
            System.out.println("Cannot download file. You already have this file.");
        }
        else
        {
            try
            {
                String[] tokens = receivedReplies.get(replyNum - 1).split("\\s+");
                Socket socket = new Socket(tokens[2], PORT);
                System.out.println("You selected reply " + replyNum + ": " + receivedReplies.get(replyNum - 1));
                PrintWriter pout = new PrintWriter(socket.getOutputStream(), true);
                pout.println(tokens[0]);
                InputStream in = socket.getInputStream();
                BufferedReader read = new BufferedReader(new InputStreamReader(in));
                PrintWriter writeFile = new PrintWriter("/home/013/a/ax/axz210027/" + homeDirectory + "/" + tokens[0]);
                String line;
                while((line = read.readLine()) != null)
                {
                    if (line.equals("END_OF_FILE"))
                    {
                        break;
                    }
                    writeFile.println(line);
                }
                makeAvailable(tokens[0], tokens[1]);
                read.close();
                pout.close();
                writeFile.close();
                socket.close();
            }
            catch(IOException e)
            {
                System.err.println("Error downloading file: " + e.getMessage());
            }
        }
    }

    //Server sends file to the client.
    private void sendFile(File file, Socket client)
    {
        try
        {
            PrintWriter pout = new PrintWriter(client.getOutputStream(), true);
            List<String> lines = Files.readAllLines(file.toPath());
            for(String line: lines)
            {
                pout.println(line);
            }
            pout.println("END_OF_FILE");
            pout.flush();
        }
        catch (IOException e)
        {
            System.err.println("Error sending file: " + e.getMessage());
        }
    }

    //Adds the file to availableFiles.txt.
    private void makeAvailable(String file, String keyword) throws IOException
    {
        FileWriter writeFile = new FileWriter("/home/013/a/ax/axz210027/" + homeDirectory + "/availableFiles.txt", true);
        PrintWriter pout = new PrintWriter(writeFile);
        pout.println(file + " " + keyword);
        pout.close();
    }

    //Node leaves the P2P network. Must inform neighbors so that it is removed from their adjacency lists.
    private void departNode()
    {
        int selectedNeighbor = (int) (Math.random() * neighbors.size());
        for(int i = 0; i < neighbors.size(); i++)
        {
            try
            {
                Socket socket = new Socket(neighbors.get(i), PORT);
                PrintWriter pout = new PrintWriter(socket.getOutputStream(), true);
                if(i != selectedNeighbor || neighbors.size() == 1)
                {
                    pout.println("leaving");
                }
                else
                {
                    String allNeighbors = "";
                    for(String n: neighbors)
                    {
                        allNeighbors = allNeighbors.concat(n + " ");
                    }
                    pout.println("leaving\t" + allNeighbors);
                }
                socket.close();
            }
            catch (IOException e)
            {
                System.err.println("Error forwarding request: " + e.getMessage());
            }
        }
        System.out.println("Exiting the P2P system.\n\n");
        System.exit(0);
    }
}
