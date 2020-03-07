
import java.net.*;
import java.util.HashMap;
public class Server {
	private final static int port= 80;
	@SuppressWarnings({ "resource", "static-access" })
	public static void main(String[] args) throws Exception{
		try {
			ServerSocket server = new ServerSocket(port);
			while(true) {
				Socket clientRequest = server.accept();
				Thread thread = new Thread(new clientRequestHandler(clientRequest));
				thread.run();
				thread.sleep(1000);
				clientRequest.close();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}
}
