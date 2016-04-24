import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class Client extends Thread {
	private static final int TIMEOUT = 3000;
	protected int sequence; // The clients sequence number
	protected int session; // The session Id of the client
	protected boolean handShake; // Flag set when the client receives Hello when
									// in the Hello Wait state.
	protected InetAddress address;
	protected DatagramSocket socket;
	protected int port;
	protected boolean userquit; // Flag set when client transitions to closing
								// state.

	class TimeoutTask extends TimerTask {
		Client client;

		TimeoutTask(Client c) {
			client = c;
		}

		@Override
		public void run() {
			try {
				// Server has timed out
				if (!userquit) {
					timeoutTask = null;
					sendGoodBye();
				} else {
					// Timeout in closing state
					timeoutTask = null;
					socket.close();
					System.exit(0);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	TimerTask timeoutTask = null;
	Timer timer;

	// constructor
	public Client(String hostname, String port, int session) throws IOException {
		this.address = InetAddress.getByName(hostname);
		this.sequence = 0;
		this.session = session;
		this.socket = new DatagramSocket();
		this.timer = new Timer(true);
		this.handShake = false;
		this.port = Integer.parseInt(port);
	}

	@Override
	public void run() {
		try {
			sendHello();

			while (!userquit) {
				byte[] buf = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);
				processPacket(packet.getData(), packet.getLength());
			}

			// Wait until we receive a goodbye from the server
			// or our timer task times out because we still have to process
			// multiple alive commands from server.
			while (true) {
				byte[] buf = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);
				processPacket(packet.getData(), packet.getLength());
			}
		} catch (IOException e) {

			e.printStackTrace();
		}
		socket.close();
	}

	// processPacket() -- read and process an incoming UDP packet
	public void processPacket(byte[] buf, int length) throws IOException {
		try {
			MessageUtils.Message message = MessageUtils.parseMessageBuffer(buf, length);

			if (!(message.isValid())) {
				// Received invalid header
				// Silently discard
				return;
			}
			// handshake = false if we haven't gotten a HELLO response
			if (!handShake) {
				if (message.getCommand() != MessageUtils.HELLO) {
					// Send Goodbye() and wait for response
					if (!userquit)
						sendGoodBye();
				} else {
					handShake = true;
					cancelTimeout();
					return;
				}
			}

			if (message.getCommand() == MessageUtils.ALIVE) {
				if (!userquit)
					cancelTimeout();
				return;
			}

			// Server sent goodbye message assume it is gone and transition to
			// closed state.
			if (message.getCommand() == MessageUtils.GOODBYE) {
				socket.close();
				System.exit(0);
			}

			// Received an unknown command
			// Send Good Bye to server if haven't sent already
			if (!userquit)
				sendGoodBye();
		} catch (java.net.SocketTimeoutException e) {
			// Don't need to do anything with the socketTimeout
			System.err.println("Caught Exception: " + e.getMessage());
		}
	}

	// processInput() -- process input read from stdin
	public void processInput(String msg) throws IOException {
		ByteBuffer buf = MessageUtils.buildMessage(MessageUtils.DATA, sequence++, session, msg);
		buf.flip();
		processBuffer(buf);
	}

	void quit() {
		userquit = true;
	}

	// Send Hello
	public void sendHello() throws IOException {
		ByteBuffer hellobuf = MessageUtils.buildMessage(MessageUtils.HELLO, sequence++, session);
		hellobuf.flip();
		processBuffer(hellobuf);
	}

	// Send GoodBye
	public void sendGoodBye() throws IOException {
		ByteBuffer byebuf = MessageUtils.buildMessage(MessageUtils.GOODBYE, sequence++, session);
		quit();
		byebuf.flip();
		processBuffer(byebuf);
	}

	// Process the buffer then send.
	public void processBuffer(ByteBuffer buf) throws IOException {
		DatagramPacket packet = new DatagramPacket(buf.array(), buf.limit(), address, port);
		socket.send(packet);
		startTimeout();
	}

	public void startTimeout() {
		synchronized (this) {
			if (timeoutTask == null) {
				timeoutTask = new TimeoutTask(this);
				timer.schedule(timeoutTask, TIMEOUT);
			}
		}
	}

	public void cancelTimeout() {
		synchronized (this) {
			// Cancel timeout if exists and user hasn't quit.
			if (timeoutTask != null && !userquit)
				timeoutTask.cancel();
			timeoutTask = null;
		}
	}
}