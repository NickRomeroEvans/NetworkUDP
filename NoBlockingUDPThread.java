import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoBlockingUDPThread extends Thread {
	protected DatagramChannel channel = null;
	protected Selector selector;
	protected HashMap<Integer, Session> sessionMap; // Key is session id number and the value is the Session object
													// representing the client with that id
	protected int sequenceNumber;
	protected AtomicBoolean userquit;
	private static final int TIMEOUT = 3000; // The threashold of time outs
	private static final int CHECKTIMEOUT = 1000; // Used to check when to check
	private static final boolean DEBUG = false;
													// for timeouts
	protected Queue<Event> eventQueue;

	public NoBlockingUDPThread(String port) throws IOException {
		this("NoBlockingUDPThread", port);
	}

	public NoBlockingUDPThread(String name, String port) throws IOException {
		super(name);
		channel = DatagramChannel.open();
		channel.configureBlocking(false);
		channel.socket().bind(new InetSocketAddress(Integer.parseInt(port)));
		sessionMap = new HashMap<Integer, Session>();
		selector = Selector.open();
		channel.register(selector, SelectionKey.OP_READ);
		userquit = new AtomicBoolean(false);
		sequenceNumber = 0;
		eventQueue = new LinkedList<Event>();
	}

	@Override
	public void run() {
		// New method with selector
		long lastCheck = System.currentTimeMillis();
		ByteBuffer bbuf = ByteBuffer.allocate(1024);
		while (!userquit.get()) { // quit variable
			try {
				selector.select(TIMEOUT);

				Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();

				while (keyIter.hasNext()) {
					SelectionKey key = keyIter.next();
					if (key.isReadable()) {
						// Time to handle Channel
						// Get clients channel and receive the buffer
						DatagramChannel clientChannel = (DatagramChannel) key.channel();

						SocketAddress client = clientChannel.receive(bbuf);

						bbuf.flip();
						// Get response to client
						ByteBuffer response = handleRead(bbuf, client);
						bbuf.clear();
						if (response != null) {
							response.flip();
							// clientChannel.send(response, client);
							eventQueue.add(new Event(response, client));
							key.interestOps(SelectionKey.OP_WRITE);
						}

					} else if (key.isWritable()) {
						Event response = eventQueue.poll();
						if (response != null) {
							DatagramChannel clientChannel = (DatagramChannel) key.channel();
							clientChannel.send(response.getBuffer(), response.getSocketAddress());
						}

						key.interestOps(SelectionKey.OP_READ);
					}

					keyIter.remove();
				}

				// Check sessions for timeouts
				if (System.currentTimeMillis() - lastCheck > CHECKTIMEOUT) {
					lastCheck = System.currentTimeMillis();
					for (Session session : sessionMap.values()) {
						if (lastCheck - session.lastAccess >= TIMEOUT) {
							debugMessage(session.getSessionId(), session.getSequence() + 1, "Session timed out");
							ByteBuffer response = goodbyeAndCloseSession(session.getSessionId());
							response.flip();
							channel.send(response, session.sa);
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// Check sessions for timeout keep track of last check
		for (Session session : sessionMap.values()) {
			debugMessage(session.getSessionId(), session.getSequence() + 1, "Sending Goodbye to client");
			ByteBuffer response = goodbyeAndCloseSession(session.getSessionId());
			response.flip();
			try {
				channel.send(response, session.sa);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Constructs a ByteBuffer in response to bbuf, supplied by client, that
	 * follows the P0P protocol.
	 * */
	public ByteBuffer handleRead(ByteBuffer bbuf, SocketAddress client) throws IOException {
		MessageUtils.Message message = MessageUtils.parseMessageBuffer(bbuf);

		// Discard packets that don't have the right version or magic number.
		if (!message.isValid()) {
			return null;
		}

		Session session = sessionMap.get(message.getSessionId());
		ByteBuffer newHeader = null;

		if (session == null && message.getCommand() != MessageUtils.HELLO) {
			debugMessage(message.getSessionId(), message.getSequence(), "Protocol error");

			newHeader = goodbyeAndCloseSession(message.getSessionId());
			return newHeader;
		}

		switch (message.getCommand()) {
		case MessageUtils.HELLO: {
			if (session == null && message.getSequence() == 0) {
				sessionMap.put(message.getSessionId(),
						new Session(message.getSessionId(), client, message.getSequence(), System.currentTimeMillis()));

				printMessage(message.getSessionId(), message.getSequence(), "Session created");

				newHeader = MessageUtils
						.buildMessage(MessageUtils.HELLO, message.getSequence(), message.getSessionId());
			} else {
				// Hello from registered client is a protocol error
				debugMessage(message.getSessionId(), message.getSequence(), "Protocol error");
				newHeader = goodbyeAndCloseSession(message.getSessionId());
			}
			break;
		}
		case MessageUtils.DATA: {
			int oldSequnce = session.sequenceNumber;

			if (message.getSequence() == oldSequnce) {
				printMessage(message.getSessionId(), message.getSequence(), "duplicate packet");
				session.lastAccess = System.currentTimeMillis();
				sessionMap.put(message.getSessionId(), session);
				newHeader = MessageUtils.buildMessage(MessageUtils.ALIVE, sequenceNumber++, message.getSessionId());
			} else if (message.getSequence() < oldSequnce) {
				// Sequence too low from Client treat as protocol error
				debugMessage(message.getSessionId(), oldSequnce + 1, "Sequence too low from Client Goodbye.");
				newHeader = goodbyeAndCloseSession(message.getSessionId());
			} else {
				for (int i = oldSequnce + 1; i < message.getSequence(); i++) {
					printMessage(message.getSessionId(), i, "Lost Packet");
				}

				session.sequenceNumber = message.getSequence();
				session.lastAccess = System.currentTimeMillis();

				sessionMap.put(message.getSessionId(), session);
				printMessage(message.getSessionId(), message.getSequence(), message.getData());
				newHeader = MessageUtils.buildMessage(MessageUtils.ALIVE, sequenceNumber++, message.getSessionId());
			}
			break;
		}
		case MessageUtils.ALIVE: {
			// Client should never send ALIVE treat as protocol error
			debugMessage(message.getSessionId(), message.getSequence(), "Protocol error");
			newHeader = goodbyeAndCloseSession(message.getSessionId());
			break;
		}
		case MessageUtils.GOODBYE: {
			printMessage(message.getSessionId(), message.getSequence(), "GOODBYE from client.");
			newHeader = goodbyeAndCloseSession(message.getSessionId());
			break;
		}
		default: {
			// Client sent an unknown command treat as protocol error
			debugMessage(message.getSessionId(), message.getSequence(), "Protocol error");
			newHeader = goodbyeAndCloseSession(message.getSessionId());
		}

		}

		return newHeader;
	}

	
	void debugMessage(int sessionId, int sequence, String received) {
		if(DEBUG) printMessage(sessionId, sequence, received);
	}
	
	void printMessage(int sessionId, int sequence, String received) {
		String filter = received == null ? "" : received;
		System.out.println("0x" + Integer.toHexString(sessionId) + " [" + sequence + "]" + " " + filter);
	}

	/**
	 * Close the current session and generate "Goodbye" message
	 * 
	 * @returns a ByteBuffer in "fill" mode, flip before sending
	 */
	public ByteBuffer goodbyeAndCloseSession(int sessionId) {
		// Remove session
		sessionMap.remove(sessionId);
		System.out.println("0x" + Integer.toHexString(sessionId) + " Session closed");
		return MessageUtils.buildMessage(MessageUtils.GOODBYE, sequenceNumber++, sessionId, null);
	}

	// Notify server that the user has quit the server.
	void quit() {
		userquit.set(true);
	}

	class Event {
		public ByteBuffer buf;
		public SocketAddress sa;

		Event(ByteBuffer bbuf, SocketAddress sa) {
			buf = bbuf;
			this.sa = sa;
		}

		public ByteBuffer getBuffer() {
			return buf;
		}

		public SocketAddress getSocketAddress() {
			return sa;
		}
	}

	class Session {
		public int sessionID;
		public int sequenceNumber;
		public SocketAddress sa;
		public long lastAccess;

		public int getSessionId() {
			return sessionID;
		}

		public int getSequence() {
			return sequenceNumber;
		}

		public long getLastAccess() {
			return lastAccess;
		}

		public SocketAddress getSocketAddress() {
			return sa;
		}

		// Constructor
		Session(int sessionID, SocketAddress address, int sequence, long lastAccess) {
			this.sessionID = sessionID;
			this.sa = address;
			this.sequenceNumber = sequence;
			this.lastAccess = lastAccess;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Session)) {
				return false;
			}

			Session cs = (Session) o;
			return this.sa.equals(cs.sa) && this.sessionID == cs.sessionID;
		}

		@Override
		public int hashCode() {
			return this.sessionID + this.sa.hashCode();
		}
	}
}
