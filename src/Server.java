import java.io.*;
import java.net.*;
import java.util.*;

public class Server
{
    private final String homeDirectory;
    private List<String> neighbors;

    public static void main(String[] args)
    {
        try
        {
            Server server = new Server(args[0]);
            if(args.length > 1)
            {
                server.addNeighbor(args[1]);
            }
            ServerSocket socket = new ServerSocket(5000);
            while(true)
            {
                Socket client = socket.accept();
                if(!server.neighbors.contains(client.getInetAddress().getHostAddress()))
                {
                    server.addNeighbor(client.getInetAddress().getHostAddress());
                    System.out.println("Server neighbors: " + server.neighbors);
                }
                else
                {
                    InputStream in = client.getInputStream();
                    BufferedReader read = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while((line = read.readLine()) != null)
                    {
                        System.out.println(line);
                        String[] tokens = line.split(" ");
                        System.out.println("Split successfully");
                        server.respondToSearch(tokens[1]);
                    }
                }
                client.close();
            }
        }
        catch(IOException e)
        {
            System.err.println(e);
        }
    }

    public Server(String homeDirectory)
    {
        this.homeDirectory = homeDirectory;
        this.neighbors = new ArrayList<>();
    }

    public void addNeighbor(String neighbor)
    {
        neighbors.add(neighbor);
    }

    public void respondToSearch(String keyword) throws FileNotFoundException
    {
        File checkFile = new File("/home/013/a/ax/axz210027/" + homeDirectory + "/availableFiles.txt");
        System.out.println("checkFile is" + checkFile);
        Scanner scan = new Scanner(checkFile);
        while(scan.hasNextLine())
        {
            String line = scan.nextLine();
            System.out.println(line);
            if(line.contains(keyword))
            {
                System.out.println("File " + keyword + " found!");
            }
            else
            {
                System.out.println("File " + keyword + " not found...");
            }
        }
        System.out.println("Well I guess the while loop never runs because the file is empty?");
    }

    public void requestSearch(String keyword)
    {

    }
}
