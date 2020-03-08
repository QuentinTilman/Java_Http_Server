import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
//optimized (handles request is 6ms instead of 4000ms)
public class clientHandler implements Runnable{
	private Socket request;
	private static int nbOfRequests = 0;
	static final String ROOT = "D:\\www\\";
	static final String DEFAULT_FILE = "index.html";
	static final String ERROR_FILE_404 = "404.html";
	static final String ERROR_FILE_501 = "501.html";

	public clientHandler(Socket r) {
		this.setRequest(r);
	}

	public Socket getRequest() {
		return request;
	}

	public void setRequest(Socket request) {
		this.request = request;
	}

	public static int getNbOfRequests() {
		return nbOfRequests;
	}

	public static void setNbOfRequests(int nbOfRequests) {
		clientHandler.nbOfRequests = nbOfRequests;
	}

	@Override
	public void run() {

		nbOfRequests += 1;
		System.out.print("request nb : " +nbOfRequests+"\n");
		System.out.println("thread started opened. (" + new Date() + ")");
		BufferedReader in = null;
		DataOutputStream out = null;
		try {
			in = new BufferedReader(new InputStreamReader(request.getInputStream()));
			out = new DataOutputStream (request.getOutputStream());
			String header = "";

			/*while(in.ready()) {
				int theCharNum = in.read();
				char theChar = (char) theCharNum;
				header += theChar;
			}*/
			header = in.readLine();
			System.out.println("While is done. (" + new Date() + ")");
			String[] headerTokens = header.split(" ");
			String method = headerTokens[0];
			if (!method.equals("GET")  && !method.equals("HEAD") && !method.equals("PUT") && !method.equals("POST")) {

				long contentLength= Files.size(Paths.get(ROOT+ERROR_FILE_501));
				String contentMimeType = "text/html";
				out.writeBytes("HTTP/1.1 501 Not Implemented/n");
				out.writeBytes("Server: Java HTTP Server from SSaurel : 1.0/n");
				out.writeBytes("Date: " + new Date()+"/n");
				out.writeBytes("Content-type: " + contentMimeType+"/n");
				out.writeBytes("Content-length: " + contentLength+"/n");
				out.writeBytes("/n"); // blank line between headers and content, very important !
				out.flush(); // flush character output stream buffer
				// file
				Files.copy(Paths.get(ROOT+ERROR_FILE_501), out);
				out.flush();
			}
			else 
			{
				String path = headerTokens[1];
				if(method.contentEquals("GET")) {
					if(path.equals("/")) {		
						long contentLength= Files.size(Paths.get("D:\\www\\index.html"));
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Content-Type: text/html\r\n");
						out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
						Files.copy(Paths.get("D:\\www\\index.html"), out);
						out.flush();
					}
					else
					{
						long contentLength= Files.size(Paths.get("D:\\www"+path));
						
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Server: Java HTTP Server from SSaurel : 1.0");
						out.writeBytes("Date: " + new Date());
						out.writeBytes("Content-Type: "+getMimeType(path)+"\r\n");
						out.writeBytes("Content-Length: "+contentLength+"\r\n");
						out.writeBytes("\n");
						out.flush();
						Files.copy(Paths.get("D:\\www\\"+path), out);
						out.flush();
					}
					
				}
			}
			request.close();
		}
		catch(Exception e) {
			fileNotFound(in,out);
			e.printStackTrace();
		}
	}

	public void fileNotFound(BufferedReader in , DataOutputStream out) {
		try {
			long contentLength= Files.size(Paths.get("D:\\www\\404.html"));
			out.writeBytes("HTTP/1.1 404 \r\n");
			out.writeBytes("Content-Type: text/html\r\n");
			out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
			out.writeBytes(Files.readString(Paths.get(ROOT+ERROR_FILE_404)));//AANPASSEN
			out.flush();
		}
		catch(Exception e) {
		}
	}
	
	public String getMimeType(String path) {
		if(path.endsWith(".html"))
			return "text/html";
		else if(path.endsWith(".css")) 
			return "text/css";
		else if(path.endsWith(".png"))
			return "image/png";
		else if(path.endsWith(".jpg") || path.endsWith(".jpeg"))
			return "image/jpeg";
		else if(path.endsWith(".js"))
			return "text/javascript";
		else if(path.endsWith(".ico"))
			return "image/x-icon";
		else return null;
	}
}

