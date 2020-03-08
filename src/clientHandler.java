import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
//optimized (handles request is 6ms instead of 4000ms)
public class clientHandler implements Runnable{
	private Socket request;
	private static int nbOfRequests = 0;
	static final String ROOT = "D:\\www\\";
	static final String DEFAULT_FILE = "index.html";
	static final String ERROR_FILE_404 = "404.html";
	static final String ERROR_FILE_501 = "501.html";
	static final String ERROR_FILE_304 = "304.html";
	static final String POST_FILE = "post.html";

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
		BufferedReader in = null;
		DataOutputStream out = null;
		try {
			in = new BufferedReader(new InputStreamReader(request.getInputStream()));
			out = new DataOutputStream (request.getOutputStream());
			ArrayList<String> headerTokens = new ArrayList<String>();
			String token = "";

			while(in.ready()) {
				int theCharNum = in.read();
				char theChar = (char) theCharNum;
				if(theChar == ' ') {
					headerTokens.add(token);
					token ="";
				}
				token += theChar;
				
			}
			System.out.print(headerTokens.toString());
			String method = headerTokens.get(0);
			if (!method.equals("GET")  && !method.equals("HEAD") && !method.equals("PUT") && !method.equals("POST")) {

				long contentLength= Files.size(Paths.get(ROOT+ERROR_FILE_501));
				String contentMimeType = "text/html";
				out.writeBytes("HTTP/1.1 501 Not Implemented/n");
				out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0/n");
				out.writeBytes("Date: " + new Date()+"/n");
				out.writeBytes("Content-type: " + contentMimeType+"/n");
				out.writeBytes("Content-length: " + contentLength+"/n");
				out.writeBytes("/n");
				out.flush();
				Files.copy(Paths.get(ROOT+ERROR_FILE_501), out);
				out.flush();
			}
			else 
			{
				String path = headerTokens.get(1);
				if(method.equals("GET")) {
					if(path.equals("/")) {		
						long contentLength= Files.size(Paths.get(ROOT+DEFAULT_FILE));
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Content-Type: text/html\r\n");
						out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
						out.flush();
						Files.copy(Paths.get("D:\\www\\index.html"), out);
						out.flush();
					}
					else
					{
						long contentLength= Files.size(Paths.get(ROOT+path));
						
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\n");
						out.writeBytes("Date: " + new Date()+"\n");
						out.writeBytes("Content-Type: "+getMimeType(path)+"\r\n");
						out.writeBytes("Content-Length: "+contentLength+"\r\n");
						out.writeBytes("\n");
						out.flush();
						Files.copy(Paths.get("D:\\www\\"+path), out);
						out.flush();
					}
					
				}
				else if(method.equals("PUT"))
				{
					out.writeBytes("HTTP/1.1 200 OK\r\n");
					out.writeBytes("Content-Type: text/html\r\n");
					out.flush();
				}
				else if(method.equals("HEAD")){
					out.writeBytes("HTTP/1.1 200 OK \r\n");
					out.writeBytes("Content-Type: text/html\r\n\r\n");
				}
				else if(method.equals("POST"))
				{
					try {
						int position = headerTokens.indexOf("posts");
						String message = headerTokens.get(position);
						String contentToAdd = 
								" <tr>\r\n" + 
								"    <td>"+new Date()+"</td>\r\n" + 
								"    <td>"+request.getInetAddress()+"</td>\r\n" + 
								"	<td>"+message+"</td>\r\n" + 
								"  </tr>";
						
						Files.writeString(Paths.get(ROOT+POST_FILE),contentToAdd+"\n" ,StandardOpenOption.APPEND);
						long contentLength= Files.size(Paths.get(ROOT+POST_FILE));
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\n");
						out.writeBytes("Date: " + new Date()+"\n");
						out.writeBytes("Content-Type: "+getMimeType(POST_FILE)+"\r\n");
						out.writeBytes("Content-Length: "+contentLength+"\r\n");
						out.writeBytes("\n");
						out.flush();
						Files.copy(Paths.get(ROOT+POST_FILE), out);
						out.flush();
					}
					catch(Exception e)
					{
						e.printStackTrace();
						out.writeBytes("HTTP/1.1 304 Not Modified\r\n");
						out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0");
						out.writeBytes("Content-Type: text/html\r\n\n");
						out.writeBytes("Date: " + new Date());
						out.writeBytes("\n");
						out.flush();
						Files.copy(Paths.get(ROOT+ERROR_FILE_304), out);
						out.flush();
						out.close();

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
			out.flush();
			out.writeBytes(Files.readString(Paths.get(ROOT+ERROR_FILE_404)));//AANPASSEN
			out.flush();
			out.close();
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

