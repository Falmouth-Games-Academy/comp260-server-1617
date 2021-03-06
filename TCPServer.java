import java.io.DataInputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.net.ServerSocket;

//Source From http://makemobiapps.blogspot.co.uk/p/multiple-client-server-chat-programming.html

/*
 * A chat server that delivers public and private messages.
 */
public class TCPServer {
	
	private static Timer timer = Timer.getInstance();
	
	private static TCPServer tCPServer = new TCPServer();

	// The server socket.
	private static ServerSocket serverSocket = null;
	// The client socket.
	private static Socket clientSocket = null;

	// This chat server can accept up to maxClientsCount clients' connections.
	private static final int maxClientsCount = 40;
	private static final clientThread[] threads = new clientThread[maxClientsCount];

	//Our Code////////////////////////////////////////////////////////////
	public clientThread[] GetThreads() {
		return threads;
	}
	
	public static TCPServer getInstance(){
		return tCPServer;
	}
	
	private TCPServer(){}
	
	//Our code ends////////////////////////////////////////////////////////
	public static void main(String args[]) {

		// The default port number.
		int portNumber = 2222;
		if (args.length < 1) {
			System.out.println(
					"Usage: java MultiThreadChatServerSync <portNumber>\n" + "Now using port number=" + portNumber);
		} else {
			portNumber = Integer.valueOf(args[0]).intValue();
		}

		/*
		 * Open a server socket on the portNumber (default 2222). Note that we
		 * can not choose a port less than 1023 if we are not privileged users
		 * (root).
		 */
		try {
			serverSocket = new ServerSocket(portNumber);
		} catch (IOException e) {
			System.out.println(e);
		}

		/*
		 * Create a client socket for each connection and pass it to a new
		 * client thread.
		 */
		while (true) {
			try {
				clientSocket = serverSocket.accept();
				int i = 0;
				for (i = 0; i < maxClientsCount; i++) {
					if (threads[i] == null) {
						(threads[i] = new clientThread(clientSocket, threads, i)).start();
						break;
					}
				}
				if (i == maxClientsCount) {
					PrintStream os = new PrintStream(clientSocket.getOutputStream());
					os.println("Server too busy. Try later.");
					os.close();
					clientSocket.close();
					timer.getTickThread().interrupt();
				}
			} catch (IOException e) {
				System.out.println(e);
			}
		}
	}
}

/*
 * The chat client thread. This client thread opens the input and the output
 * streams for a particular client, ask the client's name, informs all the
 * clients connected to the server about the fact that a new client has joined
 * the chat room, and as long as it receive data, echos that data back to all
 * other clients. The thread broadcast the incoming messages to all clients and
 * routes the private message to the particular client. When a client leaves the
 * chat room this thread informs also all the clients about that and terminates.
 */
class clientThread extends Thread {

	private String clientName = null;
	private DataInputStream inStream = null;
	private PrintStream outStream = null;
	private Socket clientSocket = null;
	private final clientThread[] threads;
	//Our Code////////////////////////////////////////////////////////////

	private int maxClientsCount;
	private Character character = new Character(1, 1);
	private final int ID;

	private Parser parser = Parser.getInstance();

	public clientThread(Socket clientSocket, clientThread[] threads, int ID) {
		this.clientSocket = clientSocket;
		this.threads = threads;
		this.ID = ID;
		maxClientsCount = threads.length;
	}

	public int getID(){
		return ID;
	}
	//Our Code End/////////////////////////////////////////////////////////

	@SuppressWarnings("deprecation")
	public void run() {
		int maxClientsCount = this.maxClientsCount;
		clientThread[] threads = this.threads;

		try {
			/*
			 * Create input and output streams for this client.
			 */
			inStream = new DataInputStream(clientSocket.getInputStream());
			outStream = new PrintStream(clientSocket.getOutputStream());
			String name;
			while (true) {
				outStream.println("Enter your name.");
				name = inStream.readLine().trim();
				if (name.indexOf('@') == -1) {
					break;
				} else {
					outStream.println("The name should not contain '@' character.");
				}
			}

			/* Welcome the new the client. */
			outStream.println("Welcome " + name + " Client ID: " + GetClientThread()
					+ " to our chat room.\nTo leave enter /quit in a new line.");
			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] == this) {
						clientName = "@" + name;
						break;
					}
				}
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this) {
						threads[i].outStream.println("*** A new user " + name + " entered the chat room !!! ***");
					}
				}
			}
			/* Start the conversation. */
			while (true) {
				String line = inStream.readLine();
				if (line.startsWith("/quit")) {
					break;
				}
				/* If the message is private sent it to the given client. */
				if (line.startsWith("@")) {
					String[] words = line.split("\\s", 2);
					if (words.length > 1 && words[1] != null) {
						words[1] = words[1].trim();
						if (!words[1].isEmpty()) {
							synchronized (this) {
								for (int i = 0; i < maxClientsCount; i++) {
									if (threads[i] != null && threads[i] != this && threads[i].clientName != null
											&& threads[i].clientName.equals(words[0])) {
										threads[i].outStream.println("<" + name + "> " + words[1]);
										/*
										 * Echo this message to let the client
										 * know the private message was sent.
										 */
										this.outStream.println(">" + name + "> " + words[1]);
										break;
									}
								}
							}
						}
					}
				} else {
					/*
					 * The message is public, broadcast it to all other clients.
					 */
					synchronized (this) {
						for (int i = 0; i < maxClientsCount; i++) {
							if (threads[i] != null && threads[i].clientName != null) {
								//Our Code////////////////////////////////////////////////////////////

								// Added line
								threads[i].outStream.println("<" + name + "> " + line);
								// This section accesses the parser and adds the
								// inputed line/word to the command list
								String commands = line;
								String finalID = Long.toString(getID());
								if (getID() < 10){
									long iD =getID();
									Long.toString(iD);
									finalID = ("0" + iD);
									
								}
								parser.addToCommands(commands + finalID);
								threads[i].outStream.println();
								// commands = null;
								//Our Code Ends////////////////////////////////////////////////////////


							}
						}
					}
				}
			}
			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this && threads[i].clientName != null) {
						threads[i].outStream.println("*** The user " + name + " is leaving the game !!! ***");
					}
				}
			}
			outStream.println("*** Bye " + name + " ***");

			/*
			 * Clean up. Set the current thread variable to null so that a new
			 * client could be accepted by the server.
			 */
			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] == this) {
						threads[i] = null;
					}
				}
			}
			/*
			 * Close the output stream, close the input stream, close the
			 * socket.
			 */
			inStream.close();
			outStream.close();
			clientSocket.close();
		} catch (IOException e) {
		}

	}
	//Our Code////////////////////////////////////////////////////////////

	// Function for getting the client ID for each client thread
	public long GetClientThread() {

		return Thread.currentThread().getId();
	}

	public Character GetCharacter() {
		return character;
	}
	//Our Code Ends////////////////////////////////////////////////////////

}
