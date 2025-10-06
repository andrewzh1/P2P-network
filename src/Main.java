
public class Main
{
    public static void main(String[] args)
    {
        System.out.println("Welcome to the P2P System! Valid commands are: search <keyword>, download <replyNum>, and exit.");
        Node thisPC = new Node(args[1]);
        if(!args[0].equalsIgnoreCase("none"))
        {
            thisPC.joinNetwork(args[0]);
        }
        thisPC.run();
        thisPC.clientLoop();
    }
}
