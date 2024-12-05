# Project: Chat service

# Name: Gage Wilson

## Demo video link: [Demo video link](https://youtu.be/6G9OM8SyDKQ)

## Manifest:
- ChatClient.java: This is the class that represents the client in the server. 
- ChatSever.java: This is the class that represents the server and handles all server side stuff
- ClientHandler.java: This class handles all of the client commands and parses them properly

## Building and running:

To run the server, in the root directory of this project open a terminal and run:
```shell
$ javac ChatServer.java
```

This will compile the ChatServer and you'll be able to run it with the command line arguments:
```shell
$ java ChatServer -p <port> -d <debug level>
```
Where port is the port number to start the server on, and the debug level is either 0 or 1 to indicate what logging level you want the server.

To run the client, in the root directory of the project folder run:
```shell
$ javac ChatClient.java
```
This will compile the client

To run the client and connect to a running server, run the command:
```shell
$ java ChatClient
```
You will be greeted with a prompt telling you to connect to a server, to connect to a server you can type:
```shell
$ /connect <server-ip/name> [port]
```
The port is optional, if a server is running on port 5000 you don't have to specify the port. Otherwise, if the server isn't running on port 5000 you will have to specify the port number. From there, you will be connected to the server and the prompts and help command will guide you through using the server from there.

## Testing methodology:
- Testing involved incrementally developing the code and then running it. Once I got multithreading implemented, I started connecting multiple clients to the server in order to test it. Honestly, that's the only thing I did to test the code, was just constantly running it as I made small changes.

## Observations/Reflection:
- This project was pretty tough to take on alone. I had to learn and refresh myself on a lot of different topics in order to get this working correctly. I spent anytime that I was bored over thanksgiving break on reading information and learning in order to complete this project. I found that doing the single-threaded version was very easy to do honestly, but once I started incorporating multithreading and learning about that, it got much harder. I think this project was a great experience, and honestly I'm glad I did it without partners as often times working with a group is more of a hassle than it's worth from my experience in school. I'm interested to learn more about multi-threading and applications of it to use on projects in the future.

## Sources used
[Multithreading in Java](https://www.tutorialspoint.com/java/java_multithreading.htm)

[Shutdown hooks](https://www.geeksforgeeks.org/jvm-shutdown-hook-java/)

[Synchronized collections](https://www.baeldung.com/java-synchronized-collections)

[Executors](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html)

[Thread pools](https://www.baeldung.com/thread-pool-java-and-guava)

#### Since I worked on this project alone, I found that looking up ideas on how to actually design a ChatServer was really helpful since I didn't have any partners to discuss this with. These were two valuable resources for getting me started and going in the right direction.

[Ideas on how to design the classes](https://www.geeksforgeeks.org/multi-threaded-chat-application-set-1/)

[Set 2 of ideas on design](https://www.geeksforgeeks.org/multi-threaded-chat-application-set-2/)