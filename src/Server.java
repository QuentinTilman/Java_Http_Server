
import java.net.*;
public class Server {
	static final int PORT = 80;
	public static void main(String[] args) throws Exception{
		try {
			ServerSocket server = new ServerSocket(PORT);
			System.out.print("Server is listining on port "+PORT+"...");
			while(true) {
				Thread thread = new Thread(new clientHandler(server.accept()));
				thread.start();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}
}
