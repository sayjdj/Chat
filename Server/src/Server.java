import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by Jordan Blackadar as a part of the Server package in Chat.
 * GUI for Server interface
 * @author Jordan Blackadar<"jordan.blackadar@outlook.com"/>
 * @author Liam Brown<"liamnb525@gmail.com"/>
 * @version 0.3.5
 * @since 3/19/2017 : 2:15 PM
 */
//TODO Set all users to offline when closing the server
public class Server extends JFrame implements Runnable, ClientActionListener {
    private JTextArea serverLog;
    private JPanel panel;
    private JLabel numberOnlineLabel;
    private JTextField AdminField;
    private JTextField userSearch;

    private JMenuBar menus;
    private ArrayList<JMenu> menu = new ArrayList<>();

    private File preferences;
    protected Preferences loaded_prefs;

    public String name;
    private static final String SERVER_ERR_LBL = "Error: ";
    private ServerSocket server;
    private int numberOnline = 0;

    private File save = new File("save.svs");
    Save currentSave;

    public static void main(String[] args) {
        threadNewServer("Beta Chat Server", 9090);
    }

    public Server(int port) throws IOException {
        super("Chat Server");
        initGui(); //Set up GUI
        server = new ServerSocket(port); //Create socket to listen for connections
        output("Local IP: " + InetAddress.getLocalHost());  //Output the local IP
        initSaveFile(); //Initialize the save file, creating it if it does not exist

    }

