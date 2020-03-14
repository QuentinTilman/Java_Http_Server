
import java.net.*;
public class TCPServer {
	static final int PORT = 80;
	public static void main(String[] args) throws Exception{
		try {
			ServerSocket server = new ServerSocket(PORT);
			System.out.print("Server started listining on port "+PORT+"...");
			while(true) {
				Thread thread = new Thread(new HTTP_Application(server.accept()));
				thread.start();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}
}
