package funcatron.helpers;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Bridge from the built in Java Logger to another kind of Logger
 */
public class LoggerBridge extends Logger {
    private final ActualLogger actual;

    public interface ActualLogger {
        void log(LogRecord lr);
        boolean isLoggable(Level lvl);
    }
    public LoggerBridge(ActualLogger actual) {

        super(null, null);

        this.actual = actual;

    }

    public boolean isLoggable(Level lvl) {
        return actual.isLoggable(lvl);
    }

    @Override
    public void log(LogRecord var1) {
        actual.log(var1);
    }

}
