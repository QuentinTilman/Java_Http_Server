import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

//optimized (handles request is 6ms instead of 4000ms)
public class HTTP_Application implements Runnable {
	private Socket clientConnection;
	private static int nbOfRequests = 0;
	static final String ROOT = "D:\\www\\";
	static final String DEFAULT_FILE = "index.html";
	static final String ERROR_FILE_404 = "404.html";
	static final String ERROR_FILE_501 = "501.html";
	static final String ERROR_FILE_304 = "304.html";
	static final String POST_FILE = "post.html";
	private final Date firstRequestTime;
	static final int keep_Alive = 1000;

	public HTTP_Application(Socket r) throws IOException {
		this.setRequest(r);
		this.firstRequestTime = new Date();
	}

	public Socket getRequest() {
		return clientConnection;
	}

	public void setRequest(Socket request) {
		this.clientConnection = request;
	}

	public static int getNbOfRequests() {
		return nbOfRequests;
	}

	public static void setNbOfRequests(int nbOfRequests) {
		HTTP_Application.nbOfRequests = nbOfRequests;
	}

	@Override
	public void run() {
		System.out.print("\n--------------------------------------\n");
		System.out.print("Client " + clientConnection.getInetAddress() + " made new request.");
		System.out.print("\n--------------------------------------\n");
		try {
			clientConnection.setKeepAlive(true);
			while ((firstRequestTime.getTime() + keep_Alive >= (new Date()).getTime())) {
				handleRequest();
			}
			clientConnection.close();
			
		} catch (IOException e) {
			try {
				System.out.println("connection for " + clientConnection.getInetAddress() + " has been closed");
				clientConnection.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	public void handleRequest() throws IOException {
		nbOfRequests += 1;
		BufferedReader in = null;
		DataOutputStream out = null;
		try {
			in = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
			out = new DataOutputStream(clientConnection.getOutputStream());
			ArrayList<String> headerTokens = new ArrayList<String>();
			String token = "";

			while (in.ready()) {
				int theCharNum = in.read();
				char theChar = (char) theCharNum;
				token += theChar;
				if (theChar == ' ' || theChar == '=') {
					headerTokens.add(token.strip());
					token = "";
				}
			}
			for (String tokens : headerTokens) {
				System.out.print(tokens);
			}
			if (headerTokens.size() == 0) {

			} else {
				System.out.print("\n--------------------------------------\n");
				headerTokens.add(token);
				String method = headerTokens.get(0);
				if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("PUT")
						&& !method.equals("POST")) {
					notImplementedError(out);
					out.close();
				} else {
					String path = headerTokens.get(1);
					if (method.equals("GET")) {
						if (path.equals("/")) {
							long contentLength = Files.size(Paths.get(ROOT + DEFAULT_FILE));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Date: " + new Date() + "\r\n");
							out.writeBytes(
									"Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
							out.writeBytes("Content-Length: " + contentLength + "\r\n");
							out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
							out.writeBytes("Connection: Keep-Alive\r\n");
							out.writeBytes("Keep-Alive: timeout=5, max=1000\r\n");
							out.writeBytes("\r\n");
							out.flush();
							Files.copy(Paths.get("D:\\www\\index.html"), out);
							out.flush();
						} else {
							long contentLength = Files.size(Paths.get(ROOT + path));
							out.writeBytes("HTTP/1.1 200 OK\r\n");
							out.writeBytes("Date: " + new Date() + "\r\n");
							out.writeBytes(
									"Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
							out.writeBytes("Content-Length: " + contentLength + "\r\n");
							out.writeBytes("Content-Type: " + getMimeType(path) + "; charset=UTF-8 \r\n");
							out.writeBytes("Connection: Keep-Alive\r\n");
							// out.writeBytes("Keep-Alive: timeout=5, max=1000\r\n");
							out.writeBytes("\r\n");
							out.flush();
							Files.copy(Paths.get("D:\\www\\" + path), out);
							out.flush();
						}

					} else if (method.equals("PUT")) {
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Content-Type: text/html\r\n");
						out.flush();
					} else if (method.equals("HEAD")) {
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Date: " + new Date() + "\r\n");
						out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
						out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
						out.writeBytes("Content-Type: " + getMimeType(path) + "; charset=UTF-8 \r\n");
						out.writeBytes("Connection: Keep-Alive\r\n");
						out.writeBytes("Keep-Alive: timeout=5, max=1000\r\n");
						out.writeBytes("\r\n");
						out.flush();
					} else if (method.equals("POST")) {
						String message = headerTokens.get(headerTokens.size() - 1);
						message = message.replace("=", "");
						message = message.replace("+", " ");
						String contentToAdd = " <tr>\r\n" + "    <td>" + new Date() + "</td>\r\n" + "    <td>"
								+ clientConnection.getInetAddress() + "</td>\r\n" + "	<td>" + message + "</td>\r\n"
								+ "  </tr>";

						Files.writeString(Paths.get(ROOT + POST_FILE), contentToAdd + "\n", StandardOpenOption.APPEND);
						long contentLength = Files.size(Paths.get(ROOT + POST_FILE));
						out.writeBytes("HTTP/1.1 200 OK\r\n");
						out.writeBytes("Date: " + new Date() + "\r\n");
						out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
						out.writeBytes("Content-Length: " + contentLength + "\r\n");
						out.writeBytes("Content-Type: " + getMimeType(POST_FILE) + "; charset=UTF-8 \r\n");
						out.writeBytes("Connection: Keep-Alive\r\n");
						out.writeBytes("Keep-Alive: timeout=5, max=1000\r\n");
						out.writeBytes("\r\n");
						out.flush();
						Files.copy(Paths.get(ROOT + POST_FILE), out);
						out.flush();
					}
				}
			}
		} catch (FileNotFoundException e) {
			fileNotFound(in, out);
			out.close();
		}
	}

	public void notModifiedError(DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_404));
		out.writeBytes("HTTP/1.1 304 Not Modified\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		Files.copy(Paths.get(ROOT + ERROR_FILE_304), out);
		out.flush();
	}

	public void fileNotFound(BufferedReader in, DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get("D:\\www\\404.html"));
		out.writeBytes("HTTP/1.1 404 \r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		out.writeBytes(Files.readString(Paths.get(ROOT + ERROR_FILE_404)));// AANPASSEN
		out.flush();
	}

	public void notImplementedError(DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_501));
		String contentMimeType = "text/html";
		out.writeBytes("HTTP/1.1 501 Not Implemented\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		Files.copy(Paths.get(ROOT + ERROR_FILE_501), out);
		out.flush();
	}

	public String getMimeType(String path) {
		if (path.endsWith(".html"))
			return "text/html";
		else if (path.endsWith(".css"))
			return "text/css";
		else if (path.endsWith(".png"))
			return "image/png";
		else if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
			return "image/jpeg";
		else if (path.endsWith(".js"))
			return "text/javascript";
		else if (path.endsWith(".ico"))
			return "image/x-icon";
		else
			return null;
	}
}
