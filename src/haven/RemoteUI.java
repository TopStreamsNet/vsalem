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

public class RemoteUI implements UI.Receiver, UI.Runner {
	public final Session sess;
	private Session	ret;
	UI ui;

	public RemoteUI(Session sess) {
		this.sess = sess;
		Widget.initnames();
	}

	public void rcvmsg(int id, String name, Object... args) {
		Message msg = new Message(Message.RMSG_WDGMSG);
		msg.adduint16(id);
		msg.addstring(name);
		msg.addlist(args);
		sess.queuemsg(msg);
	}

	public void ret(Session sess) {
		synchronized(this.sess) {
			this.ret = sess;
			this.sess.notifyAll();
		}
	}

	public Session run(UI ui) throws InterruptedException {
		this.ui = ui;
		ui.setreceiver(this);
		while(true) {
			Message msg;
			while((msg = sess.getuimsg()) != null) {
				if(Config.debug) {
					System.out.println("RemoteUI Receiver : "+msg.type);
				}
				if(msg.type == Message.RMSG_NEWWDG) {
					int id = msg.uint16();
					String type = msg.string();
					int parent = msg.uint16();
					Object[] pargs = msg.list();
					Object[] cargs = msg.list();
					ui.newwidget(id, type, parent, pargs, cargs);
					if(Config.debug) {
						int i = 0;
						try {
							for (Object obj : pargs) {
								System.out.println("\tpargs["+i+"]" + obj);
								i++;
							}
							i = 0;
							for (Object obj : cargs) {
								System.out.println("\tcargs["+i+"]" + obj);
								i++;
							}
						} catch (ArrayIndexOutOfBoundsException ignored) {
						}
					}

				} else if(msg.type == Message.RMSG_WDGMSG) {
					int id = msg.uint16();
					String name = msg.string();
					Object[] args = msg.list();
					ui.uimsg(id, name, args);

					checkvents(name, args);
					if(Config.debug) {
						System.out.println("\tname: "+name);
						int i = 0;
						try {
							for (Object obj : args) {
								System.out.println("\targs["+i+"]" + obj);
								i++;
							}
						} catch (ArrayIndexOutOfBoundsException ignored) {
						}
					}
				} else if(msg.type == Message.RMSG_DSTWDG) {
					int id = msg.uint16();
					ui.destroy(id);
				}
			}
			synchronized(sess) {
				if(ret != null) {
					sess.close();
					return(ret);
				}
				if(!sess.alive())
					return(null);
				sess.wait(50);
			}
		}
	}

	private void checkvents(String name, Object[] args) {
		if(name.equals("prog")){
			if(args.length == 0){
				progressComplete();
			}
		}
	}

	private void progressComplete() {
		try {
			if (Config.autosift && UI.isCursor(UI.Cursor.SIFTING)) {
				MapView map = UI.instance.gui.map;
				Gob player = map.player();
				map.wdgmsg(map, "click", player.sc, player.rc, 1, 0);
			}
		} catch(Exception ignored){}
	}
}
