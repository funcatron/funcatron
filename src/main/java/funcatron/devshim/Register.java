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


    public static class User implements java.io.Serializable {

        private String name;
        private Integer age;

        public String getName(){
            return this.name;
        }

        public void setName(String name){
            this.name = name;
        }

        public Integer getAge(){
            return this.age;
        }

        public void setAge(Integer age){
            this.age = age;
        }
    }

    public static class PetOwner implements java.io.Serializable {
        private User pet;

        public PetOwner(User pet) {
            this.pet = pet;
        }

        public PetOwner() {

        }

        public User getPet() {return pet;}
        public void setPet(User u) {pet = u;}
    }

    static class Mooser implements Func<PetOwner, User> {

        @Override
        public User apply(PetOwner po, Context context) {

            User ret = null;

            if (null != po) ret = po.getPet();

            if (ret == null) {
                ret = new User();
                ret.setAge(52);
                ret.setName("David");
            }
            ret.setName("Hello: "+ret.getName());
            ret.setAge(1 + ret.getAge());

            return ret;
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
        String classname = (String) info.get("class");
        Map<String, Object> headers = (Map<String, Object>) info.get("headers");

        try {
            Class<Func<Object, Object>> c = (Class<Func<Object, Object>>) Class.forName(classname);
            Func<Object, Object> f = c.newInstance();
            Method meth =
                    Arrays.stream(c.getMethods()).filter(m -> m.getName().equals("apply") &&
                            m.getParameterCount() == 2).findFirst().get();
            Class paramType = meth.getParameterTypes()[0];
            Object theParam = null;

            if (null != info.get("body")) {
                theParam = (new ObjectMapper()).
                        readValue((String) info.get("body"), paramType);
            }

            Object ret = f.apply(
                    theParam,
                    new ContextImpl(headers, LoggerFactory.getLogger(classname)));

            HashMap<String, Object> answer = new HashMap<>();
            HashMap<String, Object> response = new HashMap<>();
            HashMap<String, Object> repHeaders = new HashMap<>();

            answer.put("cmd", "reply");
            answer.put("replyTo", info.get("replyTo"));
            answer.put("response", response);

            response.put("status", 200);
            response.put("headers", repHeaders);


            if (null == ret) {

                repHeaders.put("Content-Type", "text/plain");
                response.put("body", "");

            } else if (ret instanceof MetaResponse) {
                MetaResponse mr = (MetaResponse) ret;
                response.put("headers",mr.getHeaders());
                response.put("status", mr.getResponseCode());
                response.put("body", Base64.getEncoder().encode(mr.getBody()));
                answer.put("decodeBody", true);
            } else {
                response.put("body", ret);
                repHeaders.put("Content-Type", "application/json");
            }

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
                final Map message = (new ObjectMapper()).reader().forType(Map.class).readValue(bytes);

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
