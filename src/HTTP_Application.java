import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

import jdk.jshell.spi.ExecutionControl.NotImplementedException;

//optimized (handles request is 6ms instead of 4000ms)
public class HTTP_Application implements Runnable {
	private Socket clientConnection;
	static final String ROOT = "D:\\www\\";
	static final String DEFAULT_FILE = "index.html";
	static final String ERROR_FILE_404 = "/errors/404.html";
	static final String ERROR_FILE_400 = "/errors/400.html";
	static final String ERROR_FILE_501 = "/errors/501.html";
	static final String FILES_LIST = "files.html";
	static final String POST_FILE = "post.html";
	private final Date firstRequestTime;
	static final int keep_Alive = 1000;
	private int requestInOneConnection = 0;
	
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
				e.printStackTrace();
				System.out.println("\n connection for " + clientConnection.getInetAddress() + " has been closed");
				clientConnection.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	public void handleRequest() throws IOException {
		BufferedReader in = null;
		DataOutputStream out = null;
		try {
			in = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
			out = new DataOutputStream(clientConnection.getOutputStream());
			ArrayList<String> headerTokens = getRequestTokens(in);
			for (String tokens : headerTokens) {
				System.out.println(tokens);
			}
			if (headerTokens.size() == 0) {
				
			} else {
				requestInOneConnection += 1;
				System.out.print("\n requests:" + requestInOneConnection);
				System.out.print("\n--------------------------------------\n");
				
				if(checkIfValidRequest(headerTokens)) {
					getBadRequestHeader(in, out);
				}
				
				String method = headerTokens.get(0);
				if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("PUT")
						&& !method.equals("POST")) {
					getNotImplementedHeader(out);
					out.close();
				} else {
					String ifnonematch = "";
					String notmodifiedsince = "";
					try {
						ifnonematch = headerTokens.get(headerTokens.indexOf("If-None-Match:") + 1);
						notmodifiedsince = headerTokens.get(headerTokens.indexOf("If-Modified-Since:") + 1);
					} catch (IndexOutOfBoundsException e) {

					}
					String path = headerTokens.get(1);
					if (method.equals("GET")) {
						if (path.equals("/")) {
							path = DEFAULT_FILE;
						}
						File file = new File(ROOT + path);
						String ETag = getEtag(file);
						
						if (ifnonematch.equals(ETag)) {
								getNotModifiedHeader(out);
							} else {
								getHeader(out);
								getFile(out, path,ETag,file);
							}

					} else if (method.equals("PUT")) {
						putMethod(headerTokens);
						getHeader(out);
						out.writeBytes("\r\n");
						out.flush();

					} else if (method.equals("HEAD")) {
						getHeader(out);
						out.writeBytes("Content-Type: " + getMimeType(path) + "; charset=UTF-8 \r\n");
						out.writeBytes("\r\n");
						out.flush();
					} else if (method.equals("POST")) {
						postMethod(headerTokens);
						getHeader(out);
						File file = new File(ROOT + POST_FILE);
						String ETag = getEtag(file);
						getFile(out, POST_FILE,ETag,file);
					}
				}
			}
		} catch (FileNotFoundException | NoSuchFileException e) {
			getFileNotFoundHeader(in, out);
		} 
		catch (Exception e) {
			e.printStackTrace();
			getNotImplementedHeader(out);
		}
	}
	
	
	private ArrayList<String> getRequestTokens(BufferedReader in) throws IOException {
		ArrayList<String> headerTokens = new ArrayList<String>();
		String token = "";

		while (in.ready()) {
			int theCharNum = in.read();
			char theChar = (char) theCharNum;
			if (theChar == '\n' || theChar == '\r') {
				theChar = ' ';
			}
			token += theChar;
			if (theChar == ' ' || theChar == '=' || theChar == ':') {
				headerTokens.add(token.strip());
				token = "";
			}
		}
		headerTokens.add(token);
		headerTokens.removeIf(a -> a.equals(""));
		headerTokens.trimToSize();
		return headerTokens;
	}
	
	
	private void getFile(DataOutputStream out,String path,String ETag,File file) throws IOException {
		
		long contentLength = file.length();
		Date modifiedSince = new Date(file.lastModified());
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: " + getMimeType(path) + "; charset=UTF-8 \r\n");
		out.writeBytes("ETag:" + ETag + "\r\n");
		out.writeBytes("Last-Modified:" + dateFormat.format(modifiedSince) + "\r\n");
		out.writeBytes("\r\n");
		out.flush();
		Files.copy(Paths.get(ROOT + path), out);
		out.flush();
	}
	
	private String getEtag(File file) throws IOException {
		int fileLength = (int) file.length();
		byte[] fileData = readFileData(file, fileLength);
		return calculateEtag(fileData);
	}

	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) {
				fileIn.close();
			}
		}
		return fileData;
	}

	private String calculateEtag(byte[] fileData) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(fileData);
			return bytesToHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

	private void getHeader(DataOutputStream out) throws IOException {
		out.writeBytes("HTTP/1.1 200 OK\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes(
				"Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Connection: Keep-Alive\r\n");
		out.writeBytes("Keep-Alive: timeout=5, max=1000\r\n");
	}
	
	
	private void postMethod(ArrayList<String> headerTokens) throws IOException {
		String message = headerTokens.get(headerTokens.size() - 1);
		message = message.replace("=", "");
		message = message.replace("+", " ");
		String contentToAdd = " <tr>\r\n" + "    <td>" + new Date() + "</td>\r\n" + "    <td>"
				+ clientConnection.getInetAddress() + "</td>\r\n" + "	<td>" + message + "</td>\r\n"
				+ "  </tr>";

		Files.writeString(Paths.get(ROOT + POST_FILE), contentToAdd + "\n", StandardOpenOption.APPEND);
		long contentLength = Files.size(Paths.get(ROOT + POST_FILE));
	}
	
	private void putMethod(ArrayList<String> headerTokens) throws Exception {
		String filename = headerTokens.get(headerTokens.indexOf("filename=") + 1).replace('"', ' ')
				.strip();
		
		if (!checkIfValidFile(filename)) {
			throw new Exception();
		}
		else {
			String contentType = headerTokens.get(headerTokens.lastIndexOf("Content-Type:") + 1);
			List<String> fileContent = headerTokens.subList(headerTokens.lastIndexOf("Content-Type:") + 2,
					headerTokens.size() - 1);

			boolean exist = Files.exists(Path.of(ROOT + "/files/" + filename), LinkOption.NOFOLLOW_LINKS);
			FileWriter myWriter = new FileWriter(ROOT + "/files/" + filename);
			for (String string : fileContent) {
				myWriter.write(string+" ");
			}
			myWriter.close();
			if(!exist) {
				updateFiles(filename);
			}
		}
		
	}
	
	private void updateFiles(String filename) throws IOException {
		String contentToAdd = " <tr>\r\n" + "    <td>" + new Date() + "</td>\r\n" + "    <td>"
				+ clientConnection.getInetAddress() + "</td>\r\n" + "	<td> <a href=\"/files/"
				+ filename + "\"> " + filename + "</a> </td>\r\n" + "  </tr>";

		Files.writeString(Paths.get(ROOT + FILES_LIST), contentToAdd, StandardOpenOption.APPEND);
	}
	
	private void getNotModifiedHeader(DataOutputStream out) throws IOException {
		System.out.print("not modified");
		out.writeBytes("HTTP/1.1 304 Not Modified\r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: Keep-Alive\r\n");
		out.writeBytes("\r\n");
		out.flush();
	}

	private void getFileNotFoundHeader(BufferedReader in, DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_404));
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

	private void getBadRequestHeader(BufferedReader in, DataOutputStream out) throws IOException {
		long contentLength = Files.size(Paths.get(ROOT + ERROR_FILE_400));
		out.writeBytes("HTTP/1.1 400 Bad Request \r\n");
		out.writeBytes("Date: " + new Date() + "\r\n");
		out.writeBytes("Server: Java HTTP Server from Delmeiren Jonathan & Tilman Quentin : 1.0\r\n");
		out.writeBytes("Content-Length: " + contentLength + "\r\n");
		out.writeBytes("Content-Type: text/html; charset=UTF-8 \r\n");
		out.writeBytes("Connection: closed\r\n");
		out.writeBytes("\r\n");
		out.flush();
		out.writeBytes(Files.readString(Paths.get(ROOT + ERROR_FILE_400)));// AANPASSEN
		out.flush();
	}

	private void getNotImplementedHeader(DataOutputStream out) throws IOException {
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

	private String getMimeType(String path) {
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

	private boolean checkIfValidFile(String filename) {
		if (filename.endsWith("txt") || filename.endsWith("html") || filename.endsWith("rtf"))
			return true;
		return false;
	}
	
	private boolean checkIfValidRequest(ArrayList<String> headerTokens) {
		if (!(headerTokens.contains("Host:"))) {
			return false;
		}
		if(headerTokens.get(headerTokens.lastIndexOf("Host:")+1).equals(headerTokens.get(headerTokens.indexOf("Host:")+1)))
			return false;
		return true;
	}
}
