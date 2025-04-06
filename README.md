# Redes_Project

# Java Chat Application — Server & Client with Multiplexed Communication

This project consists of implementing a **text-based chat server and client** in Java. It is based on a **custom communication protocol** over TCP sockets using a **multiplexed server model**.

## Objectives

- Develop a Java server capable of handling multiple clients simultaneously using **non-blocking multiplexed I/O**.
- Implement a simple Java GUI client that communicates with the server and uses **two threads**:
  - One to handle user interaction.
  - One to listen for messages from the server.
- Fully support the defined communication protocol.
- Handle message buffering and partial/multiple message transmissions correctly.


### How to run

To start the server on a given TCP port:

```bash
java ChatServer 8000
```

To start the client and connect to a given server:

```bash
java ChatClient localhost 8000

