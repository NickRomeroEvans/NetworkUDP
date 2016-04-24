# NetworkUDP

P0P, the Project 0 Protocol, defines what messages are sent between the client and server, and how they are encoded.
(There is a mail protocol, POP, with a similar-looking name, but the two have nothing to do with each other.) 
P0P application transfers lines of input from the client's stdin to the server, which then prints them on its stdout.

Protocol Headers 
P0P is much more realistic than the protocols shown in sections and class, in large part because it defines a message as a header plus data (rather than just data). Without a header, you can have only one kind of message, and so can't do simple things you almost certainly need to do, even if you don't realize it yet. (One example is returning an error indication, for instance, even though we don't do that in this project.) Protocols you design should always include headers in message encodings.

Protocol Sessions 
P0P supports the notion of a session. A session is a related sequence of messages coming from a single client. Sessions allow the server to maintain state about each individual client. For instance, the server could, in theory, print out how many messages it has received in each session, for instance, or it could maintain a shopping cart for each session. (We don't actually implement either of those.)

Unlike TCP (which has "connections"), UDP doesn't have any notion related to sessions, so we build them as part of our protocol.

Protocol Messages and Format 
P0P defines four message types: HELLO, DATA, ALIVE, and GOODBYE. All message encodings include a header. The header is filled with binary values. The header bytes are the initial bytes of the message. They look like this, with fields sent in order from left to right:

magic	version	command	sequence number	session id
16 bits	8 bits	8 bits	32 bits	32 bits
magic is the value 0xC461 (i.e., decimal 50273, if taken as an unsigned 16-bit integer). An arriving packet whose first two bytes do not match the magic number is silently discarded.
version is the value 1.
command is 0 for HELLO, 1 for DATA, 2 for ALIVE, and 3 for GOODBYE.
sequence numbers in messages sent by the client are 0 for the first packet of the session, 1 for the next packet, 2 for the one after that, etc. Server sequence numbers simply count up each time the server sends something (in any session).
session id is an arbitrary 32-bit integer. The client chooses its value at random when it starts. Both the client and the server repeat the session id bits in all messages that are part of that session.
Multi-byte values are sent big-endian (which is often called "network byte order").
In DATA messages, the header is followed by arbitrary data; the other messages do not have any data. The receiver can determine the amount of data that was sent by subtracting the known header length from the length of the UDP packet, something the language/package you use will provide some way of obtaining.

Only one P0P message may be sent in a single UDP packet, and all P0P messages must fit in a single UDP packet. P0P itself does not define either maximum or minimum DATA payload sizes. It expects that all reasonable implementations will accept data payloads that are considerably larger than a typical single line of typed input.

P0P Message Processing

Server: ./server <portnum> 
<portnum> is the port number the server should bind to.
Client: ./client <hostname> <portnum>. 
<hostname> and <portnum> give the location of the server. The hostname can be a domain name (e.g., attu1.cs.washington.edu) or an IPv4 address (e.g., 128.208.1.137).

https://courses.cs.washington.edu/courses/cse461/16wi/projects/proj0/proj0.html
