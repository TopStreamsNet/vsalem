package haven.lisp;
import haven.HackThread;
import haven.UI;
import org.armedbear.lisp.*;

import java.awt.*;


public class LispThread extends HackThread {
    public LispThread(String name){
        super(name);
        setDaemon(true);
    }

    public void run(){
        if(UI.instance.gui != null)UI.instance.gui.syslog.append("Running LISP", Color.BLUE);
        Interpreter interpreter = Interpreter.getInstance();
        if (interpreter == null){
            String args[] = {"--noinform --batch"};
            interpreter = Interpreter.createDefaultInstance(args);
        }
        interpreter.eval("(load \"salem.lisp\")");
        if(UI.instance.gui != null)UI.instance.gui.syslog.append("Done with LISP", Color.BLUE);
    }
}
