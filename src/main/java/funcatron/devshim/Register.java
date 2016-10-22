package funcatron.devshim;

import com.fasterxml.jackson.databind.ObjectMapper;
import funcatron.intf.Context;
import funcatron.intf.Func;
import funcatron.intf.MetaResponse;
import funcatron.intf.impl.ContextImpl;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Register with the Funcatron "devmode Tron"
 * instance.
 */
public class Register {

    static class Mooser implements Func<Map, Map> {

        @Override
        public Map apply(Map map, Context context) {
            return new HashMap();
        }
    }

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

    static {
        execMap.put("invoke", Register::invoker);
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
        synchronized (syncObj) {
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

    /**
     * Based on the incoming
     * @param info
     * @return
     */
    private static Void invoker(Map<String, Object> info) {
        logger.log(Level.SEVERE, "Invoker");
        String classname = (String) info.get("class");
        Map<String, Object> headers = (Map<String, Object>) info.get("headers");

        try {
            Class<Func<Object, Object>> c = (Class<Func<Object, Object>>) Class.forName(classname);
            Func<Object, Object> f = c.newInstance();
            Method meth =
                    Arrays.stream(c.getMethods()).filter(m -> m.getName().equals("apply") &&
                            m.getParameterCount() == 2).findFirst().get();
            Class paramType = meth.getParameterTypes()[0];
            Object ret = f.apply(
                    (new ObjectMapper()).reader().forType(paramType).readValue(Base64.getDecoder().decode((String) info.get("body"))),
                    new ContextImpl(headers, LoggerFactory.getLogger(classname)));

            HashMap<String, Object> answer = new HashMap<>();
            answer.put("cmd", "reply");
            answer.put("replyTo", info.get("replyTo"));
            answer.put("status", 200);

            if (null == ret) {

            } else if (ret instanceof MetaResponse) {
                MetaResponse mr = (MetaResponse) ret;
                answer.put("headers",mr.getHeaders());
                answer.put("status", mr.getResponseCode());
                answer.put("body", Base64.getEncoder().encode(mr.getBody()));
            } else {
                answer.put("body", Base64.getEncoder().encode((new ObjectMapper()).writer().writeValueAsBytes(ret)));
            }

            sendMessage(answer);

        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "Unabled to invoke " + classname);
            HashMap<String, Object> answer = new HashMap<>();
            answer.put("cmd", "reply");
            answer.put("replyTo", info.get("replyTo"));
            answer.put("status", 500);

            try {
                answer.put("body", Base64.getEncoder().encode(e.getMessage().getBytes("UTF-8")));
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
        logger.log(Level.SEVERE, "in Got a line");
        System.out.println("in Got line ");
        System.err.println("in err Got line ");
        try {
            InputStreamReader ios = new InputStreamReader(tronInput, "UTF-8");
            BufferedReader br = new BufferedReader(ios);
            while (version == runCount.longValue()) {
                String line = br.readLine();
                logger.log(Level.SEVERE, "Got a line");
                System.out.println("Got line "+line);
                System.err.println("err Got line "+line);
                byte[] bytes = Base64.getDecoder().decode(line);
                final Map message = (new ObjectMapper()).reader().forType(Map.class).readValue(bytes);

                logger.log(Level.SEVERE, "Hey");

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
        byte[] bytes = (new ObjectMapper()).writer().writeValueAsBytes(msg);
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

    public static void main(String[] argv) throws Exception {
        register("localhost", 55667, new File("src/main/resources/funcatron.yml"));
        System.out.println("Funcatron registered");
    }
}
