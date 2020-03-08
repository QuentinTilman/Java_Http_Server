
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;



public class clientRequestHandler implements Runnable {
	private Socket request;
	private static int nbOfRequests = 0;

	public clientRequestHandler(Socket r) {
		this.setRequest(r);
	}

	@SuppressWarnings("unused")
	@Override
	public void run() {

		nbOfRequests += 1;
		System.out.print("request nb : " +nbOfRequests+"\n");
		System.out.println("thread started opened. (" + new Date() + ")");
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(request.getInputStream()));
			DataOutputStream out = new DataOutputStream (request.getOutputStream());
			String header = "";
			
			while(in.ready()) {
				int theCharNum = in.read();
				char theChar = (char) theCharNum;
				header += theChar;
			}
			System.out.println("While is done. (" + new Date() + ")");
			String method = header.split(" ")[0];
			if (!method.equals("GET")  || !method.equals("HEAD") || !method.equals("PUT") || !method.equals("POST")) {
				out.writeBytes("HTTP/1.1 504 Not Implemented\r\n");
			}
			else if(method.equals("GET"))
			{
				//gebruik maken van StringTokenizer ???
				try {
					String path = header.split(" ")[1];
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
						if(path.endsWith(".html"))
						{
							long contentLength= Files.size(Paths.get("D:\\www"+path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Content-Type: text/html\r\n");
							out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
							Files.copy(Paths.get("D:\\www"+path), out);
							out.flush();
						}
						else if(path.endsWith(".css")) {
							long contentLength= Files.size(Paths.get("D:\\www\\"+path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Content-Type: text/css\r\n");
							out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
							Files.copy(Paths.get("D:\\www\\"+path), out);
							out.flush();
						}
						else if(path.endsWith(".js")) {
							long contentLength= Files.size(Paths.get("D:\\www\\"+path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Content-Type: text/javascript\r\n");
							out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
							Files.copy(Paths.get("D:\\www\\"+path), out);
							out.flush();
						}

						else if(path.endsWith(".png")) {
							long contentLength= Files.size(Paths.get("D:\\www\\"+path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Content-Type: image/png\r\n");
							out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
							Files.copy(Paths.get("D:\\www\\"+path), out);
							out.flush();
						}
						else if(path.endsWith(".ico")) {
							long contentLength= Files.size(Paths.get("D:\\www\\"+path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Content-Type: image/x-icon\r\n");
							out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
							Files.copy(Paths.get("D:\\www\\"+path), out);
							out.flush();
						}
						else if(path.endsWith(".jpg") || path.endsWith(".jpeg")) {
							long contentLength= Files.size(Paths.get("D:\\www\\"+path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Content-Type: image/jpeg\r\n");
							out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
							Files.copy(Paths.get("D:/www"+path), out);
							out.flush();
						}
					}
				}
				catch(AccessDeniedException e)
				{
					long contentLength= Files.size(Paths.get("D:\\www\\404.html"));
					out.writeBytes("HTTP/1.1 403 Forbidden\r\n");
					out.writeBytes("Content-Type: text/html\r\n");
					out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
					out.writeUTF(Files.readString(Paths.get("D:\\www\\404.html")));//AANPASSEN
					out.flush();
				}
				catch(NoSuchFileException e) {
					long contentLength= Files.size(Paths.get("D:\\www\\404.html"));
					out.writeBytes("HTTP/1.1 404 \r\n");
					out.writeBytes("Content-Type: text/html\r\n");
					out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
					out.writeUTF(Files.readString(Paths.get("D:\\www\\404.html")));//AANPASSEN
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
					String path = header.split(" ")[1];
					String[] items = header.split(" ");
					String arguments = items[items.length - 1];
					String input = arguments.split("=")[2];
					input = input.replace("+", " ");
					Files.writeString(Paths.get("D:\\www"+path),"<p>"+input+"</p> \n" ,StandardOpenOption.APPEND);
					long contentLength= Files.size(Paths.get("D:\\www"+path));
					out.writeBytes("HTTP/1.1 200 OK\r\n");
					out.writeBytes("Content-Type: text/html\r\n");
					out.writeBytes("Content-Length: "+contentLength+"\r\n\r\n");
					Files.copy(Paths.get("D:\\www"+path), out);
					out.flush();
				}
				catch(Exception e)
				{
					out.writeBytes("HTTP/1.1 304 Not Modified\r\n");
					out.writeBytes("Content-Type: text/html\r\n\n");
					out.flush();

				}
			}
			System.out.println("file is sended. (" + new Date() + ")");
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Socket getRequest() {
		return request;
	}

	public void setRequest(Socket request) {
		this.request = request;
	}
	
	public String getMIME(String mime)
	{
		return "html";
	}
	
	public void sendFile(String path)
	{
		
	}


}
