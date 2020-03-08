
import java.net.*;
import java.util.Date;
public class Server {
	static final int PORT = 80;
	@SuppressWarnings({ "resource", "static-access" })
	public static void main(String[] args) throws Exception{
		try {
			ServerSocket server = new ServerSocket(PORT);
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