    private static void threadNewServer(String name, int port){
        Server running = null;
        try {
            running = new Server(port); //Create server and set port
            running.name = name;
            Thread server = new Thread(running);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            if(!(running == null)) {
                for (User x : running.currentSave.all) {
                    x.online = false;
                }
            }
            JOptionPane.showMessageDialog(null, "A General Exception was Detected on Server " + running.name, e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Main loop for running server.  Waits for connections, upon receiving them creates a new ClientListener thread.
     */
    public void run() {
        output("Listening on Port " + server.getLocalPort() + ".");

        preferences = new File ("preferences.sprefs");
        if(!preferences.exists()) initPrefs(); //first run
        readPrefs();

        updateLabel();
        while (true) { //Continuously look for and accept new incoming connections
            try {
                Socket client = server.accept(); //Block until a connection is attempted

                //Create a client listener object to handle new user
                ClientListener pendingUser = new ClientListener(client, this);
                if(pendingUser.client.blacklist){ //Ensure that user is not banned before starting a listener
                    output("Banned user " + pendingUser.client.userName + " attempted to connect to the server");

                    //Ignore the connection attempt before starting new ClientListener thread
                    pendingUser = null;
                    continue;
                }
                pendingUser.addListener(this); //Assign this server as the listener for new client

                //Check if the user is in the saved list of users
                currentSave.recognize(pendingUser.client);
                Save.write(currentSave); //Save the user list
                pendingUser.client.online = true;

                //Create and run client listener thread
                Thread t = new Thread(pendingUser);
                t.start();

            } catch (IOException e) {
                System.err.println("Could not store user data file");
                e.printStackTrace();
                output(SERVER_ERR_LBL + "Could not store user data file");
            } catch (ClassNotFoundException e) {
                System.err.println("Client did not properly initialize connection");
                e.printStackTrace();
                output(SERVER_ERR_LBL + "Client did not properly initialize connection");
            }
        }
    }

    protected void output(String toOutput) {
        serverLog.append(toOutput + "\n");
        updateLabel();
    }

    private void updateLabel() {
        numberOnlineLabel.setText("Online: " + numberOnline);
        serverLog.setCaretPosition(serverLog.getDocument().getLength());
    }

    private void executeAdminCommand(String input) {
        String [] cmd = input.substring(1).split(" ");
        boolean exists = false;
        switch(cmd[0]){
            case "mod":

                for(int c = 0; c < currentSave.all.size(); c++){
                    if(currentSave.all.get(c).userName.equals(cmd[1])) {
                        exists = true;
                        output("Modded: " + currentSave.all.get(c).userName);
                        currentSave.all.get(c).isMod = true;
                        break;
                    }
                }
                if(!exists) output("Failed to unmod nonexistent user " + cmd[1]);
                break;
            case "unmod":
                for(int c = 0; c < currentSave.all.size(); c++){
                    if(currentSave.all.get(c).userName.equals(cmd[1])){
                        exists = true;
                        output("Unmodded: " + currentSave.all.get(c).userName);
                        currentSave.all.get(c).isMod = false;
                        break;
                    }
                }
                if(!exists) output("Failed to ban nonexistent user " +cmd[1]);
                break;
            case "ban":
                for(int c = 0; c < currentSave.all.size(); c++){
                    if(currentSave.all.get(c).userName.equals(cmd[1])){
                        exists = true;
                        output("Banned: " + currentSave.all.get(c).userName);
                        currentSave.all.get(c).blacklist = true;
                        break;
                    }
                }
                if(!exists) output("Failed to ban nonexistent user " + cmd[1]);
                break;
            case "unban":
                for(int c = 0; c < currentSave.all.size(); c++){
                    if(currentSave.all.get(c).userName.equals(cmd[1])){
                        exists = true;
                        output("Unbanned: " + currentSave.all.get(c).userName);
                        currentSave.all.get(c).blacklist = false;
                        break;
                    }
                }
                if(!exists) output("Failed to unban nonexistent user " + cmd[1]);
                break;
            case "userinfo":
                for(int c = 0; c < currentSave.all.size(); c++){
                    if(currentSave.all.get(c).userName.equals(cmd[1])){
                        exists = true;
                        output(currentSave.all.get(c).toString());
                        break;
                    }
                }
                if(!exists) output("Failed to print user info for nonexistent user " + cmd[1]);
                break;
            default:
                output("Admin command " + cmd[1] + " attempted does not exist.");
                break;
        }
    }

    @Override
    public void clientDisconnected(String userName, String address) {
        numberOnline--;
        updateLabel();
        output("Lost connection to " + userName + " at " + address);
    }

    @Override
    public void clientConnected(String userName, String address) {
        numberOnline++;
        updateLabel();
        output("Initializing Connection to " + userName + " at " + address);
    }

    @Override
    public void clientChangedName(String old, String updated) {
        output("ClientListener " + old + " changed alias to " + updated + ".");
    }

    private void initGui(){

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int)screen_size.getWidth();
        int height = (int) screen_size.getHeight();
        Font Sans = new Font("Sans Serif", Font.PLAIN, height / 72);
        Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("icon.png"));
        this.setIconImage(image);
        setContentPane(panel);

        //Create Menus
        menus = new JMenuBar();

        //File
        JMenu file = new JMenu("File");
        file.setFont(Sans);
        file.add(new JMenu("Test"));
        menus.add(file);

        //Preferences
        JMenu prefs = new JMenu("Preferences");
        prefs.setFont(Sans);
        menus.add(prefs);

        //Sub Menus
        JMenu save = new JMenu("Save");
        save.setFont(Sans);
        file.add(save);

        JMenu buffer = new JMenu("Buffer");
        buffer.setFont(Sans);
        prefs.add(buffer);

        JCheckBoxMenuItem chatlog = new JCheckBoxMenuItem("Chat Log");
        chatlog.setFont(Sans);
        chatlog.addItemListener(source -> {
            //System.out.println(loaded_prefs.readPreference("view_chat").getValue());
            if(loaded_prefs.readPreference("view_chat").getValue().equals("disabled")){
                System.out.println("SELECTED");
                loaded_prefs.setPreference("view_chat", "enabled");
            } else {
                System.out.println("DESELECTED");
                loaded_prefs.setPreference("view_chat", "disabled");
            }
        });
        buffer.add(chatlog);

        menu.add(file);
        menu.add(prefs);
        for(JMenu temp : menu) menus.add(temp);
        menus.setFont(Sans);
        menus.setPreferredSize(new Dimension(-1, height / 43));
        this.setJMenuBar(menus);

        this.setPreferredSize(new Dimension(width / 2, height / 2));
        numberOnlineLabel.setFont(Sans);
        serverLog.setLineWrap(true);
        serverLog.setMinimumSize(new Dimension(-1, -1));
        serverLog.setFont(Sans);
        updateLabel();

        //Initialize administrator command field
        AdminField.setFont(new Font("Sans Serif", Font.PLAIN, height / 72));
        AdminField.setPreferredSize(new Dimension(-1, height / 43));
        AdminField.setMinimumSize(new Dimension(-1, -1));
        AdminField.setText("Command");
        AdminField.setForeground(new Color(160,160,160));
        AdminField.addFocusListener(new FocusListener(){
            @Override
            public void focusGained(FocusEvent e) {
                AdminField.setText("");
                AdminField.setForeground(new Color(0,0,0));
            }
            @Override
            public void focusLost(FocusEvent e) {
                AdminField.setText("Command");
                AdminField.setForeground(new Color(160,160,160));
            }
        });
        AdminField.addActionListener(actionEvent -> {
            Message current = new Message(AdminField.getText());
            try {
                executeAdminCommand(current.contents);
            } catch(Exception e) {
                output("Admin command not in a valid format");
            }
            AdminField.setText("");
        });

        this.setLocationRelativeTo(null);
        pack();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setVisible(true);
    }



    private void initSaveFile () throws IOException{
        if(save.exists()){
            try {
                currentSave = Save.read();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            currentSave = new Save();
        }
    }


    /**
     * Initializes preferences object and writes it to file preferences.sprefs with some default settings and values.
     */
    private void initPrefs(){
        try {
            loaded_prefs = new Preferences();
            loaded_prefs.addPreference("view_chat" , "disabled");
            loaded_prefs.addPreference("color_scheme" , "default");
            loaded_prefs.addPreference("reject_connections" , "disabled");
            loaded_prefs.addPreference("require_password" , "disabled");
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(preferences));
            out.writeObject(loaded_prefs);
            out.flush();
            out.close();

        } catch (Exception e) {
            output("Failed to initialize preferences file");
        }
    }

    /**
     * Reads the preferences file into a preferences object.
     */
    private void readPrefs(){
        try {
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(preferences));
        loaded_prefs = (Preferences) in.readObject();
        in.close();
        } catch (Exception e) {
            output("Failed to read preferences file");
        }
    }

    /**
     * Writes the preferences object into the preferences.sprefs file.
     */
    private void writePrefs(){
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(preferences));
            out.writeObject(loaded_prefs);
            out.flush();
            out.close();
        } catch (Exception e) {
         output("Failed to write preferences file");
        }
    }

}