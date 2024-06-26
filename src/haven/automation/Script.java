package haven.automation;

import com.google.common.flogger.FluentLogger;
import haven.UI;
import haven.Widget;

import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

public abstract class Script extends Thread {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    public static class Message {
        public final Widget sender;
        public final String msg;
        public final Object[] args;

        public Message(final Widget sender, final String msg, final Object... args) {
            this.sender = sender;
            this.msg = msg;
            this.args = args;
        }
    }

    private final long sid;
    private final long start;
    public SessionDetails session;

    private final Queue<Message> msgs = new LinkedList<>();
    private Pattern subjectfilter;
    private boolean listening;
    private boolean allowExternalMsgs;

    private boolean intp;

    public Script(final long id, final SessionDetails session) {
        super("Script Thread Sid [" + id + "]");
        this.sid = id;
        this.session = session;

        this.start = System.currentTimeMillis();
        this.listening = false;
        this.intp = false;
    }

    public double time() {
        return (System.currentTimeMillis() - start) / 1000.0;
    }

    long sid() {
        return sid;
    }

    public abstract String name();

    @Override
    public String toString() {
        return String.format("%s [%d]", name(), sid);
    }

    @Override
    public void interrupt() {
        intp = true;
        super.interrupt();
    }

    private boolean intp() {
        return intp;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // Some basic scripting API
    //////////////////////////////////////////////////////////////////////////////////////////////

    public static Script myself() {
        return (Script) Thread.currentThread();
    }

    public static void checkintp() throws InterruptedException {
        if (((Script) Thread.currentThread()).intp()) {
            throw new InterruptedException();
        }
    }

    public static void wdgmsg(final Widget sender, final String msg, final Object[] args) {
        sender.wdgmsg(msg, args);
    }

    public long gettime() {
        return System.currentTimeMillis();
    }


    /* Messaging system *************************************************************************/
    public void listen(final String filter, final boolean allowexternal) {
        listening = true;
        subjectfilter = Pattern.compile(filter);
        allowExternalMsgs = allowexternal;
    }

    public boolean allowExternal() {
        return allowExternalMsgs;
    }

    public void stopListening() {
        listening = false;
    }

    public void clearmsgs() {
        synchronized (msgs) {
            msgs.clear();
        }
    }

    public boolean hasmsg() {
        synchronized (msgs) {
            return msgs.size() > 0;
        }
    }

    public Message pollmsg() {
        synchronized (msgs) {
            return msgs.poll();
        }
    }

    void newmsg(final Widget sender, final String msg, final Object... args) {
        if (listening && subjectfilter.matcher(msg).find()) {
            synchronized (msgs) {
                msgs.offer(new Message(sender, msg, args));
            }
        }
    }
    /* *****************************************************************************************/

    /* Logs*********************************************************************************/
    public void log(final String msg) {
        logger.atInfo().log("Script [%s] [sid %d] [start %d] %s", name(), sid, start, msg);
    }
    /* *****************************************************************************************/


    public abstract void script_run() throws Throwable;

    @Override
    public void run() {
        try {
            script_run();

            final UI ui = session.getUI();
            if (ui != null && ui.gui != null) {
                if (intp) {
                    ui.message("Script Interrupted -> " + name() + " [" + sid + "]");
                } else {
                    ui.message("Finished Script -> " + name() + " [" + sid + "]");
                }
            }
        } catch (Throwable t) {
            final UI ui = session.getUI();
            if (ui != null && ui.gui != null) {
                if (intp) {
                    ui.message("Script Interrupted -> data/scripts/" + name() + " [" + sid + "]");
                } else {
                    ui.message("Script died -> data/scripts/" + name() + " [" + sid + "]");
                    try {
                        logger.atSevere().withCause(t).log("Script died [%s] [sid %d] [start %d]", name(), sid, start);
                    } catch (Exception e) {
                        //Ignore
                    }
                    logger.atSevere().withCause(t).log("Script %s [%d] died, review logs", name(), sid);
                }
            }
        }
    }
}
