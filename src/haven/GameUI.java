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

import haven.Gob.Overlay;
import haven.res.lib.HomeTrackerFX;
import org.ender.timer.TimerController;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;
import java.util.List;

import static haven.Inventory.invsq;
import static haven.Inventory.isqsz;

public class GameUI extends ConsoleHost implements Console.Directory {
	public final String chrid;
	private static final int fkeys[] = {KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
			KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
			KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12};
	private static final int nkeys[] = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
			KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8,
			KeyEvent.VK_9, KeyEvent.VK_0, KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS};
	public final long plid;
	public final EquipProxyWdg equipProxy;
	public MenuGrid menu;
	public CraftWnd craftwnd;
	public Tempers tm;
	public Widget gobble;
	public MapView map;
	public LocalMiniMap mmap;
	public Fightview fv;
	public static final Text.Foundry errfoundry = new Text.Foundry(MainFrame.uiConfig.getFontConfig("gameUIerror")); // vSalem Change Font - ???
	private Text lasterr;
	private long errtime;
	public InvWindow invwnd;
	private Window equwnd, makewnd;
	public Inventory maininv;
	public WeightWdg weightwdg;
	public MainMenu mainmenu;
	public BuddyWnd buddies;
	public CharWnd chrwdg;
	public Polity polity;
	public HelpWnd help;
	public OptWnd opts;
	public Store storewnd;
	public Collection<GItem> hand = new LinkedList<GItem>();
	public WItem vhand;
	public ChatUI chat;
	public FilterWnd filter = new FilterWnd(this);
	public FlatnessTool flat;
	public ChatUI.Channel syslog;
	private HomeTrackerFX.HTrackWdg hrtptr;
	public int prog = -1;
	private boolean afk = false;
	@SuppressWarnings("unchecked")
	public Indir<Resource>[] belt = new Indir[144];
	public Indir<Resource> lblk, dblk;
	//    Belt beltwdg;
	public String polowner;

	// vSalem
	public boolean drinkingTea, lastDrinkingSucessful;
	public Thread DrinkThread;
	private long  DrinkTimer = 0;


	private List<Class<? extends Widget> > filterout = new ArrayList<Class<? extends Widget> >();
	public int weight;

	public abstract class Belt extends Widget {
		public Belt(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent);
		}

		public void keyact(final int slot) {
			if(map != null) {
				Coord mvc = map.rootxlate(ui.mc);
				if(mvc.isect(Coord.z, map.sz)) {
					map.delay(map.new Hittest(mvc) {
						protected void hit(Coord pc, Coord mc, MapView.ClickInfo inf) {
							if(inf == null)
								GameUI.this.wdgmsg("belt", slot, 1, ui.modflags(), mc);
							else
								GameUI.this.wdgmsg("belt", slot, 1, ui.modflags(), mc, (int)inf.gob.id, inf.gob.rc);
						}

						protected void nohit(Coord pc) {
							GameUI.this.wdgmsg("belt", slot, 1, ui.modflags());
						}
					});
				}
			}
		}
	}

	@RName("gameui")
	public static class $_ implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			String chrid = (String)args[0];
			int plid = (Integer)args[1];
			return(new GameUI(parent, chrid, plid));
		}
	}

	public GameUI(Widget parent, String chrid, long plid) {
		super(Coord.z, parent.sz, parent);
		ui.gui = this;
		this.chrid = chrid;
		this.plid = plid;
		setcanfocus(true);
		setfocusctl(true);
		menu = new MenuGrid(Coord.z, this);
		new SeasonImg(new Coord(2,2), Avaview.dasz, this);
		new Bufflist(new Coord(80, 60), this);
		equipProxy = new EquipProxyWdg(new Coord(80, 2), new int[]{6, 7, 9, 14, 5, 4}, this);
		tm = new Tempers(Coord.z, this);
		chat = new ChatUI(Coord.z, 0, this);
		syslog = new ChatUI.Log(chat, "System");
		ui.cons.out = new java.io.PrintWriter(new java.io.Writer() {
			StringBuilder buf = new StringBuilder();

			public void write(char[] src, int off, int len) {
				buf.append(src, off, len);
				int p;
				while((p = buf.indexOf("\n")) >= 0) {
					syslog.append(buf.substring(0, p), Color.WHITE);
					buf.delete(0, p + 1);
				}
			}

			public void close() {}
			public void flush() {}
		});
		opts = new OptWnd(sz.sub(200, 200).div(2), this);
		opts.hide();
		TimerController.init(Config.server);
		makemenu();
		resize(sz);
		updateRenderFilter();
	}

	public static class MenuButton extends IButton {
		private final int gkey;
		private long flash;
		private Tex glowmask;

		MenuButton(Coord c, Widget parent, String base, int gkey, String tooltip) {
			super(c, parent, Resource.loadimg("gfx/hud/" + base + "up"), Resource.loadimg("gfx/hud/" + base + "down"));
			this.tooltip = Text.render(tooltip);
			this.gkey = (char)gkey;
		}

		public void click() {}
		protected void toggle(){
			BufferedImage sel = up;
			BufferedImage img = down;

			hover = up = img;
			down = sel;
		}
		public boolean globtype(char key, KeyEvent ev) {
			if((gkey != -1) && (key == gkey)) {
				click();
				return(true);
			}
			return(super.globtype(key, ev));
		}

		public void draw(GOut g) {
			super.draw(g);
			if(flash > 0) {
				if(glowmask == null)
					glowmask = new TexI(PUtils.glowmask(PUtils.glowmask(up.getRaster()), 10, new Color(192, 255, 64)));
				g = g.reclipl(new Coord(-10, -10), g.sz.add(20, 20));
				double ph = (System.currentTimeMillis() - flash) / 1000.0;
				g.chcolor(255, 255, 255, (int)(128 * ((Math.cos(ph * Math.PI * 2) * -0.5) + 0.5)));
				g.image(glowmask, Coord.z);
				g.chcolor();
			}
		}

		public void flash(boolean f) {
			if(f) {
				if(flash == 0)
					flash = System.currentTimeMillis();
			} else {
				flash = 0;
			}
		}
	}

	public static class MenuButtonT extends MenuButton{

		MenuButtonT(Coord c, Widget parent, String base, int gkey, String tooltip) {
			super(c, parent, base, gkey, tooltip);
			hover = down;
		}
		@Override
		protected void toggle() {
			BufferedImage img = up;

			up = hover;
			hover = img;
			down = img;
		}

	}

	static class Hidewnd extends Window {
		Hidewnd(Coord c, Coord sz, Widget parent, String cap) {
			super(c, sz, parent, cap);
		}

		public void wdgmsg(Widget sender, String msg, Object... args) {
			if((sender == this) && msg.equals("close")) {
				this.hide();
				return;
			}
			super.wdgmsg(sender, msg, args);
		}
	}

	public static class InvWindow extends Hidewnd {
		public final Map<Inventory, String> names = new HashMap<Inventory, String>();
		private Label[] labels = new Label[0];
		private final GameUI wui;
		private final Label wlbl;

		@RName("invwnd")
		public static class $_ implements Factory {
			public Widget create(Coord c, Widget parent, Object[] args) {
				String cap = (String)args[0];
				return(new InvWindow(c, new Coord(100, 100), parent, cap, null));
			}
		}

		public InvWindow(Coord c, Coord sz, Widget parent, String cap, GameUI wui) {
			super(c, sz, parent, cap);
			if((this.wui = wui) != null) {
				wlbl = new Label(Coord.z, this, "");
				updweight();
			} else {
				wlbl = null;
			}
		}

		private void updweight() {
			int weight = wui.weight;
			int nr = 0;
			if(wui.maininv != null)
				nr = wui.maininv.wmap.size();
			int cap = 25000;
			Glob.CAttr ca = ui.sess.glob.cattr.get("carry");
			if(ca != null)
				cap = ca.comp;
			wlbl.settext(String.format("Carrying %.2f/%.2f kg (%d in inventory)", weight / 1000.0, cap / 1000.0, nr));
			wlbl.setcolor((weight > cap)?Color.RED:Color.WHITE);
		}

		private void repack() {
			for(Label lbl : labels) {
				if(lbl != null)
					lbl.destroy();
			}

			int mw = 0;
			for(Inventory inv : names.keySet())
				mw = Math.max(mw, inv.sz.x);

			List<String> cn = new ArrayList<String>();
			for(String nm : names.values()) {
				if(!cn.contains(nm))
					cn.add(nm);
			}
			Collections.sort(cn);

			Label[] nl = new Label[cn.size()];
			int n = 0, y = 0;
			for(String nm : cn) {
				if(!nm.equals("")) {
					nl[n] = new Label(new Coord(0, y), this, nm);
					y = nl[n].c.y + nl[n].sz.y + 5;
				}
				int x = 0;
				int mh = 0;
				for(Map.Entry<Inventory, String> e : names.entrySet()) {
					if(e.getValue().equals(nm)) {
						Inventory inv = e.getKey();
						if((x > 0) && ((x + inv.sz.x) > mw)) {
							x = 0;
							y += mh + 5;
							mh = 0;
						}
						inv.c = new Coord(x, y);
						mh = Math.max(mh, inv.sz.y);
						x += inv.sz.x + 5;
					}
				}
				y += mh + 5;
				n++;
			}
			if(wlbl != null)
				wlbl.c = new Coord(0, y);
			this.labels = nl;
			pack();
		}

		public Widget makechild(String type, Object[] pargs, Object[] cargs) {
			String nm;
			if(pargs.length > 0)
				nm = (String)pargs[0];
			else
				nm = "";
			Inventory inv = (Inventory)gettype(type).create(Coord.z, this, cargs);
			names.put(inv, nm);
			repack();
			return(inv);
		}

		public void cdestroy(Widget w) {
			if((w instanceof Inventory) && names.containsKey(w)) {
				Inventory inv = (Inventory)w;
				names.remove(inv);
				repack();
			}
		}

		public void cresize(Widget w) {
			if((w instanceof Inventory) && names.containsKey(w))
				repack();
		}
	}

	private void updhand() {
		if((hand.isEmpty() && (vhand != null)) || ((vhand != null) && !hand.contains(vhand.item))) {
			ui.destroy(vhand);
			vhand = null;
			ui.sess.details.removeHeldItem();
		}
		if(!hand.isEmpty() && (vhand == null)) {
			GItem fi = hand.iterator().next();
			vhand = new ItemDrag(new Coord(15, 15), this, fi);
			ui.sess.details.attachHeldItem(vhand.item);
		}
	}

	public Widget makechild(String type, Object[] pargs, Object[] cargs) {
		String place = ((String)pargs[0]).intern();
		if(place == "mapview") {
			Coord cc = (Coord)cargs[0];
			map = new MapView(Coord.z, sz, this, cc, plid);
			map.lower();
			if(mmap != null){
				ui.destroy(mmap);
			}

			if(Config.pclaimv) map.enol(0,1);
			if(Config.tclaimv) map.enol(2,3);
			if(Config.wclaimv) map.enol(4);

			this.updateRenderFilter();

			mmap = new LocalMiniMap(new Coord(GameUI.this.sz.x-250, 15), new Coord(146,146), this, map);

			return(map);
		} else if(place == "fight") {
			fv = (Fightview)gettype(type).create(new Coord(sz.x - Fightview.width, 0), this, cargs);
			return(fv);
		} else if(place == "inv") {
			String nm = (pargs.length > 1)?((String)pargs[1]):null;
			if(invwnd == null) {
				invwnd = new InvWindow(new Coord(100, 100), Coord.z, this, "Inventory", this);
				invwnd.hide();
			}
			if(nm == null) {
				Inventory inv = (Inventory)invwnd.makechild(type, new Object[0], cargs);
				maininv = inv;
				weightwdg = new WeightWdg(new Coord(10, 100), this);
				return(inv);
			} else {
				return(invwnd.makechild(type, new Object[] {nm}, cargs));
			}
		} else if(place == "equ") {
			equwnd = new Hidewnd(new Coord(400, 10), Coord.z, this, "Equipment");
			Widget equ = gettype(type).create(Coord.z, equwnd, cargs);
			equwnd.pack();
			equwnd.hide();
			return(equ);
		} else if(place == "hand") {
			GItem g = (GItem)gettype(type).create((Coord)pargs[1], this, cargs);
			hand.add(g);
			updhand();
			return(g);
		} else if(place == "craft") {
			final Widget[] mk = {null};
			showCraftWnd();
			if(craftwnd != null){
				mk[0] = gettype(type).create(new Coord(215, 250), craftwnd, cargs);
				craftwnd.setMakewindow(mk[0]);
				return (mk[0]);
			} else {
				makewnd = new Window(new Coord(350, 100), Coord.z, this, "Crafting") {
					public void wdgmsg(Widget sender, String msg, Object... args) {
						if((sender == this) && msg.equals("close")) {
							mk[0].wdgmsg("close");
							return;
						}
						super.wdgmsg(sender, msg, args);
					}
					public void cdestroy(Widget w) {
						if(w == mk[0]) {
							ui.destroy(this);
							makewnd = null;
						}
					}
				};
				mk[0] = gettype(type).create(Coord.z, makewnd, cargs);
				makewnd.pack();
				return (mk[0]);
			}
		} else if(place == "buddy") {
			buddies = (BuddyWnd)gettype(type).create(new Coord(187, 50), this, cargs);
			buddies.hide();
			return(buddies);
		} else if(place == "pol") {
			polity = (Polity)gettype(type).create(new Coord(500, 50), this, cargs);
			polity.hide();
			return(polity);
		} else if(place == "chr") {
			chrwdg = (CharWnd)gettype(type).create(new Coord(100, 50), this, cargs);
			chrwdg.hide();
			fixattrview(chrwdg);
			return(chrwdg);
		} else if(place == "chat") {
			return(chat.makechild(type, new Object[] {}, cargs));
		} else if(place == "party") {
			return(gettype(type).create(new Coord(2, 80), this, cargs));
		} else if(place == "misc") {
			if(type.contains("ui/hrtptr")){
				if(hrtptr != null)
				{
					hrtptr.dispose();
					hrtptr = null;
				}
				hrtptr = new HomeTrackerFX.HTrackWdg(this, gettype(type).create((Coord)pargs[1], this, cargs));
				return hrtptr;
			}
			return(gettype(type).create((Coord)pargs[1], this, cargs));
		} else {
			throw(new UI.UIException("Illegal gameui child", type, pargs));
		}
	}

	public Equipory getEquipory(){
		if(equwnd != null){
			for(Widget wdg = equwnd.child; wdg != null; wdg = wdg.next){
				if(wdg instanceof Equipory){
					return (Equipory) wdg;
				}
			}
		}
		return null;
	}

	public void cdestroy(Widget w) {
		if((w instanceof GItem) && hand.contains(w)) {
			hand.remove(w);
			updhand();
		} else if(w == polity) {
			polity = null;
		} else if(w == chrwdg) {
			chrwdg = null;
			attrview.destroy();
		}
	}

	public void destroy() {
		super.destroy();
		OptWnd2.close();
		TimerPanel.close();
		DarknessWnd.close();
		FlatnessTool.close();
		LocatorTool.close();
		OverviewTool.close();
		HotkeyListWindow.close();
		SecretWindow.close();
		WikiBrowser.close();
		if (menu!= null) menu.destroy();
		if (tm!= null) tm.destroy();
		if (gobble!= null) gobble.destroy();
		if (map!= null) map.destroy();
		if (mmap!= null) mmap.destroy();
		if (fv!= null) fv.destroy();
		if (invwnd!= null) invwnd.destroy();
		if (equwnd!= null) equwnd.destroy();
		if (makewnd!= null) makewnd.destroy();
		if (maininv!= null) maininv.destroy();
		if (mainmenu!= null) mainmenu.destroy();
		if (buddies!= null) buddies.destroy();
		if (chrwdg!= null) chrwdg.destroy();
		if (polity!= null) polity.destroy();
		if (help!= null) help.destroy();
		if (chat!= null) chat.destroy();
		if (syslog!= null) syslog.destroy();
		if (hrtptr!= null) hrtptr.destroy();
	}

	private Widget attrview;
	private void fixattrview(final CharWnd cw) {
		final IBox box = new IBox(Window.fbox.ctl, Tex.empty, Window.fbox.cbl, Tex.empty,
				Window.fbox.bl, Tex.empty, Window.fbox.bt, Window.fbox.bb);
		CharWnd.Attr a = (CharWnd.Attr)cw.attrwdgs.child;
		final Coord moff = new Coord(20, 0);
		attrview = new Widget(Coord.z, new Coord(a.expsz.x, cw.attrwdgs.sz.y).add(moff).add(10, Window.cbtni[0].getHeight() + 10).add(box.bisz()), this) {
			boolean act = false;
			Label la;
			int cmod = 0;
			{
				Widget cbtn = new IButton(Coord.z, this, Window.cbtni[0], Window.cbtni[1], Window.cbtni[2]) {
					public void click() {
						act(false);
					}
				};
				cbtn.c = new Coord(sz.x - cbtn.sz.x, box.bt.sz().y);
				int y = cbtn.c.y + cbtn.sz.y;

				cbtn = new IButton(Coord.z, this, Window.rbtni[0], Window.rbtni[1], Window.rbtni[2]) {
					public void click() {
						togglecw();
					}
				};
				cbtn.c = new Coord(sz.x - Window.cbtni[0].getWidth() - cbtn.sz.x - 2, box.bt.sz().y);

				la = new Label(box.btloff(), this, "LA: ");

				Coord ctl = box.btloff().add(5, 5);
				for(CharWnd.Attr a = (CharWnd.Attr)cw.attrwdgs.child; a != null; a = (CharWnd.Attr)a.next) {
					final CharWnd.Attr ca = a;
					new Widget(ctl.add(0, y), a.expsz.add(moff), this) {
						public void draw(GOut g) {
							g.image(ca.res.layer(Resource.imgc).tex(), Coord.z);
							ca.drawmeter(g, moff, ca.expsz);
						}
						@Override
						public boolean mousedown(Coord c, int button) {
							boolean res = ca.mousedown(c.add(ca.expc), button);
							ui.grabmouse(this);
							return res;
						}
						@Override
						public boolean mouseup(Coord c, int button) {
							ui.grabmouse(null);
							return ca.mouseup(c.add(ca.expc), button);
						}
					};
					y += 20;
				}
			}

			public void draw(GOut g) {
				if(cmod != cw.tmexp){
					cmod = cw.tmexp;
					la.settext(String.format("Insp: %d", cmod));
				}
				if((fv != null) && !fv.lsrel.isEmpty())
					return;
				g.chcolor(0, 0, 0, 128);
				g.frect(box.btloff(), sz.sub(box.bisz()));
				g.chcolor();
				super.draw(g);
				box.draw(g, Coord.z, sz);
			}

			public void presize() {
				c = new Coord(GameUI.this.sz.x - sz.x, (menu.c.y - sz.y) / 2);
			}

			public boolean show(boolean show) {
				return(super.show(show && act));
			}

			private void act(boolean act) {
				Utils.setprefb("attrview", this.act = act);
				show(act);
			}

			{
				cw.addtwdg(new IButton(Coord.z, cw, Window.rbtni[0], Window.rbtni[1], Window.rbtni[2]) {
					public void click() {
						act(true);
						cw.hide();
					}
				});
				presize();
				act(Utils.getprefb("attrview", false));
			}
		};
	}

	private void togglecw() {
		if(chrwdg != null) {
			if(chrwdg.show(!chrwdg.visible)) {
				chrwdg.raise();
				fitwdg(chrwdg);
				setfocus(chrwdg);
			}
			attrview.show(!chrwdg.visible);
		}
	}

	static Text.Furnace progf = new PUtils.BlurFurn(new Text.Foundry(new java.awt.Font("Terminus", java.awt.Font.BOLD, 10)).aa(true), 2, 1, new Color(0, 16, 16)); // vSalem Change Font - ???
	Text progt = null;
	public void updateRenderFilter()
	{
		filterout = new ArrayList<Class<? extends Widget> >();
		if(Config.hide_minimap)
		{
			filterout.add(LocalMiniMap.class);
		}
		if(Config.hide_tempers)
		{
			filterout.add(Tempers.class);
			this.tm.hide();
		}
		else
		{
			this.tm.show();
		}
	}
	private void drawFiltered(GOut g)//TODO: this is ugly!
	{
		Widget next;

		for(Widget wdg = child; wdg != null; wdg = next) {
			next = wdg.next;
			if(!wdg.visible || filterout.contains(wdg.getClass()))
				continue;
			Coord cc = xlate(wdg.c, true);
			GOut g2;
			g2 = g.reclip(cc, wdg.sz);
			wdg.draw(g2);
		}
	}
	public void draw(GOut g) {
//	boolean beltp = !chat.expanded;
//	beltwdg.show(beltp);
//	super.draw(g);
		//here, we'd like to only draw the mapview child
		drawFiltered(g);
		if(prog >= 0) {
			String progs = String.format("%d%%", prog);
			if((progt == null) || !progs.equals(progt.text))
				progt = progf.render(progs);
			g.aimage(progt.tex(), new Coord(sz.x / 2, (sz.y * 4) / 10), 0.5, 0.5);
		}
		int by = sz.y;
		if(Config.chat_expanded)
			by = Math.min(by, chat.c.y);
//	if(beltwdg.visible)
//	    by = Math.min(by, beltwdg.c.y);
		int bx = mainmenu.sz.x + 10;
		if(cmdline != null) {
			drawcmd(g, new Coord(bx, by -= 20));
		} else if(lasterr != null) {
			if((System.currentTimeMillis() - errtime) > 3000) {
				lasterr = null;
			} else {
				g.chcolor(0, 0, 0, 192);
				g.frect(new Coord(bx - 2, by - 22), lasterr.sz().add(4, 4));
				g.chcolor();
				g.image(lasterr.tex(), new Coord(bx, by -= 20));
			}
		}
		if(!Config.chat_expanded) {
			chat.drawsmall(g, new Coord(bx, by), 50);
		}
	}

	public void tick(double dt) {
		super.tick(dt);
		String need = null;
		if(!drinkingTea && Config.autodrink && (DrinkThread == null || !DrinkThread.isAlive()) && ((need = needToDrinkTea())!=null) ) {
			if(System.currentTimeMillis() - DrinkTimer >= Config.autodrinktime * 1000) {
				DrinkTimer = System.currentTimeMillis();
				new Thread(new DrinkTea(this, need)).start();
			}
		}
		dwalkupd();
	}

	public void uimsg(String msg, Object... args) {
		if(msg == "err") {
			String err = (String)args[0];
			error(err);
		} else if(msg == "prog") {
			if(args.length > 0)
				prog = (Integer)args[0];
			else
				prog = -1;
		} else if(msg == "setbelt") {
			int slot = (Integer)args[0];
			if(args.length < 2) {
				belt[slot] = null;
			} else {
				belt[slot] = ui.sess.getres((Integer)args[1]);
			}
		} else if(msg == "ins") {
			tm.updinsanity((Integer)args[0]);
		} else if(msg == "stm") {
			int[] n = new int[4];
			for(int i = 0; i < 4; i++)
				n[i] = (Integer)args[i];
			tm.upds(n);
		} else if(msg == "htm") {
			int[] n = new int[4];
			for(int i = 0; i < 4; i++)
				n[i] = (Integer)args[i];
			tm.updh(n);
		} else if(msg == "gavail") {
			tm.gavail = (Integer)args[0] != 0;
		} else if(msg == "cravail") {
			if(args[0] == null)
				tm.cravail(null);
			else
				tm.cravail(ui.sess.getres((Integer)args[0]));
		} else if(msg == "gobble") {
			boolean g = (Integer)args[0] != 0;
			if(g && (gobble == null)) {
        boolean old = args.length < 2 || (Integer)args[1] == 0;
				tm.hide();
				gobble = old ? new OldGobble(Coord.z, this) : new Gobble(Coord.z, this);
				resize(sz);
			} else if(!g && (gobble != null)) {
				ui.destroy(gobble);
				gobble = null;
				tm.show();
			}
		} else if(Gobble.msgs.contains(msg)) {
      gobble.uimsg(msg, args);
		} else if(msg == "polowner") {
			String o = (String)args[0];
			boolean n = ((Integer)args[1]) != 0;
			if(o.length() == 0)
				o = null;
			else
				o = o.intern();
			if(o != polowner) {
				if(map != null) {
					if(o == null) {
						if(polowner != null)
							map.setpoltext("Leaving " + polowner);
					} else {
						map.setpoltext("Entering " + o);
					}
				}
				polowner = o;
			}
		} else if(msg == "dblk") {
			int id = (Integer)args[0];
			dblk = (id < 0)?null:(ui.sess.getres(id));
		} else if(msg == "lblk") {
			int id = (Integer)args[0];
			lblk = (id < 0)?null:(ui.sess.getres(id));
		} else if(msg == "showhelp") {
			Indir<Resource> res = ui.sess.getres((Integer)args[0]);
			if(help == null)
				help = new HelpWnd(sz.div(2).sub(150, 200), this, res);
			else
				help.res = res;
		} else if(msg == "weight") {
			weight = (Integer)args[0];
			if(invwnd != null)
				invwnd.updweight();
			if(weightwdg != null){
				weightwdg.update(weight);
				OverviewTool.instance(ui).force_update();
			}
		} else {
			super.uimsg(msg, args);
		}
	}

	@Override
	public void wdgmsg(String msg, Object... args) {
		super.wdgmsg(msg, args);
		if(msg.equals("belt")){
			checkBelt(args);
		}
	}

	private void checkBelt(Object... args) {
		int index = (Integer) args[0];
		Indir<Resource> indir = belt[index];
		if (indir != null){
			try{
				Resource res = indir.get();
				if(menu.isCrafting(res)){
					showCraftWnd();
				}
				if(craftwnd != null) {
					craftwnd.select(res,false);
				}
			}
			catch(Loading l){}
		}
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
		if(sender == menu) {
			wdgmsg(msg, args);
			return;
		} else if((sender == buddies) && (msg == "close")) {
			buddies.hide();
		} else if((sender == polity) && (msg == "close")) {
			polity.hide();
		} else if((sender == chrwdg) && (msg == "close")) {
			chrwdg.hide();
		} else if((sender == help) && (msg == "close")) {
			ui.destroy(help);
			help = null;
			return;
		} else if((sender == storewnd) && (msg == "close")) {
			storewnd.hide();
			return;
		}
		super.wdgmsg(sender, msg, args);
	}

	private void fitwdg(Widget wdg) {
		wdg.render_c = new Coord(wdg.c);
		if(wdg.render_c.x < 0)
			wdg.render_c.x = 0;
		if(wdg.render_c.y < 0)
			wdg.render_c.y = 0;
		if(wdg.render_c.x + wdg.sz.x > sz.x)
			wdg.render_c.x = sz.x - wdg.sz.x;
		if(wdg.render_c.y + wdg.sz.y > sz.y)
			wdg.render_c.y = sz.y - wdg.sz.y;
		wdg.c = wdg.render_c;
	}

	/* Directional walking. Apparently AWT send repeated keyup/keydown
	 * events on key autorepeat (:-/), so hysteresis elimination of
	 * some kind is necessary. This variant waits 100 ms before
	 * accepting a keyup event. */
	private boolean dwalking = false;
	private Coord dwalkang = new Coord();
	private long dwalkhys;
	private float dwalkbase;
	private boolean[] dkeys = {false, false, false, false};

	private void dwalkupd() {
		Coord a = new Coord();
		if(dkeys[0]) a = a.add(1, 0);
		if(dkeys[1]) a = a.add(0, 1);
		if(dkeys[2]) a = a.add(-1, 0);
		if(dkeys[3]) a = a.add(0, -1);
		long now = System.currentTimeMillis();
		if(!a.equals(dwalkang) && (now > dwalkhys)) {
			if((a.x == 0) && (a.y == 0)) {
				wdgmsg("dwalk");
			} else {
				float da = dwalkbase + (float)a.angle(Coord.z);
				wdgmsg("dwalk", (int)((da / (Math.PI * 2)) * 1000));
			}
			dwalkang = a;
		}
	}

	private int dwalkkey(int key) {
		if(key == KeyEvent.VK_W)
			return(0);
		else if(key == KeyEvent.VK_D)
			return(1);
		else if(key == KeyEvent.VK_S)
			return(2);
		else if(key == KeyEvent.VK_A)
			return(3);
		throw(new Error());
	}

	private void dwalkdown(KeyEvent ev) {
		if(!dwalking) {
			if (MapView.ffThread > 0) {
				MapView.signalToStop = true;
			}
			dwalking = true;
			dwalkbase = -map.camera.angle();
			ui.grabkeys(this);
		}
		int k = dwalkkey(ev.getKeyCode());
		dkeys[k] = true;
		dwalkhys = ev.getWhen();
	}

	private void dwalkup(KeyEvent ev) {
		int k = dwalkkey(ev.getKeyCode());
		dkeys[k] = false;
		dwalkhys = ev.getWhen() + 100;
		if(!dkeys[0] && !dkeys[1] && !dkeys[2] && !dkeys[3]) {
			dwalking = false;
			ui.grabkeys(null);
		}
	}

	private static final Tex menubg = Resource.loadtex("gfx/hud/menubg");
	private static final Tex menubgfull = Resource.loadtex("gfx/hud/menubgfull");

	public class MainMenu extends Widget {
		public final MenuButton invb, equb, chrb, budb, polb, optb;
		public final MenuButton clab, towb, warb, ptrb, lndb, chatb;//hwab
		public boolean hpv = true, pv = hpv && !Config.hptr;

		boolean full = Config.mainmenu_full;
		public MenuButton[] tohide = {
				invb = new MenuButton(new Coord(4, 8), this, "inv", 9, "Inventory (Tab)") {
					int seq = 0;
					@Override
					public void click() {
						if((invwnd != null) && invwnd.show(!invwnd.visible)) {
							invwnd.raise();
							fitwdg(invwnd);
						}
					}

					@Override
					public void tick(double dt) {
						if(maininv != null) {
							if(invwnd.visible) {
								seq = maininv.newseq;
								flash(false);
							} else if(maininv.newseq != seq) {
								flash(true);
							}
						}
					}
				},
				equb = new MenuButton(new Coord(62, 8), this, "equ", 5, "Equipment (Ctrl+E)") {
					public void click() {
						if((equwnd != null) && equwnd.show(!equwnd.visible)) {
							equwnd.raise();
							fitwdg(equwnd);
						}
					}
				},
				chrb = new MenuButton(new Coord(120, 8), this, "chr", 20, "Studying (Ctrl+T)") {
					public void click() {
						togglecw();
					}
					public void tick(double dt) {
						if((chrwdg != null) && chrwdg.skavail)
							flash(true);
						else
							flash(false);
					}
				},
				budb = new MenuButton(new Coord(4, 66), this, "bud", 2, "Buddy List (Ctrl+B)") {
					public void click() {
						if((buddies != null) && buddies.show(!buddies.visible)) {
							buddies.raise();
							fitwdg(buddies);
							setfocus(buddies);
						}
					}
				},
				polb = new MenuButton(new Coord(62, 66), this, "pol", 16, "Town (Ctrl+P)") {
					final Tex gray = Resource.loadtex("gfx/hud/polgray");

					public void draw(GOut g) {
						if(polity == null)
							g.image(gray, Coord.z);
						else
							super.draw(g);
					}

					public void click() {
						if((polity != null) && polity.show(!polity.visible)) {
							polity.raise();
							fitwdg(polity);
							setfocus(polity);
						}
					}
				},
				optb = new MenuButton(new Coord(120, 66), this, "opt", 15, "Options (Ctrl+O)") {
					public void click() {
						OptWnd2.toggle();
//			if(opts.show(!opts.visible)) {
//			    opts.raise();
//			    fitwdg(opts);
//			    setfocus(opts);
//			}
					}
				}
		};
		public IButton cash, manual;

		public MainMenu(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent);

			int y = sz.y - 21;
			int x = 6;
			clab = new MenuButtonT(new Coord(x, y), this, "cla", -1, "Display personal claims") {
				public void click() {
					if(!map.visol(0))
						map.enol(0, 1);
					else
						map.disol(0, 1);
					toggle();
					Config.pclaimv = !Config.pclaimv;
					Utils.setprefb("pclaimv", Config.pclaimv);
				}
			};
			if(Config.pclaimv) clab.toggle();
			clab.render();
			x+=18;
			towb = new MenuButtonT(new Coord(x, y), this, "tow", -1, "Display town claims") {
				public void click() {
					if(!map.visol(2))
						map.enol(2, 3);
					else
						map.disol(2, 3);
					toggle();
					Config.tclaimv = !Config.tclaimv;
					Utils.setprefb("tclaimv",  Config.tclaimv);
				}
			};
			if(Config.tclaimv) towb.toggle();
			towb.render();
			x+=18;
			warb = new MenuButtonT(new Coord(x, y), this, "war", -1, "Display waste claims") {
				public void click() {
					if(!map.visol(4))
						map.enol(4);
					else
						map.disol(4);
					toggle();
					Config.wclaimv = !Config.wclaimv;
					Utils.setprefb("wclaimv", Config.wclaimv);
				}
			};
			if(Config.wclaimv) warb.toggle();
			warb.render();
			x+=18;
			ptrb = new MenuButton(new Coord(x, y), this, "ptr", -1, "Display homestead pointer") {
				public void click() {
					Config.hpointv = !Config.hpointv;
					Utils.setprefb("hpointv", Config.hpointv);
					pv = Config.hpointv && !Config.hptr;
				}
			};
			pv = Config.hpointv && !Config.hptr;
			x+=18;
//            hwab = new MenuButton(new Coord(x,y), this, "hwa", -1, "Walk to your homestead") {
//                public void click() {
//                    GameUI.this.homesteadWalk();
//                }
//            };
//	    x+=12;
			new MenuButton(new Coord(x, y), this, "height", -1, "Display heightmap") {
				{
					hover = down;
					down = Resource.loadimg("gfx/hud/heighthl");
				}
				public void click() {
					mmap.toggleHeight();
					toggle();
				}
				@Override
				protected void toggle() {
					BufferedImage img = up;

					up = hover;
					hover = down;
					down = img;
				}

			};
			x+=18;
			lndb = new MenuButton(new Coord(x, y), this, "lnd", -1, "Display Landscape Tool") {
				public void click() {
					FlatnessTool.instance(GameUI.this.ui).toggle();
				}
			};
			x+=18;
			chatb = new MenuButton(new Coord(x, y), this, "chat", 3, "Chat (Ctrl+C)") {
				public void click() {
					chat.toggle();
				}
			};
			new MenuButton(new Coord(this.sz.x - 22, y), this, "gear", -1, "Menu") {
				{
					recthit = true;
					hover = Resource.loadimg("gfx/hud/gearhl");
				}
				public void click() {
					mainmenu.toggle();
					toggle();
				}
				@Override
				protected void toggle() {
					BufferedImage img = up;

					up = down;
					down = img;
				}
			};
		}

		@Override
		public void draw(GOut g) {
			g.image(Config.mainmenu_full?menubgfull:menubg, Coord.z);
			super.draw(g);
		}

		public void toggle() {
			Utils.setprefb("mainmenu_full", Config.mainmenu_full=!Config.mainmenu_full);
			apply_visibility();
		}
		public void apply_visibility() {
			for (Widget w: tohide){
				w.visible = Config.mainmenu_full;
			}
			if(cash != null)
				cash.presize();
			if(manual != null)
				manual.presize();
		}

	}

	public void showCraftWnd() {
		showCraftWnd(false);
	}

	public void showCraftWnd(boolean force) {
		if(craftwnd == null && (force || Config.autoopen_craftwnd)){
			new CraftWnd(Coord.z, this);
		}
	}

	public void toggleCraftWnd() {
		if(craftwnd == null) {
			showCraftWnd(true);
		} else {
			craftwnd.wdgmsg(craftwnd, "close");
		}
	}

	public void toggleFilterWnd() {
		filter.show(!filter.visible);
	}

	private void makemenu() {
		mainmenu = new MainMenu(new Coord(0, sz.y - menubg.sz().y), menubg.sz(), this);

		new Widget(Coord.z, isqsz.add(Window.swbox.bisz()), this) {
			private final Tex none = Resource.loadtex("gfx/hud/blknone");
			private Tex mono;
			private Indir<Resource> monores;

			{
				tooltip = Text.render("Toggle maneuver (Ctrl+S)");
			}

			public void draw(GOut g) {
				try {
					if(lblk != null) {
						g.image(lblk.get().layer(Resource.imgc).tex(), Window.swbox.btloff());
					} else if(dblk != null) {
						if(monores != dblk) {
							if(mono != null) mono.dispose();
							mono = new TexI(PUtils.monochromize(dblk.get().layer(Resource.imgc).img, new Color(128, 128, 128)));
							monores = dblk;
						}
						g.image(mono, Window.swbox.btloff());
					} else {
						g.image(none, Window.swbox.btloff());
					}
				} catch(Loading e) {
				}
				g.chcolor(133, 92, 62, 255);
				Window.swbox.draw(g, Coord.z, sz);
				g.chcolor();
			}

			public void presize() {
				this.c = menu.c.add(menu.sz.x, 0).sub(this.sz);
			}

			public boolean globtype(char key, KeyEvent ev) {
				if(key == 19) {
					act("blk");
					return(true);
				}
				return(super.globtype(key, ev));
			}

			public boolean mousedown(Coord c, int btn) {
				act("blk");
				return(true);
			}
		}.presize();
		if((Config.manualurl != null) && (WebBrowser.self != null)) {
      IButton manual = new IButton(new Coord(150, 0), this, Resource.loadimg("gfx/hud/manu"), Resource.loadimg("gfx/hud/mand"), Resource.loadimg("gfx/hud/manh")) {
				{
					tooltip = Text.render("Open Manual");
				}

				public void click() {
					URL base = Config.manualurl;
					try {
						WebBrowser.self.show(base);
					} catch(WebBrowser.BrowserException e) {
						error("Could not launch web browser.");
					}
				}

				public void presize() {
          this.c = mainmenu.c.sub(0, this.sz.y).add(50, 0);
				}

				public Object tooltip(Coord c, Widget prev) {
					if(checkhit(c))
						return(super.tooltip(c, prev));
					return(null);
				}
			};
			manual.presize();
			mainmenu.manual = manual;
		}

		if((Config.storebase != null) && (WebBrowser.self != null)) {
			IButton cash = new IButton(Coord.z, this, Resource.loadimg("gfx/hud/cashu"), Resource.loadimg("gfx/hud/cashd"), Resource.loadimg("gfx/hud/cashh")) {
				{
					tooltip = Text.render("Salem Store");
				}

				public void click() {
					if(storewnd == null) {
						storewnd = new Store(Coord.z, GameUI.this, Config.storebase);
						storewnd.hide();
						storewnd.c = storewnd.parent.sz.sub(storewnd.sz).div(2);
					}
					if(storewnd.show(!storewnd.visible)) {
						storewnd.raise();
						fitwdg(storewnd);
						GameUI.this.setfocus(storewnd);
					}
				}

				public void presize() {
					this.c = mainmenu.c.sub(0, this.sz.y);
				}

				public Object tooltip(Coord c, Widget prev) {
					if(checkhit(c))
						return(super.tooltip(c, prev));
					return(null);
				}
			};
			cash.presize();
			mainmenu.cash = cash;
		}
		if(mainmenu.manual != null || mainmenu.cash != null)
			mainmenu.apply_visibility();
	}

	public boolean globtype(char key, KeyEvent ev) {
		int keyCode = ev.getKeyCode();
		if(key == ':') {
			entercmd();
			return(true);
		} else if((Config.screenurl != null) && (keyCode == KeyEvent.VK_S) && ((ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0)) {
			Screenshooter.take(this, Config.screenurl);
			return(true);
		} else if(!ev.isControlDown() && ((keyCode == KeyEvent.VK_W) || (keyCode == KeyEvent.VK_A) || (keyCode == KeyEvent.VK_S) || (keyCode == KeyEvent.VK_D))) {
			dwalkdown(ev);
			return(true);
		} else if (!ev.isShiftDown() && keyCode == KeyEvent.VK_Q && ev.getID() == KeyEvent.KEY_TYPED)  {
			Thread t = new Thread(new PickForageable(this), "PickForageable");
			t.start();
			return(true);
		} else if (!ev.isShiftDown() && keyCode == KeyEvent.VK_B && ev.getID() == KeyEvent.KEY_TYPED)  {
			Config.showboundingboxes = !Config.showboundingboxes;
			return(true);
		}
		return(super.globtype(key, ev));
	}

	public boolean keydown(KeyEvent ev) {
		int key = ev.getKeyCode();
		if(dwalking && (!ev.isControlDown() && ((key == KeyEvent.VK_W) || (key == KeyEvent.VK_A) || (key == KeyEvent.VK_S) || (key == KeyEvent.VK_D)))) {
			dwalkdown(ev);
			return(true);
		}
		return(super.keydown(ev));
	}

	public boolean keyup(KeyEvent ev) {
		int key = ev.getKeyCode();
		if(dwalking && (!ev.isControlDown() && ((key == KeyEvent.VK_W) || (key == KeyEvent.VK_A) || (key == KeyEvent.VK_S) || (key == KeyEvent.VK_D)))) {
			dwalkup(ev);
			return(true);
		}
		return(super.keyup(ev));
	}

	public boolean mousedown(Coord c, int button) {
		return(super.mousedown(c, button));
	}

	public void resize(Coord sz) {
		this.sz = sz;
		menu.c = sz.sub(menu.sz);
		tm.c = new Coord((sz.x - tm.sz.x) / 2, 0);
		chat.move(new Coord(mainmenu.sz.x, sz.y));
		chat.resize(sz.x - chat.c.x - menu.sz.x);
		if(gobble != null)
			gobble.c = new Coord((sz.x - gobble.sz.x) / 2, 0);
		if(map != null)
			map.resize(sz);
		if(fv != null)
			fv.c = new Coord(sz.x - Fightview.width, 0);
		mainmenu.c = new Coord(0, sz.y - mainmenu.sz.y);
//	beltwdg.c = new Coord(mainmenu.sz.x + 10, sz.y - beltwdg.sz.y);
		for(Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if(wdg.visible)
				fitwdg(wdg);
		}
		super.resize(sz);
	}

	public void presize() {
		resize(parent.sz);
	}

	public void error(String msg) {
		message(msg, MsgType.ERROR);
	}

	private static final Resource errsfx = Resource.load("sfx/error");
	public void message(String msg, MsgType type) {
		message(msg, getMsgColor(type));
	}

	public void message(String msg, Color msgColor) {
		errtime = System.currentTimeMillis();
		lasterr = errfoundry.render(msg, msgColor);
		syslog.append(msg, msgColor);
		Audio.play(errsfx);
	}

	public void message(String msg) {
		message(msg, Color.WHITE);
	}

	public static Color getMsgColor(MsgType type)
	{
		switch (type){
			case INFO:
				return  Color.CYAN;
			case GOOD:
				return  Color.GREEN;
			case BAD:
				return  Color.RED;
			case ERROR:
				return  Color.RED;
		}
		return Color.WHITE;
	}

	public static enum MsgType{
		INFO, GOOD, BAD, ERROR
	}

	public void act(String... args) {
		wdgmsg("act", (Object[])args);
	}

	public void act(int mods, Coord mc, Gob gob, String... args) {
		int n = args.length;
		Object[] al = new Object[n];
		System.arraycopy(args, 0, al, 0, n);
		if(mc != null) {
			al = Utils.extend(al, al.length + 2);
			al[n++] = mods;
			al[n++] = mc;
			if(gob != null) {
				al = Utils.extend(al, al.length + 2);
				al[n++] = (int)gob.id;
				al[n++] = gob.rc;
			}
		}
		wdgmsg("act", al);
	}

	public void listWindows(){
		UI.instance.gui.syslog.append("Windows: ", Color.RED);
		for (Widget w = lchild; w != null; w = w.prev) {
			if (w instanceof Window) {
				Window wnd = (Window) w;
				if (wnd.cap != null)
					UI.instance.gui.syslog.append(wnd.cap.text, Color.ORANGE);
			}
		}
	}

	public void listWidgets(){
		UI.instance.gui.syslog.append("Widgets: ", Color.RED);
		for (Map.Entry<Integer, Widget> entry: UI.instance.widgets.entrySet()) {
			UI.instance.gui.syslog.append("" + entry.getValue(), Color.ORANGE);
		}

	}

	public Window getwnd(String cap) {
		for (Widget w = lchild; w != null; w = w.prev) {
			if (w instanceof Window) {
				Window wnd = (Window) w;
				if (wnd.cap != null && cap.equals(wnd.cap.text))
					return wnd;
			}
		}
		return null;
	}

	private static final int WND_WAIT_SLEEP = 8;
	public Window waitfForWnd(String cap, int timeout) {
		int t  = 0;
		while (t < timeout) {
			Window wnd = getwnd(cap);
			if (wnd != null)
				return wnd;
			t += WND_WAIT_SLEEP;
			try {
				Thread.sleep(WND_WAIT_SLEEP);
			} catch (InterruptedException e) {
				return null;
			}
		}
		return null;
	}

	public class FKeyBelt extends Belt implements DTarget, DropTarget {
		public final int beltkeys[] = {KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
				KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
				KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12};
		public int curbelt = 0;

		public FKeyBelt(Coord c, Widget parent) {
			super(c, Inventory.invsz(new Coord(12, 1)), parent);
		}

		private Coord beltc(int i) {
			return(Inventory.sqoff(new Coord(i, 0)));
		}

		private int beltslot(Coord c) {
			for(int i = 0; i < 12; i++) {
				if(c.isect(beltc(i), isqsz))
					return(i + (curbelt * 12));
			}
			return(-1);
		}

		public void draw(GOut g) {
			invsq(g, Coord.z, new Coord(12, 1));
			for(int i = 0; i < 12; i++) {
				int slot = i + (curbelt * 12);
				Coord c = beltc(i);
				try {
					Indir<Resource> ir = belt[slot];
					if(ir != null)
						g.image(ir.get().layer(Resource.imgc).tex(), c);
				} catch(Loading e) {}
				g.chcolor(156, 180, 158, 255);
				FastText.aprintf(g, c.add(isqsz), 1, 1, "F%d", i + 1);
				g.chcolor();
			}
		}

		public boolean mousedown(Coord c, int button) {
			int slot = beltslot(c);
			if(slot != -1) {
				if(button == 1)
					GameUI.this.wdgmsg("belt", slot, 1, ui.modflags());
				if(button == 3)
					GameUI.this.wdgmsg("setbelt", slot, 1);
				return(true);
			}
			return(false);
		}

		public boolean globtype(char key, KeyEvent ev) {
			if(key != 0)
				return(false);
			boolean M = (ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;
			for(int i = 0; i < beltkeys.length; i++) {
				if(ev.getKeyCode() == beltkeys[i]) {
					if(M) {
						curbelt = i;
						return(true);
					} else {
						keyact(i + (curbelt * 12));
						return(true);
					}
				}
			}
			return(false);
		}

		public boolean drop(Coord c, Coord ul) {
			int slot = beltslot(c);
			if(slot != -1) {
				GameUI.this.wdgmsg("setbelt", slot, 0);
				return(true);
			}
			return(false);
		}

		public boolean iteminteract(Coord c, Coord ul) {return(false);}

		public boolean dropthing(Coord c, Object thing) {
			int slot = beltslot(c);
			if(slot != -1) {
				if(thing instanceof Resource) {
					Resource res = (Resource)thing;
					if(res.layer(Resource.action) != null) {
						GameUI.this.wdgmsg("setbelt", slot, res.name);
						return(true);
					}
				}
			}
			return(false);
		}
	}

	public class NKeyBelt extends Belt implements DTarget, DropTarget {
		public int curbelt = 0;

		public NKeyBelt(Coord c, Widget parent) {
			super(c, Inventory.invsz(new Coord(10, 1)), parent);
		}

		private Coord beltc(int i) {
			return(Inventory.sqoff(new Coord(i, 0)));
		}

		private int beltslot(Coord c) {
			for(int i = 0; i < 10; i++) {
				if(c.isect(beltc(i), isqsz))
					return(i + (curbelt * 12));
			}
			return(-1);
		}

		public void draw(GOut g) {
			invsq(g, Coord.z, new Coord(10, 1));
			for(int i = 0; i < 10; i++) {
				int slot = i + (curbelt * 12);
				Coord c = beltc(i);
				try {
					Indir<Resource> ir = belt[slot];
					if(ir != null)
						g.image(ir.get().layer(Resource.imgc).tex(), c);
				} catch(Loading e) {}
				g.chcolor(156, 180, 158, 255);
				FastText.aprintf(g, c.add(isqsz), 1, 1, "%d", (i + 1) % 10);
				g.chcolor();
			}
		}

		public boolean mousedown(Coord c, int button) {
			int slot = beltslot(c);
			if(slot != -1) {
				if(button == 1)
					GameUI.this.wdgmsg("belt", slot, 1, ui.modflags());
				if(button == 3)
					GameUI.this.wdgmsg("setbelt", slot, 1);
				return(true);
			}
			return(false);
		}

		public boolean globtype(char key, KeyEvent ev) {
			if(key != 0)
				return(false);
			int c = ev.getKeyChar();
			if((c < KeyEvent.VK_0) || (c > KeyEvent.VK_9))
				return(false);
			int i = Utils.floormod(c - KeyEvent.VK_0 - 1, 10);
			boolean M = (ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;
			if(M) {
				curbelt = i;
			} else {
				keyact(i + (curbelt * 12));
			}
			return(true);
		}

		public boolean drop(Coord c, Coord ul) {
			int slot = beltslot(c);
			if(slot != -1) {
				GameUI.this.wdgmsg("setbelt", slot, 0);
				return(true);
			}
			return(false);
		}

		public boolean iteminteract(Coord c, Coord ul) {return(false);}

		public boolean dropthing(Coord c, Object thing) {
			int slot = beltslot(c);
			if(slot != -1) {
				if(thing instanceof Resource) {
					Resource res = (Resource)thing;
					if(res.layer(Resource.action) != null) {
						GameUI.this.wdgmsg("setbelt", slot, res.name);
						return(true);
					}
				}
			}
			return(false);
		}
	}

	{
		new ToolBeltWdg(this, "F-Belt", 0, fkeys);
		new ToolBeltWdg(this, "NumericBelt", 6, nkeys);
//	String val = Utils.getpref("belttype", "n");
//	if(val.equals("n")) {
//	    beltwdg = new NKeyBelt();
//	} else if(val.equals("f")) {
//	    beltwdg = new FKeyBelt();
//	} else {
//	    beltwdg = new NKeyBelt();
//	}
	}

	private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
	{
		cmdmap.put("afk", new Console.Command() {
			public void run(Console cons, String[] args) {
				afk = !afk;
				if (afk)
					wdgmsg("afk");
			}
		});
		cmdmap.put("act", new Console.Command() {
			public void run(Console cons, String[] args) {
				Object[] ad = new Object[args.length - 1];
				System.arraycopy(args, 1, ad, 0, ad.length);
				wdgmsg("act", ad);
			}
		});
	/*cmdmap.put("belt", new Console.Command() {
		public void run(Console cons, String[] args) {
		    if(args[1].equals("f")) {
			beltwdg.destroy();
			beltwdg = new FKeyBelt(Coord.z, GameUI.this);
			Utils.setpref("belttype", "f");
			resize(sz);
		    } else if(args[1].equals("n")) {
			beltwdg.destroy();
			beltwdg = new NKeyBelt(Coord.z, GameUI.this);
			Utils.setpref("belttype", "n");
			resize(sz);
		    }
		}
	    });*/
		cmdmap.put("tool", new Console.Command() {
			public void run(Console cons, String[] args) {
				gettype(args[1]).create(new Coord(200, 200), GameUI.this, new Object[0]);
			}
		});
		cmdmap.put("homestead", new Console.Command() {
			public void run(Console cons, String[] args) {
				GameUI.this.homesteadWalk();
			}
		});
		cmdmap.put("flatness", new Console.Command(){
			public void run(Console cons, String[] args){
				FlatnessTool.instance(GameUI.this.ui);
			}
		});
	}
	public Map<String, Console.Command> findcmds() {
		return(cmdmap);
	}

	private void homesteadWalk()
	{
		if(map.player() != null)
			for(Overlay ol : map.player().ols)
			{
				if(ol.spr.getClass().equals(HomeTrackerFX.class))
				{
					HomeTrackerFX htfx = (HomeTrackerFX) ol.spr;
					// walk there!
					ui.wdgmsg(map, "click", map.player().sc, htfx.c, 1,0);
				}
			}
	}

	private String needToDrinkTea(){
		synchronized(ui.sess.glob.buffs) {
			try{
				for(Buff b : ui.sess.glob.buffs.values()) {
					if((b.res.get().name.endsWith("/phlegmplus") ||
							b.res.get().name.endsWith("/bloodplus" ) ||
							b.res.get().name.endsWith("/ybileplus")  ||
							b.res.get().name.endsWith("/bbileplus")) && b.getCstate() < 0.1)
						return(b.res.get().name);
				}
			} catch(Loading e) {}
		}
		return(null);
	}
}
