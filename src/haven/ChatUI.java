/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Bj�rn Johannessen <johannessen.bjorn@gmail.com>
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

import static haven.Window.cbtni;

import java.util.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.text.*;
import java.text.AttributedCharacterIterator.Attribute;
import java.net.URL;
import java.util.regex.*;
import java.io.IOException;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatUI extends Widget {
	public static RichText.Foundry fnd = new RichText.Foundry(new ChatParser(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 20, TextAttribute.FOREGROUND, Color.WHITE)); // vSalem Change Font
	public static Text.Foundry qfnd = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 14), new Color(192, 255, 192));
	public static int selw = 100;
	public Channel sel = null, prevsel = null;
	private final Selector chansel;
	private Coord base;
	private static int basesize = 12;
	private QuickLine qline = null;
	private final LinkedList<Notification> notifs = new LinkedList<Notification>();
	private static final Pattern tags_patt = Pattern.compile("\\$(hl)\\[([^\\[\\]]*)\\]");

	public ChatUI(Coord c, int w, Widget parent) {
		super(c.add(0, -50), new Coord(w, 50), parent);
		chansel = new Selector(Coord.z, new Coord(selw, sz.y));
		chansel.hide();
		base = c;
		setfocusctl(true);
		setcanfocus(false);
		this.setbasesize((int) Utils.getpreff("chatfontsize", 12));
		update_visibility();
	}

	public final void setbasesize(int basesize) {
		ChatUI.fnd = new RichText.Foundry(new ChatParser(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, basesize, TextAttribute.FOREGROUND, Color.WHITE));
		ChatUI.qfnd = new Text.Foundry(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, basesize + 2), new java.awt.Color(192, 255, 192));
		ChatUI.selw = 100 + (basesize - 12) * 6;
		TextEntryChannel.efnd = new Text.Foundry(new Font("SansSerif", Font.PLAIN, basesize), Color.WHITE);

		this.c = this.base.add(0, -100 - (basesize - 12) * 24);
		this.resize(new Coord(this.sz.x, 100 + (basesize - 12) * 24));
		this.chansel.nf = new Text.Foundry("SansSerif", basesize);
		this.chansel.nfu = new Text.Foundry("SansSerif", basesize + 2, Font.BOLD);
		this.chansel.ymod = (basesize - 12) * 3;
		this.chansel.rerender = true;

		this.basesize = basesize;
	}

	public static boolean hasTags(String text) {
		return tags_patt.matcher(text).find();
	}

	private static Color lighter(Color col) {
		int hsl[] = new int[3];
		Utils.rgb2hsl(col.getRed(), col.getGreen(), col.getBlue(), hsl);
		hsl[1] = Math.round(0.7f * hsl[1]);
		hsl[2] = 100;
		int rgb[] = Utils.hsl2rgb(hsl);
		return new Color(rgb[0], rgb[1], rgb[2]);
	}

	public static class ChatAttribute extends Attribute {
		private ChatAttribute(String name) {
			super(name);
		}

		public static final Attribute HYPERLINK = new ChatAttribute("hyperlink");
	}

	public static class FuckMeGentlyWithAChainsaw {
		/* This wrapper class exists to work around the possibly most
		 * stupid Java bug ever (and that's saying a lot): That
		 * URL.equals and URL.hashCode do DNS lookups and
		 * block. Which, of course, not only sucks performance-wise
		 * but also breaks actual correct URL equality. */
		public final URL url;

		public FuckMeGentlyWithAChainsaw(URL url) {
			this.url = url;
		}
	}

	public static class ChatParser extends RichText.Parser {
		public static final Pattern urlpat = Pattern.compile("\\b((https?://)|(www\\.[a-z0-9_.-]+\\.[a-z0-9_.-]+))[a-z0-9/_.~#%+?&:*=-]*", Pattern.CASE_INSENSITIVE);
		public static final Map<? extends Attribute, ?> urlstyle = RichText.fillattrs(TextAttribute.FOREGROUND, new Color(64, 175, 255),
				TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON,
				TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);

		public ChatParser(Object... args) {
			super(args);
		}

		protected RichText.Part text(PState s, String text, Map<? extends Attribute, ?> attrs) throws IOException {
			RichText.Part ret = null;
			int p = 0;
			while (true) {
				Matcher m = urlpat.matcher(text);
				if (!m.find(p))
					break;
				URL url;
				try {
					String su = text.substring(m.start(), m.end());
					if (su.indexOf(':') < 0)
						su = "http://" + su;
					url = new URL(su);
				} catch (java.net.MalformedURLException e) {
					p = m.end();
					continue;
				}
				RichText.Part lead = new RichText.TextPart(text.substring(0, m.start()), attrs);
				if (ret == null) ret = lead;
				else ret.append(lead);
				Map<Attribute, Object> na = new HashMap<Attribute, Object>(attrs);
				na.putAll(urlstyle);
				na.put(ChatAttribute.HYPERLINK, new FuckMeGentlyWithAChainsaw(url));
				ret.append(new RichText.TextPart(text.substring(m.start(), m.end()), na));
				p = m.end();
			}
			if (ret == null)
				ret = new RichText.TextPart(text, attrs);
			else
				ret.append(new RichText.TextPart(text.substring(p), attrs));
			return (ret);
		}
	}

	public static abstract class Channel extends Widget {
		public final List<Message> msgs = new LinkedList<Message>() {
			@Override
			public boolean add(Message message) {
				if (size() >= 200) {
					removeFirst().tex().dispose();
				}
				return super.add(message);
			}
		};
		private final Scrollbar sb;
		private FileOutputStream fos = null;
		public IButton cbtn;
		protected boolean read = true;

		@Override
		public void show() {
			super.show();
			read = true;
		}

		public static abstract class Message {
			public final long time = System.currentTimeMillis();

			public abstract Text text();

			public abstract Tex tex();

			public abstract Coord sz();
		}

		public static class SimpleMessage extends Message {
			private final Text t;

			public SimpleMessage(String text, Color col, int w) {
				if (Config.timestamp)
					text = Utils.timestamp(text);
				if (col == null)
					this.t = fnd.render(RichText.Parser.quote(text), w);
				else
					this.t = fnd.render(RichText.Parser.quote(text), w, TextAttribute.FOREGROUND, col);
			}

			public Text text() {
				return (t);
			}

			public Tex tex() {
				return (t.tex());
			}

			public Coord sz() {
				return (t.sz());
			}
		}

		public Channel(Coord c, Coord sz, Widget parent, boolean closeable) {
			super(c, sz, parent);
			sb = new Scrollbar(new Coord(sz.x, 0), ih(), this, 0, -ih());
			if (closeable) {
				cbtn = new IButton(Coord.z, this, cbtni[0], cbtni[1], cbtni[2]);
				cbtn.recthit = true;
				cbtn.c = new Coord(sz.x - cbtn.sz.x - sb.sz.x - 3, 0);
			}
		}

		public Channel(Widget parent, boolean closeable) {
			this(new Coord(selw, 0), parent.sz.sub(selw, 0), parent, closeable);
		}

		protected final void startLogging() {
			if (Config.chatlogs) {
				try {
					String fixed_char_name = name().replaceAll("[\\/:*?\"<>|]", "");
					String path = String.format("%s/logs/%s/%s/", Config.userhome, Config.currentCharName, fixed_char_name);

					String filename = String.format("%s.txt", Utils.current_date());
					File pathf = new File(path);
					if (pathf.mkdirs() || pathf.isDirectory())//easiest code-wise, but not exactly efficient :) Only called once for all channels anyways
					{
						fos = new FileOutputStream(path + filename);
					} else {
						throw new FileNotFoundException("Failed to create parent directories!");
					}
				} catch (FileNotFoundException ex) {
					this.ui.message("Could not open file for chat logging!", GameUI.MsgType.INFO);
				}
			}
		}

		public void append(Message msg, boolean attn) {
			synchronized (msgs) {
				msgs.add(msg);
				int y = 0;
				for (Message m : msgs)
					y += m.sz().y;
				boolean b = sb.val >= sb.max;
				sb.max = y - ih();
				if (b)
					sb.val = sb.max;

				if (fos != null) {
					try {
						fos.write((msg.text().text + "\n").getBytes());
					} catch (IOException ex) {
						Logger.getLogger(ChatUI.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
				if (attn) {
					if (!visible)
						read = false;
				}
			}
		}

		public void append(String line, Color col) {
			append(new SimpleMessage(line, col, iw()), false);
		}

		public int iw() {
			return (sz.x - sb.sz.x);
		}

		public int ih() {
			return (sz.y);
		}

		public void draw(GOut g) {
			g.chcolor(24, 24, 16, 200);
			g.frect(Coord.z, sz);
			g.chcolor();
			int y = 0;
			boolean sel = false;
			synchronized (msgs) {
				for (Message msg : msgs) {
					if ((selstart != null) && (msg == selstart.msg))
						sel = true;
					int y1 = y - sb.val - (basesize - 10);
					int y2 = y1 + msg.sz().y;
					if ((y2 > 0) && (y1 < ih())) {
						if (sel)
							drawsel(g, msg, y1);
						g.image(msg.tex(), new Coord(0, y1));
					}
					if ((selend != null) && (msg == selend.msg))
						sel = false;
					y += msg.sz().y;
				}
			}
			sb.max = y - ih();
			super.draw(g);
		}

		public boolean mousewheel(Coord c, int amount) {
			sb.ch(amount * 15);
			return (true);
		}

		public void resize(Coord sz) {
			super.resize(sz);
			if (sb != null) {
				sb.resize(ih());
				sb.move(new Coord(sz.x, 0));
				int y = 0;
				for (Message m : msgs) {
					y += m.sz().y;
				}
				boolean b = sb.val >= sb.max;
				sb.max = y - ih();
				if (b)
					sb.val = sb.max;
			}
			if (cbtn != null)
				cbtn.c = new Coord(sz.x - cbtn.sz.x - (sb != null ? sb.sz.x : 0) - 3, 0);
		}

		public void notify(Message msg) {
			getparent(ChatUI.class).notify(this, msg);
		}

		public static class CharPos {
			public final Message msg;
			public final RichText.TextPart part;
			public final TextHitInfo ch;

			public CharPos(Message msg, RichText.TextPart part, TextHitInfo ch) {
				this.msg = msg;
				this.part = part;
				this.ch = ch;
			}

			public boolean equals(Object oo) {
				if (!(oo instanceof CharPos)) return (false);
				CharPos o = (CharPos) oo;
				return ((o.msg == this.msg) && (o.part == this.part) && o.ch.equals(this.ch));
			}
		}

		public final Comparator<CharPos> poscmp = new Comparator<CharPos>() {
			public int compare(CharPos a, CharPos b) {
				if (a.msg != b.msg) {
					synchronized (msgs) {
						for (Message msg : msgs) {
							if (msg == a.msg)
								return (-1);
							else if (msg == b.msg)
								return (1);
						}
					}
					throw (new IllegalStateException("CharPos message is no longer contained in the log"));
				} else if (a.part != b.part) {
					for (RichText.Part part = ((RichText) a.msg.text()).parts; part != null; part = part.next) {
						if (part == a.part)
							return (-1);
						else
							return (1);
					}
					throw (new IllegalStateException("CharPos is no longer contained in the log"));
				} else {
					return (a.ch.getInsertionIndex() - b.ch.getInsertionIndex());
				}
			}
		};

		public Message messageat(Coord c, Coord hc) {
			int y = -sb.val;
			synchronized (msgs) {
				for (Message msg : msgs) {
					Coord sz = msg.sz();
					if ((c.y >= y) && (c.y < y + sz.y)) {
						if (hc != null) {
							hc.x = c.x;
							hc.y = c.y - y;
						}
						return (msg);
					}
					y += sz.y;
				}
			}
			return (null);
		}

		public CharPos charat(Coord c) {
			if (c.y < -sb.val) {
				if (msgs.size() < 1)
					return (null);
				Message msg = msgs.get(0);
				if (!(msg.text() instanceof RichText))
					return (null);
				RichText.TextPart fp = null;
				for (RichText.Part part = ((RichText) msg.text()).parts; part != null; part = part.next) {
					if (part instanceof RichText.TextPart) {
						fp = (RichText.TextPart) part;
						break;
					}
				}
				if (fp == null)
					return (null);
				return (new CharPos(msg, fp, TextHitInfo.leading(0)));
			}

			Coord hc = new Coord();
			Message msg = messageat(c, hc);
			if ((msg == null) || !(msg.text() instanceof RichText))
				return (null);
			RichText rt = (RichText) msg.text();
			RichText.Part p = rt.partat(hc);
			if (p == null) {
				RichText.TextPart lp = null;
				for (RichText.Part part = ((RichText) msg.text()).parts; part != null; part = part.next) {
					if (part instanceof RichText.TextPart)
						lp = (RichText.TextPart) part;
				}
				if (lp == null) return (null);
				return (new CharPos(msg, lp, TextHitInfo.trailing(lp.end - lp.start - 1)));
			}
			if (!(p instanceof RichText.TextPart))
				return (null);
			RichText.TextPart tp = (RichText.TextPart) p;
			return (new CharPos(msg, tp, tp.charat(hc)));
		}

		private CharPos selorig, lasthit, selstart, selend;
		private boolean dragging;

		public boolean mousedown(Coord c, int btn) {
			if (super.mousedown(c, btn))
				return (true);
			if (btn == 1) {
				selstart = selend = null;
				CharPos ch = charat(c);
				if (ch != null) {
					selorig = lasthit = ch;
					dragging = false;
					ui.grabmouse(this);
				}
				return (true);
			}
			return (false);
		}

		public void mousemove(Coord c) {
			if (selorig != null) {
				CharPos ch = charat(c);
				if ((ch != null) && !ch.equals(lasthit)) {
					lasthit = ch;
					if (!dragging && !ch.equals(selorig))
						dragging = true;
					int o = poscmp.compare(selorig, ch);
					if (o < 0) {
						selstart = selorig;
						selend = ch;
					} else if (o > 0) {
						selstart = ch;
						selend = selorig;
					} else {
						selstart = selend = null;
					}
				}
			} else {
				super.mousemove(c);
			}
		}

		protected void selected(CharPos start, CharPos end) {
			StringBuilder buf = new StringBuilder();
			synchronized (msgs) {
				boolean sel = false;
				for (Message msg : msgs) {
					if (!(msg.text() instanceof RichText))
						continue;
					RichText rt = (RichText) msg.text();
					RichText.Part part = null;
					if (sel) {
						part = rt.parts;
					} else if (msg == start.msg) {
						sel = true;
						for (part = rt.parts; part != null; part = part.next) {
							if (part == start.part)
								break;
						}
					}
					if (sel) {
						for (; part != null; part = part.next) {
							if (!(part instanceof RichText.TextPart))
								continue;
							RichText.TextPart tp = (RichText.TextPart) part;
							CharacterIterator iter = tp.ti();
							int sch;
							if (tp == start.part)
								sch = tp.start + start.ch.getInsertionIndex();
							else
								sch = tp.start;
							int ech;
							if (tp == end.part)
								ech = tp.start + end.ch.getInsertionIndex();
							else
								ech = tp.end;
							for (int i = sch; i < ech; i++)
								buf.append(iter.setIndex(i));
							if (part == end.part) {
								sel = false;
								break;
							}
							buf.append(' ');
						}
						if (sel)
							buf.append('\n');
					}
					if (msg == end.msg)
						break;
				}
			}
			Clipboard cl;
			if ((cl = java.awt.Toolkit.getDefaultToolkit().getSystemSelection()) == null)
				cl = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
			try {
				final CharPos ownsel = selstart;
				cl.setContents(new StringSelection(buf.toString()),
						new ClipboardOwner() {
							public void lostOwnership(Clipboard cl, Transferable tr) {
								if (selstart == ownsel)
									selstart = selend = null;
							}
						});
			} catch (IllegalStateException e) {
			}
		}

		protected void clicked(CharPos pos) {
			AttributedCharacterIterator inf = pos.part.ti();

			//bugfix: invalidargumentexception - index out of bounds
			if (pos.ch.getCharIndex() < inf.getBeginIndex() || pos.ch.getCharIndex() >= inf.getEndIndex())
				return;

			inf.setIndex(pos.ch.getCharIndex());
			FuckMeGentlyWithAChainsaw url = (FuckMeGentlyWithAChainsaw) inf.getAttribute(ChatAttribute.HYPERLINK);
			if ((url != null) && (WebBrowser.self != null)) {
				try {
					WebBrowser.self.show(url.url);
				} catch (WebBrowser.BrowserException e) {
					getparent(GameUI.class).error("Could not launch web browser.");
				}
			}
		}

		public boolean mouseup(Coord c, int btn) {
			if (btn == 1) {
				if (selorig != null) {
					if (selstart != null)
						selected(selstart, selend);
					else
						clicked(selorig);
					ui.grabmouse(null);
					selorig = null;
					dragging = false;
				}
			}
			return (super.mouseup(c, btn));
		}

		public void select() {
			getparent(ChatUI.class).select(this);
		}

		public void display() {
			select();
			ChatUI chat = getparent(ChatUI.class);
			chat.expand();
			chat.parent.setfocus(chat);
		}

		private void drawsel(GOut g, Message msg, int y) {
			RichText rt = (RichText) msg.text();
			boolean sel = msg != selstart.msg;
			for (RichText.Part part = rt.parts; part != null; part = part.next) {
				if (!(part instanceof RichText.TextPart))
					continue;
				RichText.TextPart tp = (RichText.TextPart) part;
				if (tp.start == tp.end)
					continue;
				TextHitInfo a, b;
				if (sel) {
					a = TextHitInfo.leading(0);
				} else if (tp == selstart.part) {
					a = selstart.ch;
					sel = true;
				} else {
					continue;
				}
				if (tp == selend.part) {
					sel = false;
					b = selend.ch;
				} else {
					b = TextHitInfo.trailing(tp.end - tp.start - 1);
				}
				Coord ul = new Coord(tp.x + (int) tp.advance(0, a.getInsertionIndex()), tp.y + y);
				Coord sz = new Coord((int) tp.advance(a.getInsertionIndex(), b.getInsertionIndex()), tp.height());
				g.chcolor(0, 0, 255, 255);
				g.frect(ul, sz);
				g.chcolor();
				if (!sel)
					break;
			}
		}

		public void wdgmsg(Widget sender, String msg, Object... args) {
			if (sender == cbtn) {
				wdgmsg("close");
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException ex) {
						Logger.getLogger(ChatUI.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			} else {
				super.wdgmsg(sender, msg, args);
			}
		}

		public void uimsg(String name, Object... args) {
			if (name == "sel") {
				select();
			} else if (name == "dsp") {
				display();
			} else {
				super.uimsg(name, args);
			}
		}

		public abstract String name();
	}

	public static class Log extends Channel {
		private final String name;

		public Log(Widget parent, String name) {
			super(parent, false);
			this.name = name;
		}

		public String name() {
			return (name);
		}
	}

	static class TextEntryChannel extends TextEntry {
		public static Text.Foundry efnd = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12), Color.WHITE);

		protected Text.Line render_text(String text) {
			return efnd.render(text);
		}

		public TextEntryChannel(Coord c, Coord sz, Widget parent, String deftext) {
			super(c, sz, parent, deftext);
		}
	}

	public static abstract class EntryChannel extends Channel {
		private final TextEntry in;
		private List<String> history = new ArrayList<String>();
		private int hpos = 0;
		private String hcurrent;

		public EntryChannel(Widget parent) {
			super(parent, true);
			setfocusctl(true);
			int height = basesize + 8;
			this.in = new TextEntryChannel(new Coord(0, sz.y - height), new Coord(sz.x, height), this, "") {
				public void activate(String text) {
					if (text.length() > 0)
						send(text);
					settext("");
					hpos = history.size();
				}

				public boolean keydown(KeyEvent ev) {
					if (ev.getKeyCode() == KeyEvent.VK_UP) {
						if (hpos > 0) {
							if (hpos == history.size())
								hcurrent = text;
							rsettext(history.get(--hpos));
						}
						return (true);
					} else if (ev.getKeyCode() == KeyEvent.VK_DOWN) {
						if (hpos < history.size()) {
							if (++hpos == history.size())
								rsettext(hcurrent);
							else
								rsettext(history.get(hpos));
						}
						return (true);
					} else {
						return (super.keydown(ev));
					}
				}
			};
		}

		public int ih() {
			return (sz.y - 20);
		}

		public void resize(Coord sz) {
			super.resize(sz);
			if (in != null) {
				int height = basesize + 8;
				in.c = new Coord(0, this.sz.y - height);
				in.resize(new Coord(this.sz.x, height));
			}
		}

		public void send(String text) {
			history.add(text);
			wdgmsg("msg", text);
		}
	}

	public static class SimpleChat extends EntryChannel {
		public final String name;

		public SimpleChat(Widget parent, String name) {
			super(parent);
			this.name = name;
		}

		public void uimsg(String msg, Object... args) {
			if ((msg == "msg") || (msg == "log")) {
				String line = parseTags((String) args[0]);
				if (line != null) {
					Color col = null;
					if (args.length > 1) col = (Color) args[1];
					if (col == null) col = Color.WHITE;
					boolean notify = (args.length > 2) ? (((Integer) args[2]) != 0) : false;
					Message cmsg = new SimpleMessage(line, col, iw());
					append(cmsg, true);
					if (notify)
						notify(cmsg);
				}
			} else {
				super.uimsg(msg, args);
			}
		}

		public String name() {
			return (name);
		}
	}

	public static class MultiChat extends EntryChannel {
		private final String name;
		private final boolean notify;
		private final Map<Integer, Color> pc = new HashMap<Integer, Color>();

		public class NamedMessage extends Message {
			public final int from;
			public final String text;
			public final int w;
			public final Color col;
			private String cn;
			private Text r = null;

			public NamedMessage(int from, String text, Color col, int w) {
				this.from = from;
				this.text = text;
				this.w = w;
				this.col = col;
			}

			public Text text() {
				BuddyWnd.Buddy b = getparent(GameUI.class).buddies.find(from);
				String nm = (b == null) ? "???" : (b.name);
				if ((r == null) || !nm.equals(cn)) {
					String msg = RichText.Parser.quote(String.format("%s: %s", nm, text));
					if (Config.timestamp) {
						msg = Utils.timestamp(msg);
					}
					r = fnd.render(msg, w, TextAttribute.FOREGROUND, col);
					cn = nm;
				}
				return (r);
			}

			public Tex tex() {
				return (text().tex());
			}

			public Coord sz() {
				if (r == null)
					return (text().sz());
				else
					return (r.sz());
			}
		}

		public class MyMessage extends SimpleMessage {
			public MyMessage(String text, int w) {
				super(text, new Color(192, 192, 255), w);
			}
		}

		public MultiChat(Widget parent, String name, boolean notify) {
			super(parent);
			this.name = name;
			this.notify = notify;
			super.startLogging();
		}

		private static final Random cr = new Random();

		private static Color randcol() {
			int[] c = {cr.nextInt(256), cr.nextInt(256), cr.nextInt(256)};
			int mc = Math.max(c[0], Math.max(c[1], c[2]));
			if (c[2] > c[0]) {
				int t = c[0];
				c[0] = c[2];
				c[2] = t;
			}
			for (int i = 0; i < c.length; i++)
				c[i] = (c[i] * 255) / mc;
			return (new Color(c[0], c[1], c[2]));
		}

		public Color fromcolor(int from) {
			synchronized (pc) {
				Color c = pc.get(from);
				if (c == null)
					pc.put(from, c = randcol());
				return (c);
			}
		}

		public void uimsg(String msg, Object... args) {
			if (msg == "msg") {
				Integer from = (Integer) args[0];
				String line = parseTags((String) args[1]);
				if (line != null) {
					if (from == null) {
						append(new MyMessage(line, iw()), true);
					} else {
						Message cmsg = new NamedMessage(from, line, fromcolor(from), iw());
						append(cmsg, true);
						if (notify)
							notify(cmsg);
					}
				}

			} else {
				super.uimsg(msg, args);
			}
		}

		public String name() {
			return (name);
		}
	}

	public static class PartyChat extends MultiChat {
		public PartyChat(Widget parent) {
			super(parent, "Party", true);
		}

		public void uimsg(String msg, Object... args) {
			if (msg == "msg") {
				Integer from = (Integer) args[0];
				int gobid = (Integer) args[1];
				String line = parseTags((String) args[2]);
				if (line != null) {
					Color col = Color.WHITE;
					synchronized (ui.sess.glob.party.memb) {
						Party.Member pm = ui.sess.glob.party.memb.get((long) gobid);
						if (pm != null)
							col = lighter(pm.col);
					}
					if (from == null) {
						append(new MyMessage(line, iw()), true);
					} else {
						Message cmsg = new NamedMessage(from, line, col, iw());
						append(cmsg, true);
						notify(cmsg);
					}
				}

			} else {
				super.uimsg(msg, args);
			}
		}
	}

	public static class PrivChat extends EntryChannel {
		private final int other;
		public static final Color[] gc = new Color[]{
				new Color(230, 48, 32),
				new Color(64, 180, 200),
		};

		public class InMessage extends SimpleMessage {
			public InMessage(String text, int w) {
				super(text, PrivChat.gc[0], w);
			}
		}

		public class OutMessage extends SimpleMessage {
			public OutMessage(String text, int w) {
				super(text, PrivChat.gc[1], w);
			}
		}

		public PrivChat(Widget parent, int other) {
			super(parent);
			this.other = other;
			if (!isIgnored()) {
				this.getparent(ChatUI.class).select(this);
			}
			super.startLogging();
		}

		public void uimsg(String msg, Object... args) {
			if (msg == "msg") {
				String t = (String) args[0];
				String line = parseTags((String) args[1]);
				if (t.equals("in") && line != null) {

					if (msgs.isEmpty() && isIgnored()) {
						send("[Automatic] This player has ignored you and will not see your messages.");
						//this will gracefully close the pmchat
						cbtn.click();
						return;
					}

					Message cmsg = new InMessage(line, iw());
					append(cmsg, true);
					notify(cmsg);
				} else if (t.equals("out") && line != null) {
					append(new OutMessage(line, iw()), false);
				}

			} else if (msg == "err") {
				String err = (String) args[0];
				Message cmsg = new SimpleMessage(err, Color.RED, iw());
				append(cmsg, false);
				notify(cmsg);
			} else {
				super.uimsg(msg, args);
			}
		}

		protected boolean isIgnored() {
			String name = name();
			return name.contains("[ignored]");
		}

		public String name() {
			BuddyWnd.Buddy b = getparent(GameUI.class).buddies.find(other);
			if (b == null)
				return ("???");
			else
				return (b.name);
		}
	}

	@RName("schan")
	public static class $SChan implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			String name = (String) args[0];
			return (new SimpleChat(parent, name));
		}
	}

	@RName("mchat")
	public static class $MChat implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			String name = (String) args[0];
			boolean notify = ((Integer) args[1]) != 0;
			return (new MultiChat(parent, name, notify));
		}
	}

	@RName("pchat")
	public static class $PChat implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			return (new PartyChat(parent));
		}
	}

	@RName("pmchat")
	public static class $PMChat implements Factory {
		public Widget create(Coord c, Widget parent, Object[] args) {
			int other = (Integer) args[0];
			return (new PrivChat(parent, other));
		}
	}

	public Widget makechild(String type, Object[] pargs, Object[] cargs) {
		return (gettype(type).create(Coord.z, this, cargs));
	}

	private class Selector extends Widget {
		public Text.Foundry nf = new Text.Foundry("SansSerif", 12);
		private final List<DarkChannel> chls = new ArrayList<DarkChannel>();
		private int s = 0;
		public int ymod = 0;
		public boolean rerender = false;
		public Text.Foundry nfu = new Text.Foundry("SansSerif", 14, Font.BOLD);

		private class DarkChannel {
			public final Channel chan;
			public Text rname;
			public boolean rread;

			private DarkChannel(Channel chan) {
				this.chan = chan;
				this.rread = false;
			}
		}

		public Selector(Coord c, Coord sz) {
			super(c, sz, ChatUI.this);
		}

		private void add(Channel chan) {
			synchronized (chls) {
				chls.add(new DarkChannel(chan));
			}
		}

		private void rm(Channel chan) {
			synchronized (chls) {
				for (Iterator<DarkChannel> i = chls.iterator(); i.hasNext(); ) {
					DarkChannel c = i.next();
					if (c.chan == chan)
						i.remove();
				}
			}
		}

		public void draw(GOut g) {
			g.chcolor(64, 64, 64, 192);
			g.frect(Coord.z, sz);
			int i = s;
			int y = 0;
			synchronized (chls) {
				while (i < chls.size()) {
					DarkChannel ch = chls.get(i);
					if (ch.chan == sel) {
						g.chcolor(128, 128, 192, 255);
						g.frect(new Coord(0, y), new Coord(sz.x, 19 + ymod));
					}
					g.chcolor(255, 255, 255, 255);

					if (rerender || (ch.rname == null) || !ch.rname.text.equals(ch.chan.name()) || ch.rread != ch.chan.read) {
						ch.rread = ch.chan.read;
						if (ch.rread) {
							ch.rname = nf.render(ch.chan.name());
						} else {
							ch.rname = nfu.render(ch.chan.name());
						}
					}

					if (rerender) {
						ch.chan.c = new Coord(selw, 0);
					}

					g.aimage(ch.rname.tex(), new Coord(sz.x / 2, y + 10 + ymod / 2), 0.5, 0.5);
					g.line(new Coord(5, y + 19 + ymod), new Coord(sz.x - 5, y + 19 + ymod), 1);
					y += 20 + ymod;
					if (y >= sz.y)
						break;
					i++;
				}
				rerender = false;
			}
			g.chcolor();
		}

		public boolean up() {
			Channel prev = null;
			for (DarkChannel ch : chls) {
				if (ch.chan == sel) {
					if (prev != null) {
						select(prev);
						return (true);
					} else {
						return (false);
					}
				}
				prev = ch.chan;
			}
			return (false);
		}

		public boolean down() {
			for (Iterator<DarkChannel> i = chls.iterator(); i.hasNext(); ) {
				DarkChannel ch = i.next();
				if (ch.chan == sel) {
					if (i.hasNext()) {
						select(i.next().chan);
						return (true);
					} else {
						return (false);
					}
				}
			}
			return (false);
		}

		private Channel bypos(Coord c) {
			int i = (c.y / (20 + ymod)) + s;
			if ((i >= 0) && (i < chls.size()))
				return (chls.get(i).chan);
			return (null);
		}

		public boolean mousedown(Coord c, int button) {
			if (button == 1) {
				Channel chan = bypos(c);
				if (chan != null)
					select(chan);
			}
			return (true);
		}

		public boolean mousewheel(Coord c, int amount) {
			s += amount;
			if (s >= chls.size() - (sz.y / 20))
				s = chls.size() - (sz.y / 20);
			if (s < 0)
				s = 0;
			return (true);
		}
	}

	public void select(Channel chan) {
		prevsel = sel;
		sel = chan;
		if (Config.chat_expanded) {
			if (prevsel != null)
				prevsel.hide();
			sel.show();
			resize(sz);
		}
	}

	private class Notification {
		public final Channel chan;
		public final Text chnm;
		public final Channel.Message msg;
		public final long time = System.currentTimeMillis();

		private Notification(Channel chan, Channel.Message msg) {
			this.chan = chan;
			this.msg = msg;
			this.chnm = chansel.nf.render(chan.name(), Color.WHITE);
		}
	}

	private Text.Line rqline = null;
	private int rqpre;

	public void drawsmall(GOut g, Coord br, int h) {
		Coord c;
		if (qline != null) {
			if ((rqline == null) || !rqline.text.equals(qline.line)) {
				String pre = String.format("%s> ", qline.chan.name());
				rqline = qfnd.render(pre + qline.line);
				rqpre = pre.length();
			}
			c = br.sub(0, 8 + basesize);
			g.chcolor(24, 24, 16, 200);
			g.frect(c, rqline.tex().sz());
			g.chcolor();
			g.image(rqline.tex(), c);
			int lx = rqline.advance(qline.point + rqpre);
			g.line(new Coord(br.x + lx + 1, br.y - 18), new Coord(br.x + lx + 1, br.y - 6), 1);
		} else {
			c = br.sub(0, 5);
		}
		long now = System.currentTimeMillis();
		synchronized (notifs) {
			for (Iterator<Notification> i = notifs.iterator(); i.hasNext(); ) {
				Notification n = i.next();
				if (now - n.time > 5000) {
					i.remove();
					continue;
				}
				if ((c.y -= n.msg.sz().y) < br.y - h)
					break;

				g.chcolor(24, 24, 16, 200);
				g.frect(c, n.chnm.tex().sz().add(n.msg.tex().sz().x + selw, 0));
				g.chcolor();
				g.image(n.chnm.tex(), c, br.sub(0, h), br.add(selw - 10, 0));
				g.image(n.msg.tex(), c.add(selw, 0));
			}
		}
	}

	public static final Resource notifsfx = Resource.load("sfx/tick");

	public void notify(Channel chan, Channel.Message msg) {
		synchronized (notifs) {
			notifs.addFirst(new Notification(chan, msg));
		}
		Audio.play(notifsfx);
	}

	public void newchild(Widget w) {
		if (w instanceof Channel) {
			Channel chan = (Channel) w;
			chansel.add(chan);
			//PrivChats handle selection themselves
			if (!PrivChat.class.isInstance(chan))
				select(chan);
			if (!Config.chat_expanded)
				chan.hide();
		}
	}

	public void cdestroy(Widget w) {
		if (w instanceof Channel) {
			Channel chan = (Channel) w;
			if (chan == sel)
				sel = null;
			chansel.rm(chan);
		}
	}

	public void resize(Coord sz) {
		super.resize(sz);
		this.c = base.add(0, -this.sz.y);
		chansel.resize(new Coord(selw, this.sz.y));
		if (sel != null)
			sel.resize(new Coord(this.sz.x - selw, this.sz.y));
	}

	public void resize(int w) {
		resize(new Coord(w, sz.y));
	}

	public void move(Coord base) {
		this.c = (this.base = base).add(0, -sz.y);
	}

	public void expand() {
		if (Config.chat_expanded)
			return;
		resize(new Coord(sz.x, 100 + (basesize - 12) * 24));
		Utils.setprefb("chat_expanded", Config.chat_expanded = true);
		update_visibility();
	}

	public void contract() {
		if (!Config.chat_expanded)
			return;
		resize(new Coord(sz.x, 50));
		Utils.setprefb("chat_expanded", Config.chat_expanded = false);
		update_visibility();
	}

	public void update_visibility() {
		if (Config.chat_expanded) {
			setcanfocus(true);
			if (sel != null)
				sel.show();
			chansel.show();
		} else {
			setcanfocus(false);
			if (sel != null)
				sel.hide();
			chansel.hide();
		}
	}

	private class QuickLine extends LineEdit {
		public final EntryChannel chan;

		private QuickLine(EntryChannel chan) {
			this.chan = chan;
		}

		private void cancel() {
			qline = null;
			ui.grabkeys(null);
		}

		protected void done(String line) {
			if (line.length() > 0)
				chan.send(line);
			cancel();
		}

		public boolean key(char c, int code, int mod) {
			if (c == 27) {
				cancel();
			} else {
				return (super.key(c, code, mod));
			}
			return (true);
		}
	}

	public boolean keydown(KeyEvent ev) {
		boolean M = (ev.getModifiersEx() & (KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;
		if (qline != null) {
			if (M && (ev.getKeyCode() == KeyEvent.VK_UP)) {
				Channel prev = this.sel;
				while (chansel.up()) {
					if (this.sel instanceof EntryChannel)
						break;
				}
				if (!(this.sel instanceof EntryChannel)) {
					select(prev);
					return (true);
				}
				qline = new QuickLine((EntryChannel) sel);
				return (true);
			} else if (M && (ev.getKeyCode() == KeyEvent.VK_DOWN)) {
				Channel prev = this.sel;
				while (chansel.down()) {
					if (this.sel instanceof EntryChannel)
						break;
				}
				if (!(this.sel instanceof EntryChannel)) {
					select(prev);
					return (true);
				}
				qline = new QuickLine((EntryChannel) sel);
				return (true);
			}
			qline.key(ev);
			return (true);
		} else {
			if (M && (ev.getKeyCode() == KeyEvent.VK_UP)) {
				chansel.up();
				return (true);
			} else if (M && (ev.getKeyCode() == KeyEvent.VK_DOWN)) {
				chansel.down();
				return (true);
			}
			return (super.keydown(ev));
		}
	}

	public void toggle() {
		if (!Config.chat_expanded) {
			expand();
			parent.setfocus(this);
		} else {
			if (hasfocus) {
				if (sz.y == 100 + (basesize - 12) * 24)
					resize(new Coord(sz.x, 300 + (basesize - 12) * 48));
				else
					contract();
			} else {
				parent.setfocus(this);
			}
		}
	}

	public boolean type(char key, KeyEvent ev) {
		if (qline != null) {
			qline.key(ev);
			return (true);
		} else {
			return (super.type(key, ev));
		}
	}

	public boolean globtype(char key, KeyEvent ev) {
		if (key == 10) {
			if (!Config.chat_expanded && (sel instanceof EntryChannel)) {
				ui.grabkeys(this);
				qline = new QuickLine((EntryChannel) sel);
				return (true);
			}
		}
		return (super.globtype(key, ev));
	}

	private static String parseTags(String text) {
		try {
			Matcher m = tags_patt.matcher(text);
			while (m.find()) {
				String tag = m.group(1);
				String val = m.group(2);
				if (tag.equals("hl")) {
					try {
						long id = Long.parseLong(val);
						Gob gob = UI.instance.sess.glob.oc.getgob(id);
						if (gob != null) {
							gob.setattr(new GobHighlight(gob));
							ResDrawable d = gob.getattr(ResDrawable.class);
							String name = "unknown";
							if (d != null && d.getres() != null) {
								try {
									name = d.getres().name;
								} catch (Exception e) {
								}
							} else if (gob.getres() != null){
								try {
									name = gob.getres().name;
								} catch (Exception e) {
								}
							}
							UI.instance.gui.message("Highlighted object: " + name + " " + gob.id, GameUI.MsgType.INFO);
						}
					} catch (NumberFormatException ignored) {
					}
					return null;
				}
			}
		} catch (Exception ignored) {
		}
		return text;
	}
}
