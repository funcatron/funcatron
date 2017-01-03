package funcatron.devshim;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import funcatron.intf.impl.ContextImpl;
import funcatron.intf.impl.Dispatcher;


import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Register with the Funcatron "devmode Tron"
 * instance.
 */
public class Register {

    static final Logger logger = Logger.getLogger("funcatron.devshim.Register");
    /**
     * Used to synchronize static operations
     */
    private static final Object syncObj = new Object();

    /**
     * The stream from the Tron
     */
    private static InputStream tronInput;

    /**
     * The stream to the Tron
     */
    private static OutputStream tronOutput;

    /**
     * The socket to the Tron
     */
    private static Socket tronSocket;

    /**
     * Keep track of the version of the registration
     */
    private static final AtomicLong runCount = new AtomicLong(0);

    /**
     * The thread that's watching the funcatron.yml|yaml|json file
     */
    private static Thread fileWatcher;

    /**
     * Our private thread pool
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * Maps commends to functions
     */
    private static final HashMap<String, Function<Map, Void>> execMap = new HashMap<>();

    /**
     * The properties file to send to the context for stuff like JDBC credentials
     */
    private static File thePropsFile = null;

    private static ObjectMapper jackson = new ObjectMapper();

    static {
        execMap.put("invoke", Register::invoker);
        jackson.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    /**
     * Register the runtime with the Funcatron development server
     *
     * @param host          the host of the funcatron server
     * @param port          the port of the funcatron server
     * @param funcatronFile the funcatron.yaml, funcatron.yml, or funcatron.json file
     */
    public static void register(String host, int port, File funcatronFile)
            throws IOException {
        register(host, port, funcatronFile, null);
    }

        /**
         * Register the runtime with the Funcatron development server
         *
         * @param host          the host of the funcatron server
         * @param port          the port of the funcatron server
         * @param funcatronFile the funcatron.yaml, funcatron.yml, or funcatron.json file
         * @param propsFile the properties file to be passed to the context
         */
    public static void register(String host, int port, File funcatronFile, File propsFile)
            throws IOException {
        synchronized (syncObj) {
            thePropsFile = propsFile;

            // start a new "version" of the registration
            runCount.incrementAndGet();
            shutdown();

            try {
                // connect to the host and port
                Socket sock = new Socket(host, port);
                InputStream is = sock.getInputStream();
                OutputStream os = sock.getOutputStream();


                tronSocket = sock;
                tronInput = is;
                tronOutput = os;

                // say hello to verify the connection
                sayHello();

                fileWatcher = new Thread(() -> startWatching(runCount.longValue(), funcatronFile), "Funcatron File Watcher");
                fileWatcher.start();

                Thread t = new Thread(() -> processMessages(runCount.longValue()), "Funcatron Tron Message Processor");
                t.start();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, ioe, () -> "Failed to connect to Tron");
                shutdown();
                throw ioe;
            }

        }

    }

    private static Map<String, Object> getContextProperties() {
        if (null == thePropsFile) return new HashMap<>();

        try {
            return jackson.readValue(thePropsFile, Map.class);
        } catch (IOException io) {
            logger.log(Level.WARNING, "Failed to load Context Props file as JSON", io);
            return new HashMap<>();
        }
    }

    /**
     * Based on the incoming request map (a thing much like a Ring request https://github.com/ring-clojure/ring/wiki/Concepts#requests)
     * the right class is loaded, the Context is re-initialized, and the class is loaded and the apply method
     * is called
     *
     * @param info a map the represents an HTTP request
     * @return nothing. It's Void
     */
    private static Void invoker(Map<Object, Object> info) {
        try {
            ContextImpl.initContext(getContextProperties(), Register.class.getClassLoader(), logger);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize Context", e);
        }

        String classname = (String) info.get("class");

        Map<Object, Object> request = (Map<Object, Object>) info.get("req");

        try {
            Dispatcher d = new Dispatcher();

            BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> applyFunc = d.apply(classname,
                    new HashMap<Object, Object>((Map<Object, Object>)request.get("swagger")) {{
                put("$deserializer", new BiFunction<InputStream, Class<?>, Object>() {
                    @Override
                    public Object apply(InputStream inputStream, Class<?> aClass) {
                        try {
                            return jackson.readValue(inputStream, aClass);
                        } catch (IOException ioe) {
                            throw new RuntimeException("Failed to deserialize data", ioe);
                        }
                    }
                });
                put("$serializer", new Function<Object, byte[]>() {
                    @Override
                    public byte[] apply(Object o) {
                        try {
                            return jackson.writeValueAsBytes(o);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to serialize data", e);
                        }
                    }
                });
            }});

            final String s = (String) request.get("body");
            InputStream bodyStream = null;

            if (null != s && s.length() > 0) {
                bodyStream = new ByteArrayInputStream(Base64.getDecoder().decode(s));
            }

            Map<Object, Object> response = applyFunc.apply(bodyStream, request);

            HashMap<String, Object> answer = new HashMap<>();

            answer.put("cmd", "reply");
            answer.put("replyTo", info.get("replyTo"));
            answer.put("response", response);
            answer.put("decodeBody", true);

            sendMessage(answer);

        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "Unabled to invoke " + classname);
            HashMap<String, Object> answer = new HashMap<>();
            HashMap<String, Object> response = new HashMap<>();
            HashMap<String, Object> repHeaders = new HashMap<>();

            answer.put("response", response);
            response.put("headers", repHeaders);

            answer.put("cmd", "reply");
            answer.put("replyTo", info.get("replyTo"));

            response.put("status", 500);
            repHeaders.put("Content-Type", "text/plain");
            try {
                response.put("body", e.getMessage());
                sendMessage(answer);
            } catch (IOException ioe) {
                logger.log(Level.WARNING, ioe, () -> "Failed to send response");
            }

        }
        return null;
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        byte[] ba = new byte[4096];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int cnt = 0;

