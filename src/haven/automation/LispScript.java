package haven.automation;


import com.google.common.flogger.FluentLogger;
import haven.Config;
import org.armedbear.lisp.Interpreter;
import org.armedbear.lisp.Load;

@SuppressWarnings("unused")
public class LispScript extends Script {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private static final Interpreter engine = Interpreter.createInstance();

    public static void reloadConfig() {
        Load.load(Config.userhome+"/scripts/lib/_config.lisp");
    }

    private final String script;

    LispScript(String script, final long id, SessionDetails session) {
        super(id, session);
        this.script = script;
    }

    @Override
    public String name() {
        return script;
    }

    @Override
    public void script_run() {
        if(this.script.startsWith("repl")) {
            LispScript.engine.eval("(load \""+Config.userhome+"/scripts/lib/_config.lisp"+"\")");
            LispScript.engine.eval("(defpackage :salem-repl (:use :salem-config :common-lisp :cl-user :java))");
            LispScript.engine.eval("(in-package :salem-repl)");
            LispScript.engine.run();
        }else {
            Load.load(String.format("%s/scripts/%s", Config.userhome, script));
        }
    }
}
