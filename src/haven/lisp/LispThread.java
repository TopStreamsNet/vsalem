package haven.lisp;
import haven.Config;
import haven.HackThread;
import haven.UI;
import haven.pathfinder.PFListener;
import haven.pathfinder.Pathfinder;
import org.armedbear.lisp.*;

import java.awt.*;


public class LispThread extends HackThread implements PFListener {

    public boolean navigating = false;

    public LispThread(String name){
        super(name);
        setDaemon(true);
    }

    public void run(){
        this.navigating = false;
        if (UI.instance.gui != null) UI.instance.gui.syslog.append("Running LISP", Color.BLUE);
        Interpreter interpreter = Interpreter.getInstance();
        if (interpreter == null){
            String[] args = {"--noinform --batch"};
            interpreter = Interpreter.createDefaultInstance(args);
        }
        interpreter.eval("(load \""+ Config.aiscript +"\")");
        if (UI.instance.gui != null) UI.instance.gui.syslog.append("Done with LISP", Color.BLUE);
    }

    @Override
    public void pfDone(Pathfinder thread) {
        this.navigating = false;
    }
}
