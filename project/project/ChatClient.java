import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {
    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {
        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        this.socket = new Socket(server, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        out.println(message);
    }
    
    // Método principal do objecto
    public void run() throws IOException {
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    printMessage(formatMessage(line) + "\n");
                }
            } catch (IOException e) {
                System.err.println("Connection lost.");
            }
        });
        readerThread.start();
    }

    //Método para processar as mensagens recebidas num
    //formato mais amigável
    private String formatMessage(String message) {
        if (message.startsWith("MESSAGE")) {
            String[] parts = message.split(" ", 3);
            return parts[1] + ": " + parts[2];
        } else if (message.startsWith("NEWNICK")) {
            String[] parts = message.split(" ", 3);
            return parts[1] + " mudou de nome para " + parts[2];
        } else if (message.startsWith("JOINED")) {
            return message.substring(7) + " entrou na sala.";
        } else if (message.startsWith("LEFT")) {
            return message.substring(5) + " saiu da sala.";
        } else if (message.startsWith("PRIVATE")) {
            // Formatação para mensagens privadas
            String[] parts = message.split(" ", 3);
            return "Private " + parts[1] + ": " + parts[2];
        } else if (message.startsWith("BYE")) {
            return "Adeus!";
        }
        return message;
    }
    
    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