        do {
            cnt = is.read(ba);
            if (cnt > 0) {
                bos.write(ba, 0, cnt);
            }
        } while (cnt >= 0);

        is.close();

        return bos.toByteArray();
    }

    private static void startWatching(long version, File funcatronFile) {
        String fileBytes = null;
        boolean first = true;
        while (version == runCount.longValue()) {
            try {
                if (!first) Thread.sleep(1000);
                first = false;

                byte[] ba = readBytes(new FileInputStream(funcatronFile));
                String contents = new String(ba, "UTF-8");
                if (!contents.equals(fileBytes)) {
                    fileBytes = contents;
                    HashMap<String, Object> msg = new HashMap<>();
                    msg.put("cmd", "setSwagger");
                    msg.put("swagger", contents);
                    sendMessage(msg);
                }

            } catch (InterruptedException ie) {
                // no need to log... if our version is wrong, we'll just exit the thread
            } catch (IOException ioe) {
                logger.log(Level.WARNING, ioe, () -> "Couldn't update");
            }
        }
    }

    private static void processMessages(long version) {
        try {
            InputStreamReader ios = new InputStreamReader(tronInput, "UTF-8");
            BufferedReader br = new BufferedReader(ios);
            while (version == runCount.longValue()) {
                String line = br.readLine();
                byte[] bytes = Base64.getDecoder().decode(line);
                final Map message = jackson.reader().forType(Map.class).readValue(bytes);

                String cmd = (String) message.get("cmd");
                if (null != cmd) {
                    Function<Map, Void> func = execMap.get(cmd);
                    if (null != func) {
                        executor.submit(() -> func.apply(message));
                    }
                }
            }
        } catch (UnsupportedEncodingException uee) {
            logger.log(Level.WARNING, uee, () -> "Totally unexpected that UTF-8 isn't supported");
        } catch (IOException ioe) {
            logger.log(Level.WARNING, ioe, () -> "Connection version " + version + " ended.");
        }
    }

    private static void sayHello() throws IOException {
        HashMap<String, Object> msg = new HashMap<>();
        msg.put("cmd", "hello");
        sendMessage(msg);
    }

    private static void sendMessage(Map msg) throws IOException {
        byte[] bytes = jackson.writer().writeValueAsBytes(msg);
        String line = Base64.getEncoder().encodeToString(bytes);
        synchronized (syncObj) {
            BufferedWriter br = new BufferedWriter(new OutputStreamWriter(tronOutput, "UTF-8"));
            br.write(line);
            br.write("\n");
            br.flush();
        }
    }

    /**
     * Closes the connections if there's a current connection
     */
    public static void shutdown() {
        synchronized (syncObj) {
            runCount.incrementAndGet();
            if (null != tronInput) {
                try {
                    InputStream is = tronInput;
                    tronInput = null;
                    is.close();

                } catch (IOException ioe) {
                    logger.log(Level.WARNING, ioe, () -> "Failed to close input stream.");
                }
            }

            if (null != tronOutput) {
                try {
                    OutputStream os = tronOutput;
                    tronOutput = null;
                    os.close();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, ioe, () -> "Failed to close output stream.");
                }
            }

            if (null != tronSocket) {
                try {
                    Socket sock = tronSocket;
                    tronSocket = null;
                    sock.close();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, ioe, () -> "Failed to close socket.");
                }
            }

            if (null != fileWatcher) {
                Thread t = fileWatcher;
                fileWatcher = null;
                t.interrupt();
            }

        }
    }

    public static void register(File funcatronFile) throws Exception {
        register("localhost", 54657, funcatronFile);
    }

    public static void register(String host, File funcatronFile) throws Exception {
        register(host, 54657, funcatronFile);
    }

    public static void register(int port, File funcatronFile) throws Exception {
        register("localhost", port, funcatronFile);
    }

    public static void main(String[] argv) throws Exception {
        register("localhost", 54657, new File("src/main/resources/funcatron.yml"));
        System.out.println("Funcatron registered");
    }
}
