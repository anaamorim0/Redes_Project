import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384); // Buffer para leitura/escrita de dados
    static private final Charset charset = Charset.forName("UTF8"); // Charset para codificação de texto
    static private final CharsetDecoder decoder = charset.newDecoder(); // Decodificador UTF-8

    // Estruturas para gestão de clientes e salas
    static private final Map<SocketChannel, String> clients = new HashMap<>(); // Associa clientes aos seus nomes
    static private final Map<String, Set<SocketChannel>> rooms = new HashMap<>(); // Associa salas aos clientes
    static private final Set<SocketChannel> activeClients = Collections.synchronizedSet(new HashSet<>()); // Clientes conectados

    // Método main
    public static void main(String args[]) throws Exception {
        int port = Integer.parseInt(args[0]);

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ServerSocket ss = ssc.socket();
        InetSocketAddress isa = new InetSocketAddress(port);
        ss.bind(isa);

        Selector selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Listening on port " + port);

        while (true) {
            selector.select();

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (key.isAcceptable()) {
                    SocketChannel sc = acceptClient(ss, selector);
                    activeClients.add(sc);
                } else if (key.isReadable()) {
                    boolean ok = processInput(key);
                    if (!ok) disconnectClient(key);
                }
            }
        }
    }

    // Método que aceita um novo cliente e o regista para leitura
    private static SocketChannel acceptClient(ServerSocket ss, Selector selector) throws IOException {
        Socket s = ss.accept();
        SocketChannel sc = s.getChannel();
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_READ); 
        return sc;
    }
    
    // Método para processar os dados enviados por um cliente 
    //e executar comandos ou mensagens
    private static boolean processInput(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        buffer.clear();
        int bytesRead = sc.read(buffer);

        if (bytesRead == -1) return false;

        buffer.flip();
        String data = decoder.decode(buffer).toString().trim();

        // Divide os dados recebidos em mensagens/comandos separados por quebras de linha
        String[] messages = data.split("\n");
        for (String message : messages) {
            message = message.trim();
            if (message.isEmpty()) continue;

            if (message.startsWith("//")) {
                // Processa mensagens começadas por "//" (escape)
                String escapedMessage = message.substring(1); // Remove o primeiro '/'
                handleMessage(sc, escapedMessage);
            } else if (message.startsWith("/")) {
                // Verifica se é um comando válido
                if (!isValidCommand(message)) {
                    // Comando desconhecido
                    sendMessage(sc, "ERROR\n");
                } else {
                    // Trata como comando válido
                    if (!handleCommand(sc, key, message)) {
                        return false; 
                    }
                }
            } else {
                // Trata como mensagem normal
                handleMessage(sc, message);
            }
        }
        return true;
    }

    // Método para verificar se um comando é válido
    private static boolean isValidCommand(String message) {
        String[] parts = message.split(" ", 2);
        String cmd = parts[0];
        // Lista de comandos conhecidos
        return cmd.equals("/nick") || cmd.equals("/join") || cmd.equals("/leave") ||
            cmd.equals("/bye") || cmd.equals("/priv");
    }

    // Método para executar um comando válido enviado pelo cliente
    private static boolean handleCommand(SocketChannel sc, SelectionKey key, String command) throws IOException {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0];
        String argument = parts.length > 1 ? parts[1] : "";
        
        // Comando /nick
        if (cmd.equals("/nick")) {
            // Verifica se o nome é válido e não está em uso
            String nickname = argument.trim();
            if (nickname.isEmpty() || clients.containsValue(nickname)) {
                sendMessage(sc, "ERROR\n");
            } else {
                String oldNickname = (String) key.attachment();
                clients.put(sc, nickname);
                key.attach(nickname);
                sendMessage(sc, "OK\n");
                if (oldNickname != null) {
                    broadcast(sc, "NEWNICK " + oldNickname + " " + nickname + "\n");
                }
            }
        // Comando /join
        } else if (cmd.equals("/join")) {
            // Verifica se o nome da sala é válido
            String room = argument.trim();
            if (room.isEmpty()) {
                sendMessage(sc, "ERROR\n");
            } else {
                leaveRoom(sc); // Sai da sala antiga (se estiver numa)
                rooms.computeIfAbsent(room, k -> new HashSet<>()).add(sc);
                sendMessage(sc, "OK\n");
                broadcast(sc, "JOINED " + clients.get(sc) + "\n", room);
            }
        // Comando /leave
        } else if (cmd.equals("/leave")) {
            // Sai da sala atual (se estiver numa)
            leaveRoom(sc);
            sendMessage(sc, "OK\n");
        // Comando /bye
        } else if (cmd.equals("/bye")) {
            // Encerra a conexão do cliente
            leaveRoom(sc);
            sendMessage(sc, "BYE\n");
            return false;
        // Comando extra /priv
        } else if (cmd.equals("/priv")) {
            if (argument.isEmpty() || !argument.contains(" ")) {
                sendMessage(sc, "ERROR\n");
            } else {
                String recipientName = argument.substring(0, argument.indexOf(' ')).trim(); // Nome do destinatário
                String privateMessage = argument.substring(argument.indexOf(' ') + 1).trim(); // Mensagem privada
                handlePrivMessage(sc, recipientName, privateMessage);
            }            
        } else {
            // Comando desconhecido
            sendMessage(sc, "ERROR\n");
        }
        return true;
    }
    
    // Método para remover o cliente de todas as salas onde está presente
    private static void leaveRoom(SocketChannel sc) throws IOException {
        for (Set<SocketChannel> room : rooms.values()) {
            if (room.remove(sc)) {
                broadcast(sc, "LEFT " + clients.get(sc) + "\n");
            }
        }
    }

    // Métodos auxiliares para a execução dos comandos
    private static boolean handleMessage(SocketChannel sc, String message) throws IOException {
        String nickname = clients.get(sc);
        synchronized (clients) {
            if (nickname == null) {
                sendMessage(sc, "ERROR\n");
                return true;
            }
        }
        broadcast(sc, "MESSAGE " + nickname + " " + message + "\n");
        return true;
    }
    
    // Método para enviar uma mensagem para todos os clientes, incluindo o remetente
    private static void broadcast(SocketChannel sender, String message) throws IOException {
        for (SocketChannel client : activeClients) {
            sendMessage(client, message);
        }
    }    

    // Método para enviar uma mensagem para os clientes numa sala específica
    private static void broadcast(SocketChannel sender, String message, String room) throws IOException {
        for (SocketChannel client : rooms.getOrDefault(room, Collections.emptySet())) {
            if (client != sender) sendMessage(client, message);
        }
    }

    private static void sendMessage(SocketChannel sc, String message) throws IOException {
        sc.write(ByteBuffer.wrap(message.getBytes("UTF-8")));
    }

    private static void disconnectClient(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        activeClients.remove(sc);
        clients.remove(sc);
        leaveRoom(sc);
        sc.close();
        key.cancel();
    }

    // Método auxiliar para a execução do comando extra /priv
    private static void handlePrivMessage(SocketChannel sender, String recipientName, String privateMessage) throws IOException {
        // Verifica se o destinatário existe
        boolean userFound = false;
        for (Map.Entry<SocketChannel, String> entry : clients.entrySet()) {
            if (entry.getValue().equals(recipientName)) {
                sendMessage(entry.getKey(), "PRIVATE " + clients.get(sender) + " " + privateMessage + "\n");
                sendMessage(sender, "OK\n");
                userFound = true;
                break;
            }
        }
        if (!userFound) {
            sendMessage(sender, "ERROR\n");
        }
    }    
}
