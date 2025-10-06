import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Test implements Runnable
{
    private List<String> neighbors;
    public Test()
    {
        this.neighbors = new ArrayList<>();
    }

    public static void main(String[] args)
    {
        Test node = new Test();
        node.run();
        if(!args[0].equalsIgnoreCase("none"))
        {
            node.joinNetwork(args[0]);
        }
        Scanner scan = new Scanner(System.in);
        while(scan.hasNextLine())
        {
            String line = scan.nextLine();
            node.sendMessage(line);
        }
    }

    @Override
    public void run()
    {
        new Thread(this::startServer).start();
    }

    public void joinNetwork(String host)
    {
        try
        {
            Socket socket = new Socket(host, 5000);
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

    public void startServer()
    {
        try
        {
            ServerSocket socket = new ServerSocket(5000);
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
                    InputStream in = client.getInputStream();
                    BufferedReader read = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while((line = read.readLine()) != null)
                    {
                        System.out.println(line);
                        PrintWriter pout = new PrintWriter(client.getOutputStream(), true);
                        pout.println("Acknowledged");
                        pout.flush();
                    }
                }
                client.close();
            }
        }
        catch(IOException e)
        {
            System.err.println("Error starting server! " + e);
        }
    }

    public void addNeighbor(String neighbor)
    {
        neighbors.add(neighbor);
    }

    public void sendMessage(String message)
    {
        Socket socket = null;
        try
        {
            socket = new Socket(neighbors.get(0), 5001);
            PrintWriter pout = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            pout.println(socket.getLocalAddress().getHostAddress() + " " + message);
            pout.flush();

            String response = in.readLine();
            System.out.println("Server response: " + response);
        }
        catch (IOException e)
        {
            System.err.println("Error in sending/receiving message: " + e);
        }
        finally
        {
            if (socket != null)
            {
                try
                {
                    socket.close();
                }
                catch (IOException e)
                {
                    System.err.println("Error closing socket: " + e);
                }
            }
        }
    }
}
