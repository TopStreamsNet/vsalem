/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.automation.DBworks;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class UI {
	public static UI instance;
	public GameUI gui;
	public RootWidget root;
	private Widget keygrab, mousegrab;
	public Map<Integer, Widget> widgets = new TreeMap<Integer, Widget>();
	public Map<Widget, Integer> rwidgets = new HashMap<Widget, Integer>();
	Receiver rcvr;
	public Coord mc = Coord.z, lcc = Coord.z;
	public Session sess;
	public boolean modshift, modctrl, modmeta, modsuper;
	public Object lasttip;
	long lastevent, lasttick;
	public Widget mouseon;
	public Console cons = new WidgetConsole();
	private Collection<AfterDraw> afterdraws = new LinkedList<AfterDraw>();
	private Collection<AfterDraw> afterttdraws = new LinkedList<AfterDraw>();
	public MenuGrid mnu;
	public final ActAudio audio = new ActAudio();

	// vSalem
	public DBworks db = Config.usesqlite ? new DBworks() : null;
	private long lastactivity = 0;

	{
		lastevent = lasttick = System.currentTimeMillis();
	}

	public interface Receiver {
		public void rcvmsg(int widget, String msg, Object... args);
	}

	public interface Runner {
		public Session run(UI ui) throws InterruptedException;
	}

	public interface AfterDraw {
		public void draw(GOut g);
	}

	private class WidgetConsole extends Console {
		{
			setcmd("q", new Command() {
				public void run(Console cons, String[] args) {
					HackThread.tg().interrupt();
				}
			});
			setcmd("lo", new Command() {
				public void run(Console cons, String[] args) {
					sess.close();
				}
			});
			setcmd("where", new Command() {
				public void run(Console cons, String[] args) {
					Coord plc = UI.instance.gui.map.player().rc;
					Coord pltc = plc.div(11.0f);
					MCache.Grid plgrid = UI.instance.sess.glob.map.getgridt(pltc);

					UI.instance.gui.syslog.append("Coordinate: "+plc+ "Grid ID: "+plgrid.id+" Tile:"+pltc.sub(plgrid.ul),Color.CYAN);
				}
			});
			setcmd("grids", new Command() {
				public void run(Console cons, String[] args) {
					UI.instance.sess.glob.map.grids.forEach((Coord a, MCache.Grid b) -> {System.out.println(a+" "+b.id);});
				}
			});
			setcmd("windows", new Command() {
				public void run(Console cons, String[] args) {
					UI.instance.gui.listWindows();
				}
			});
			setcmd("widgets", new Command() {
				public void run(Console cons, String[] args) {
					UI.instance.gui.listWidgets();
				}
			});
			setcmd("tiles", new Command() {
				public void run(Console cons, String[] args) {
					UI.instance.sess.glob.map.printTiles();
				}
			});
			setcmd("secret", new Command() {
				public void run(Console cons, String[] args) {
					SecretWindow.instance(UI.instance).toggle();
				}
			});
			setcmd("ghit", new Command() {
				public void run(Console cons, String[] args) {
					UI.instance.gui.map.glob.gobhitmap.debug();
				}
			});
			setcmd("gogo", new Command() {
				public void run(Console cons, String[] args) {
					String script;
					if(args.length == 2) {
						script = args[1];
					}else{
						script = Config.aiscript;
					}
					haven.automation.Utils.launchLispScript(script,UI.instance.sess.details);
				}
			});
			setcmd("stop", new Command() {
				public void run(Console cons, String[] args) {
					haven.automation.Utils.interruptLispScript();
				}
			});
			setcmd("test", new Command() {
				public void run(Console cons, String[] args) {
					System.out.println("cam "+UI.instance.gui.map.camera.angle());

				}
			});
			setcmd("hookah", new Command() {
				public void run(Console cons, String[] args) {
					Gob hookah = null;
					Gob player = gui.map.player();
					synchronized (gui.map.glob.oc) {
						for (Gob gob : gui.map.glob.oc) {
							if (gob.resname().get().startsWith("gfx/terobjs/hookah") && (hookah == null || (gob.rc.dist(player.rc)) < hookah.rc.dist(player.rc))){
								hookah = gob;
							}
						}
					}
					if(hookah != null) {
						Gob finalHookah = hookah;
						new Thread() {
							public void run() {
								UI.instance.gui.syslog.append("Found hookah", Color.CYAN);
								UI.instance.gui.map.wdgmsg("click", Coord.z, finalHookah.rc, 1, 0);
								UI.instance.gui.syslog.append("Waiting to move to hookah", Color.CYAN);
								Moving m = player.getattr(Moving.class);
								while (m == null) {
									m = player.getattr(Moving.class);
								}
								UI.instance.gui.syslog.append("Moving to hookah", Color.CYAN);
								m = player.getattr(Moving.class);
								while (m != null) {
									m = player.getattr(Moving.class);
								}
								UI.instance.gui.syslog.append("Standing by the hookah", Color.CYAN);
								UI.instance.gui.map.wdgmsg("click", finalHookah.sc, finalHookah.rc, 3, 0, 0, (int) finalHookah.id, finalHookah.rc, 0, -1);
								UI.instance.gui.syslog.append("Waiting for flowermenu", Color.CYAN);
								Set<FlowerMenu> sfm = UI.instance.root.children(FlowerMenu.class);
								while(sfm.isEmpty()){
									sfm = UI.instance.root.children(FlowerMenu.class);
								}
								UI.instance.gui.syslog.append("Got flower menu!", Color.CYAN);
								for (FlowerMenu fm: sfm) {
									for(FlowerMenu.Petal pt : fm.opts){
										if(pt.name.startsWith("Puff")){
											UI.instance.gui.syslog.append("Start puff "+(new Timestamp(System.currentTimeMillis())), Color.CYAN);
											fm.choose(pt);
											try{
											TimeUnit.SECONDS.sleep(19);}catch(Exception ignored){}
											UI.instance.gui.syslog.append("End puff "+(new Timestamp(System.currentTimeMillis())), Color.CYAN);
											UI.instance.gui.map.wdgmsg("click", Coord.z, finalHookah.rc, 1, 0);
											return;
										}else{
											UI.instance.gui.syslog.append("Petal:"+pt.name, Color.RED);
										}
									}
								}

							}
						}.start();
					}

				}
			});
		}

		private void findcmds(Map<String, Command> map, Widget wdg) {
			if(wdg instanceof Directory) {
				Map<String, Command> cmds = ((Directory)wdg).findcmds();
				synchronized(cmds) {
					map.putAll(cmds);
				}
			}
			for(Widget ch = wdg.child; ch != null; ch = ch.next)
				findcmds(map, ch);
		}

		public Map<String, Command> findcmds() {
			Map<String, Command> ret = super.findcmds();
			findcmds(ret, root);
			return(ret);
		}
	}

	@SuppressWarnings("serial")
	public static class UIException extends RuntimeException {
		public String mname;
		public Object[] args;

		public UIException(String message, String mname, Object... args) {
			super(message);
			this.mname = mname;
			this.args = args;
		}
	}

	public UI(Coord sz, Session sess) {
		UI.instance = this;
		root = new RootWidget(this, sz);
		widgets.put(0, root);
		rwidgets.put(root, 0);
		this.sess = sess;
	}

	public void setreceiver(Receiver rcvr) {
		this.rcvr = rcvr;
	}

	public void bind(Widget w, int id) {
		widgets.put(id, w);
		rwidgets.put(w, id);
		w.binded();
	}

	public void drawafter(AfterDraw ad) {
		synchronized(afterdraws) {
			afterdraws.add(ad);
		}
	}

	public void drawaftertt(AfterDraw ad) {
		synchronized(afterttdraws) {
			afterttdraws.add(ad);
		}
	}

	public void lastdraw(GOut g) {
		synchronized(afterttdraws) {
			for(AfterDraw ad : afterttdraws)
				ad.draw(g);
			afterttdraws.clear();
		}
	}

	public void tick() {
		long now = System.currentTimeMillis();
		root.tick((now - lasttick) / 1000.0);
		lasttick = now;
	}

	public void draw(GOut g) {
		root.draw(g);
		synchronized(afterdraws) {
			for(AfterDraw ad : afterdraws)
				ad.draw(g);
			afterdraws.clear();
		}
	}

	public void newwidget(int id, String type, int parent, Object[] pargs, Object... cargs) throws InterruptedException {
		synchronized(this) {
			Widget pwdg = widgets.get(parent);
			if(pwdg == null)
				throw(new UIException("Null parent widget " + parent + " for " + id, type, cargs));
			Widget wdg = pwdg.makechild(type.intern(), pargs, cargs);
			bind(wdg, id);

			if(type.equals("gameui")){
				if(Config.alwaystrack){
					String[] as = {"tracking"};
					wdgmsg(wdg, "act", (Object[])as);
				}
			}
		}
	}

	public void grabmouse(Widget wdg) {
		mousegrab = wdg;
	}

	public void grabkeys(Widget wdg) {
		keygrab = wdg;
	}

	private void removeid(Widget wdg) {
		wdg.removed();
		if(rwidgets.containsKey(wdg)) {
			int id = rwidgets.get(wdg);
			widgets.remove(id);
			rwidgets.remove(wdg);
		}
		for(Widget child = wdg.child; child != null; child = child.next)
			removeid(child);
	}

	public void destroy(Widget wdg) {
		if((mousegrab != null) && mousegrab.hasparent(wdg))
			mousegrab = null;
		if((keygrab != null) && keygrab.hasparent(wdg))
			keygrab = null;
		removeid(wdg);
		wdg.reqdestroy();
	}

	public void destroy(int id) {
		synchronized(this) {
			if(widgets.containsKey(id)) {
				Widget wdg = widgets.get(id);
//                System.out.println("Destroying widget of type "+wdg.getClass().getName());
				destroy(wdg);
				if(wdg == gui)
				{
					this.sess.glob.purge();
					this.gui = null;
					this.cons.clearout();
					this.mnu = null;
				}
			}
		}
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
		int id;
		if(Config.debug) {
			int i = 0;
			try {
				for (Object obj : args) {
					if (!sender.toString().contains("Camera"))
						System.out.println("WDG Sender : " + sender + " msg = " + msg + " arg " + i + " : " + obj);
					i++;
				}
			} catch (ArrayIndexOutOfBoundsException ignored) {
			}
		}
		synchronized(this) {
			if(!rwidgets.containsKey(sender))
			{
				System.err.print("Wdgmsg sender (" + sender.getClass().getName() + ") is not in rwidgets");
				return;
			}
			id = rwidgets.get(sender);
		}
		if(rcvr != null)
			rcvr.rcvmsg(id, msg, args);
	}

	public void uimsg(int id, String msg, Object... args) {
		synchronized(this) {
			Widget wdg = widgets.get(id);
			if(Config.debug) {
				int i = 0;
				try {
					for (Object obj : args) {
						//if (!sender.toString().contains("Camera"))
						System.out.println("UI Receiver : " + wdg + " msg = " + msg + " arg " + i + " : " + obj);
						i++;
					}
				} catch (ArrayIndexOutOfBoundsException ignored) {
				}
			}
			if(wdg != null)
				wdg.uimsg(msg.intern(), args);
			else
				throw(new UIException("Uimsg to non-existent widget " + id, msg, args));
		}
	}

	private void setmods(InputEvent ev) {
		int mod = ev.getModifiersEx();
		Debug.kf1 = modshift = (mod & InputEvent.SHIFT_DOWN_MASK) != 0;
		Debug.kf2 = modctrl = (mod & InputEvent.CTRL_DOWN_MASK) != 0;
		Debug.kf3 = modmeta = (mod & (InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) != 0;
	/*
	Debug.kf4 = modsuper = (mod & InputEvent.SUPER_DOWN_MASK) != 0;
	*/
	}
	private int kcode = 0;
	public void type(KeyEvent ev) {
		be_active();
		setmods(ev);
		ev.setKeyCode(kcode);
		if(keygrab == null) {
			if(!root.type(ev.getKeyChar(), ev))
				root.globtype(ev.getKeyChar(), ev);
		} else {
			keygrab.type(ev.getKeyChar(), ev);
		}
	}

	public void keydown(KeyEvent ev) {
		setmods(ev);
		kcode = ev.getKeyCode();
		if(keygrab == null) {
			if(!root.keydown(ev))
				root.globtype((char)0, ev);
		} else {
			keygrab.keydown(ev);
		}
	}

	public void keyup(KeyEvent ev) {
		setmods(ev);
		kcode = 0;
		if(keygrab == null)
			root.keyup(ev);
		else
			keygrab.keyup(ev);
	}

	private Coord wdgxlate(Coord c, Widget wdg) {
		return(c.add(wdg.c.inv()).add(wdg.parent.rootpos().inv()));
	}

	public boolean dropthing(Widget w, Coord c, Object thing) {
		if(w instanceof DropTarget) {
			if(((DropTarget)w).dropthing(c, thing))
				return(true);
		}
		for(Widget wdg = w.lchild; wdg != null; wdg = wdg.prev) {
			Coord cc = w.xlate(wdg.c, true);
			if(c.isect(cc, wdg.sz)) {
				if(dropthing(wdg, c.add(cc.inv()), thing))
					return(true);
			}
		}
		return(false);
	}

	public long timesinceactive() {
		return System.currentTimeMillis() - lastactivity;
	}

	public void be_active() {
		lastactivity = System.currentTimeMillis();
	}

	public void mousedown(MouseEvent ev, Coord c, int button) {
		be_active();

		setmods(ev);
		lcc = mc = c;
		if(mousegrab == null)
			root.mousedown(c, button);
		else
			mousegrab.mousedown(wdgxlate(c, mousegrab), button);
	}

	public void mouseup(MouseEvent ev, Coord c, int button) {
		setmods(ev);
		mc = c;
		if(mousegrab == null)
			root.mouseup(c, button);
		else
			mousegrab.mouseup(wdgxlate(c, mousegrab), button);
	}

	public void mousemove(MouseEvent ev, Coord c) {
		setmods(ev);
		mc = c;
		if(mousegrab == null)
			root.mousemove(c);
		else
			mousegrab.mousemove(wdgxlate(c, mousegrab));
	}

	public void mousewheel(MouseEvent ev, Coord c, int amount) {
		this.setmods(ev);
		this.lcc = this.mc = c;
		if (this.mousegrab != null && !(this.mousegrab instanceof ItemDrag)) {
			this.mousegrab.mousewheel(this.wdgxlate(c, this.mousegrab), amount);
		} else {
			this.root.mousewheel(c, amount);
		}
	}

	public int modflags() {
		return((modshift?1:0) |
				(modctrl?2:0) |
				(modmeta?4:0) |
				(modsuper?8:0));
	}

	public void message(String str){
		this.message(str, Color.WHITE);
	}

	public void message(String str, GameUI.MsgType type) {
		if((cons!=null) && (gui!=null)){
			gui.message(str, type);
		}
	}

	public void message(String str, Color msgColor) {
		if((cons!=null) && (gui!=null)){
			gui.message(str, msgColor);
		}
	}

	public static boolean isCursor(String name) {
		return instance != null && instance.root != null && instance.root.cursor.name.equals(name);
	}

	public void destroy() {
		audio.clear();
	}

	public static class Cursor {
		public static final String SIFTING = "gfx/hud/curs/sft";
		public static final String GOBBLE = "gfx/hud/curs/eat";
	}
}
