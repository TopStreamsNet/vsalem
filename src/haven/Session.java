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

import haven.ItemInfo.Owner;
import haven.automation.SessionDetails;
import haven.integrations.map.MappingClient;

import java.net.*;
import java.util.*;
import java.io.*;
import java.lang.ref.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Session implements Owner {
	public static final int PVER = 36;

	public static final int MSG_SESS = 0;
	public static final int MSG_REL = 1;
	public static final int MSG_ACK = 2;
	public static final int MSG_BEAT = 3;
	public static final int MSG_MAPREQ = 4;
	public static final int MSG_MAPDATA = 5;
	public static final int MSG_OBJDATA = 6;
	public static final int MSG_OBJACK = 7;
	public static final int MSG_CLOSE = 8;
	public static final int OD_REM = 0;
	public static final int OD_MOVE = 1;
	public static final int OD_RES = 2;
	public static final int OD_LINBEG = 3;
	public static final int OD_LINSTEP = 4;
	public static final int OD_SPEECH = 5;
	public static final int OD_COMPOSE = 6;
	public static final int OD_ZOFF = 7;
	public static final int OD_LUMIN = 8;
	public static final int OD_AVATAR = 9;
	public static final int OD_FOLLOW = 10;
	public static final int OD_HOMING = 11;
	public static final int OD_OVERLAY = 12;
	/* public static final int OD_AUTH = 13; -- Removed */
	public static final int OD_HEALTH = 14;
	public static final int OD_BUDDY = 15;
	public static final int OD_CMPPOSE = 16;
	public static final int OD_CMPMOD = 17;
	public static final int OD_CMPEQU = 18;
	public static final int OD_ICON = 19;
	public static final int OD_END = 255;
	public static final int SESSERR_AUTH = 1;
	public static final int SESSERR_BUSY = 2;
	public static final int SESSERR_CONN = 3;
	public static final int SESSERR_PVER = 4;
	public static final int SESSERR_EXPR = 5;

	static final int ackthresh = 30;

	DatagramSocket sk;
	SocketAddress server;
	Thread rworker, sworker, ticker;
	Object[] args;
	public int connfailed = 0;
	public String state = "conn";
	int tseq = 0, rseq = 0;
	int ackseq;
	long acktime = -1;
	LinkedList<Message> uimsgs = new LinkedList<Message>();
	Map<Integer, Message> waiting = new TreeMap<Integer, Message>();
	LinkedList<Message> pending = new LinkedList<Message>();
	Map<Long, ObjAck> objacks = new TreeMap<Long, ObjAck>();
	public final SessionDetails details;
	String username;
	byte[] cookie;
	final Map<Integer, CachedRes> rescache = new TreeMap<Integer, CachedRes>();
	public final Glob glob;
	public byte[] sesskey;

	@SuppressWarnings("serial")
	public class MessageException extends RuntimeException {
		public Message msg;

		public MessageException(String text, Message msg) {
			super(text);
			this.msg = msg;
		}
	}

	public static class LoadingIndir extends Loading {
		public final int resid;
		private final CachedRes res;

		private LoadingIndir(CachedRes res) {
			this.res = res;
			this.resid = res.resid;
		}

		public void waitfor() throws InterruptedException {
			synchronized(res) {
				while(res.resnm == null)
					res.wait();
			}
		}
		public boolean canwait() {return(true);}
	}

	private static class CachedRes {
		private final int resid;
		private String resnm = null;
		private int resver;
		private Reference<Indir<Resource>> ind;

		private CachedRes(int id) {
			resid = id;
		}

		private Indir<Resource> get() {
			Indir<Resource> ind = (this.ind == null)?null:(this.ind.get());
			if(ind == null) {
				ind = new Indir<Resource>() {
					private Resource res;

					public Resource get() {
						if(resnm == null)
							throw(new LoadingIndir(CachedRes.this));
						if(res == null)
							res = Resource.load(resnm, resver, 0);
						if(res.loading)
							throw(new Resource.Loading(res));
						return(res);
					}

					public String toString() {
						if(res == null) {
							return("<res:" + resid + ">");
						} else {
							if(res.loading)
								return("<!" + res + ">");
							else
								return("<" + res + ">");
						}
					}
				};
				this.ind = new WeakReference<Indir<Resource>>(ind);
			}
			return(ind);
		}

		public void set(String nm, int ver) {
			synchronized(this) {
				this.resnm = nm;
				this.resver = ver;
				notifyAll();
			}
			Resource.load(nm, ver, -5);
		}
	}

	private CachedRes cachedres(int id) {
		synchronized(rescache) {
			CachedRes ret = rescache.get(id);
			if(ret != null)
				return(ret);
			ret = new CachedRes(id);
			rescache.put(id, ret);
			return(ret);
		}
	}

	public Indir<Resource> getres(int id) {
		return(cachedres(id).get());
	}

	public int getresid(String name){
		synchronized(rescache) {
			for(CachedRes cres : rescache.values()){
				if(name.equals(cres.resnm)){
					return cres.resid;
				}
			}
		}
		return 0;
	}

	private class ObjAck {
		long id;
		int frame;
		long recv;
		long sent;

		public ObjAck(long id, int frame, long recv) {
			this.id = id;
			this.frame = frame;
			this.recv = recv;
			this.sent = 0;
		}
	}

	private class Ticker extends HackThread {
		public Ticker() {
			super("Server time ticker");
			setDaemon(true);
		}

		public void run() {
			try {
				while(true) {
					long now, then;
					then = System.currentTimeMillis();
					glob.oc.tick();
					now = System.currentTimeMillis();
					if(now - then < 70)
						Thread.sleep(70 - (now - then));
				}
			} catch(InterruptedException e) {}
		}
	}

	private class RWorker extends HackThread {
		boolean alive;

		public RWorker() {
			super("Session reader");
			setDaemon(true);
		}

		private void gotack(int seq) {
			synchronized(pending) {
				for(ListIterator<Message> i = pending.listIterator(); i.hasNext(); ) {
					Message msg = i.next();
					if(msg.seq <= seq)
						i.remove();
				}
			}
		}

		private void getobjdata(Message msg) {
			OCache oc = glob.oc;
			while(msg.off < msg.blob.length) {
				int fl = msg.uint8();
				long id = msg.uint32();
				int frame = msg.int32();
				synchronized(oc) {
					if((fl & 1) != 0)
						oc.remove(id, frame - 1);
					Gob gob = oc.getgob(id, frame);
					if(gob != null) {
						gob.frame = frame;
						gob.virtual = ((fl & 2) != 0);
					}
					while(true) {
						int type = msg.uint8();
						if(type == OD_REM) {
							oc.remove(id, frame);
						} else if(type == OD_MOVE) {
							Coord c = msg.coord();
							int ia = msg.uint16();
							if(gob != null)
								oc.move(gob, c, (ia / 65536.0) * Math.PI * 2);
						} else if(type == OD_RES) {
							int resid = msg.uint16();
							Message sdt;
							if((resid & 0x8000) != 0) {
								resid &= ~0x8000;
								sdt = msg.derive(0, msg.uint8());
							} else {
								sdt = new Message(0);
							}
							if(gob != null)
								oc.cres(gob, getres(resid), sdt);
						} else if(type == OD_LINBEG) {
							Coord s = msg.coord();
							Coord t = msg.coord();
							int c = msg.int32();
							if(gob != null)
								oc.linbeg(gob, s, t, c);
						} else if(type == OD_LINSTEP) {
							int l = msg.int32();
							if(gob != null)
								oc.linstep(gob, l);
						} else if(type == OD_SPEECH) {
							float zo = msg.int16() / 100.0f;
							String text = msg.string();
							if(gob != null && !ChatUI.hasTags(text))
								oc.speak(gob, zo, text);
						} else if(type == OD_COMPOSE) {
							Indir<Resource> base = getres(msg.uint16());
							if(gob != null)
								oc.composite(gob, base);
						} else if(type == OD_CMPPOSE) {
							List<ResData> poses = null, tposes = null;
							int pfl = msg.uint8();
							int seq = msg.uint8();
							boolean interp = (pfl & 1) != 0;
							if((pfl & 2) != 0) {
								poses = new LinkedList<ResData>();
								while(true) {
									int resid = msg.uint16();
									if(resid == 65535)
										break;
									Message sdt = Message.nil;
									if((resid & 0x8000) != 0) {
										resid &= ~0x8000;
										sdt = msg.derive(0, msg.uint8());
									}
									poses.add(new ResData(getres(resid), sdt));
								}
							}
							float ttime = 0;
							if((pfl & 4) != 0) {
								tposes = new LinkedList<ResData>();
								while(true) {
									int resid = msg.uint16();
									if(resid == 65535)
										break;
									Message sdt = Message.nil;
									if((resid & 0x8000) != 0) {
										resid &= ~0x8000;
										sdt = msg.derive(0, msg.uint8());
									}
									tposes.add(new ResData(getres(resid), sdt));
								}
								ttime = (msg.uint8() / 10.0f);
							}
							if(gob != null)
								oc.cmppose(gob, seq, poses, tposes, interp, ttime);
						} else if(type == OD_CMPMOD) {
							List<Composited.MD> mod = new LinkedList<Composited.MD>();
							while(true) {
								int modid = msg.uint16();
								if(modid == 65535)
									break;
								Indir<Resource> modr = getres(modid);
								List<Indir<Resource>> tex = new LinkedList<Indir<Resource>>();
								while(true) {
									int resid = msg.uint16();
									if(resid == 65535)
										break;
									tex.add(getres(resid));
								}
								mod.add(new Composited.MD(modr, tex));
							}
							if(gob != null)
								oc.cmpmod(gob, mod);
						} else if(type == OD_CMPEQU) {
							List<Composited.ED> equ = new LinkedList<Composited.ED>();
							while(true) {
								int h = msg.uint8();
								if(h == 255)
									break;
								int ef = h & 0x80;
								int et = h & 0x7f;
								String at = msg.string();
								Indir<Resource> res;
								int resid = msg.uint16();
								if(resid == 65535)
									res = null;
								else
									res = getres(resid);
								Coord3f off;
								if((ef & 128) != 0) {
									int x = msg.int16(), y = msg.int16(), z = msg.int16();
									off = new Coord3f(x / 1000.0f, y / 1000.0f, z / 1000.0f);
								} else {
									off = Coord3f.o;
								}
								equ.add(new Composited.ED(et, at, res, off));
							}
							if(gob != null)
								oc.cmpequ(gob, equ);
						} else if(type == OD_ZOFF) {
							float off = msg.int16() / 100.0f;
							if(gob != null)
								oc.zoff(gob, off);
						} else if(type == OD_LUMIN) {
							Coord off = msg.coord();
							int sz = msg.uint16();
							int str = msg.uint8();
							if(gob != null)
								oc.lumin(gob, off, sz, str);
						} else if(type == OD_AVATAR) {
							List<Indir<Resource>> layers = new LinkedList<Indir<Resource>>();
							while(true) {
								int layer = msg.uint16();
								if(layer == 65535)
									break;
								layers.add(getres(layer));
							}
							if(gob != null)
								oc.avatar(gob, layers);
						} else if(type == OD_FOLLOW) {
							long oid = msg.uint32();
							Indir<Resource> xfres = null;
							String xfname = null;
							if(oid != 0xffffffffL) {
								xfres = getres(msg.uint16());
								xfname = msg.string();
							}
							if(gob != null)
								oc.follow(gob, oid, xfres, xfname);
						} else if(type == OD_HOMING) {
							long oid = msg.uint32();
							if(oid == 0xffffffffL) {
								if(gob != null)
									oc.homostop(gob);
							} else if(oid == 0xfffffffeL) {
								Coord tgtc = msg.coord();
								int v = msg.uint16();
								if(gob != null)
									oc.homocoord(gob, tgtc, v);
							} else {
								Coord tgtc = msg.coord();
								int v = msg.uint16();
								if(gob != null)
									oc.homing(gob, oid, tgtc, v);
							}
						} else if(type == OD_OVERLAY) {
							int olid = msg.int32();
							boolean prs = (olid & 1) != 0;
							olid >>= 1;
							int resid = msg.uint16();
							Indir<Resource> res;
							Message sdt;
							if(resid == 65535) {
								res = null;
								sdt = null;
							} else {
								if((resid & 0x8000) != 0) {
									resid &= ~0x8000;
									sdt = msg.derive(0, msg.uint8());
								} else {
									sdt = new Message(0);
								}
								res = getres(resid);
							}
							if(gob != null)
								oc.overlay(gob, olid, prs, res, sdt);
						} else if(type == OD_HEALTH) {
							int hp = msg.uint8();
							if(gob != null)
								oc.health(gob, hp);
						} else if(type == OD_BUDDY) {
							String name = msg.string();
							if(name.length() > 0) {
								int group = msg.uint8();
								int btype = msg.uint8();
								if(gob != null)
									oc.buddy(gob, name, group, btype);
							} else {
								if(gob != null)
									oc.buddy(gob, null, 0, 0);
							}
						} else if(type == OD_ICON) {
							int resid = msg.uint16();
							Indir<Resource> res;
							if(resid == 65535) {
								res = null;
							} else {
								res = getres(resid);
								int ifl = msg.uint8();
							}
							if(gob != null)
								oc.icon(gob, res);
						} else if(type == OD_END) {
							break;
						} else {
							throw(new MessageException("Unknown objdelta type: " + type, msg));
						}
					}
				}
				synchronized(objacks) {
					if(objacks.containsKey(id)) {
						ObjAck a = objacks.get(id);
						a.frame = frame;
						a.recv = System.currentTimeMillis();
					} else {
						objacks.put(id, new ObjAck(id, frame, System.currentTimeMillis()));
					}
				}
			}
			synchronized(sworker) {
				sworker.notifyAll();
			}
		}

		private void handlerel(Message msg) {
			if(msg.type == Message.RMSG_NEWWDG) {
				synchronized(uimsgs) {
					uimsgs.add(msg);
				}
			} else if(msg.type == Message.RMSG_WDGMSG) {
				synchronized(uimsgs) {
					uimsgs.add(msg);
				}
			} else if(msg.type == Message.RMSG_DSTWDG) {
				synchronized(uimsgs) {
					uimsgs.add(msg);
				}
			} else if(msg.type == Message.RMSG_MAPIV) {
				glob.map.invalblob(msg);
			} else if(msg.type == Message.RMSG_GLOBLOB) {
				glob.blob(msg);
			} else if(msg.type == Message.RMSG_PAGINAE) {
				glob.paginae(msg);
			} else if(msg.type == Message.RMSG_RESID) {
				int resid = msg.uint16();
				String resname = msg.string();
				int resver = msg.uint16();
				cachedres(resid).set(resname, resver);
			} else if(msg.type == Message.RMSG_PARTY) {
				glob.party.msg(msg);
			} else if(msg.type == Message.RMSG_SFX) {
				Indir<Resource> res = getres(msg.uint16());
				double vol = ((double)msg.uint16()) / 256.0;
				double spd = ((double)msg.uint16()) / 256.0;
				Audio.play(res);
			} else if(msg.type == Message.RMSG_CATTR) {
				glob.cattr(msg);
			} else if(msg.type == Message.RMSG_MUSIC) {
				String resnm = msg.string();
				int resver = msg.uint16();
				boolean loop = !msg.eom() && (msg.uint8() != 0);
				if(resnm.equals(""))
					Music.play(null, false);
				else
					Music.play(Resource.load(resnm, resver), loop);
			} else if(msg.type == Message.RMSG_TILES) {
				glob.map.tilemap(msg);
			} else if(msg.type == Message.RMSG_BUFF) {
				glob.buffmsg(msg);
			} else if(msg.type == Message.RMSG_SESSKEY) {
				sesskey = msg.bytes();
			} else {
				throw(new MessageException("Unknown rmsg type: " + msg.type, msg));
			}
		}

		private void getrel(int seq, Message msg) {
			if(seq == rseq) {
				int lastack;
				synchronized(uimsgs) {
					handlerel(msg);
					while(true) {
						rseq = ((lastack = rseq) + 1) % 65536;
						if(!waiting.containsKey(rseq))
							break;
						handlerel(waiting.get(rseq));
						waiting.remove(rseq);
					}
				}
				sendack(lastack);
				synchronized(Session.this) {
					Session.this.notifyAll();
				}
			} else if(Utils.floormod(seq - rseq, 65536) < 32768) {
				waiting.put(seq, msg);
			}
		}

		public void run() {
			try {
				alive = true;
				try {
					sk.setSoTimeout(1000);
				} catch(SocketException e) {
					throw(new RuntimeException(e));
				}
				while(alive) {
					DatagramPacket p = new DatagramPacket(new byte[65536], 65536);
					try {
						sk.receive(p);
					} catch(java.nio.channels.ClosedByInterruptException e) {
						/* Except apparently Sun's J2SE doesn't throw this when interrupted :P*/
						break;
					} catch(SocketTimeoutException e) {
						continue;
					} catch(IOException e) {
						throw(new RuntimeException(e));
					}
					if(!p.getSocketAddress().equals(server))
						continue;

					Message msg = new Message(p.getData()[0], p.getData(), 1, p.getLength() - 1);
					if(msg.type == MSG_SESS) {
						if(state == "conn") {
							int error = msg.uint8();
							synchronized(Session.this) {
								if(error == 0) {
									state = "";
								} else {
									connfailed = error;
									Session.this.close();
								}
								Session.this.notifyAll();
							}
						}
					}
					if(state != "conn") {
						if(msg.type == MSG_SESS) {
						} else if(msg.type == MSG_REL) {
							int seq = msg.uint16();
							while(!msg.eom()) {
								int type = msg.uint8();
								int len;
								if((type & 0x80) != 0) {
									type &= 0x7f;
									len = msg.uint16();
								} else {
									len = msg.blob.length - msg.off;
								}
								getrel(seq, new Message(type, msg.blob, msg.off, len));
								msg.off += len;
								seq++;
							}
						} else if(msg.type == MSG_ACK) {
							gotack(msg.uint16());
						} else if(msg.type == MSG_MAPDATA) {
							glob.map.mapdata(msg);
						} else if(msg.type == MSG_OBJDATA) {
							getobjdata(msg);
						} else if(msg.type == MSG_CLOSE) {
							synchronized(Session.this) {
								state = "fin";
								Session.this.notifyAll();
							}
							Session.this.close();
						} else {
							throw(new MessageException("Unknown message type: " + msg.type, msg));
						}

						if(Config.autolog && UI.instance.timesinceactive() > 1000*60*10)
						{
							try {
								UI.instance.be_active();
								UI.instance.cons.run("act lo cs");
							} catch (Exception ex) {
							}
						}
					}
				}
			} finally {
				synchronized(Session.this) {
					state = "dead";
					Session.this.notifyAll();
				}
			}
		}

		public void interrupt() {
			alive = false;
			super.interrupt();
		}
	}

	private class SWorker extends HackThread {

		public SWorker() {
			super("Session writer");
			setDaemon(true);
		}

		public void run() {
			try {
				long to, last = 0, retries = 0;
				while(true) {

					long now = System.currentTimeMillis();
					if(state == "conn") {
						if(now - last > 2000) {
							if(++retries > 5) {
								synchronized(Session.this) {
									connfailed = SESSERR_CONN;
									Session.this.notifyAll();
									return;
								}
							}
							Message msg = new Message(MSG_SESS);
							msg.adduint16(2);
							msg.addstring("Salem");
							msg.adduint16(PVER);
							msg.addstring(username);
							msg.adduint16(cookie.length);
							msg.addbytes(cookie);
							msg.addlist(args);
							sendmsg(msg);
							last = now;
						}
						Thread.sleep(100);
					} else {
						to = 5000;
						synchronized(pending) {
							if(pending.size() > 0)
								to = 60;
						}
						synchronized(objacks) {
							if((objacks.size() > 0) && (to > 120))
								to = 200;
						}
						synchronized(this) {
							if(acktime > 0)
								to = acktime + ackthresh - now;
							if(to > 0)
								this.wait(to);
						}
						now = System.currentTimeMillis();
						boolean beat = true;
			/*
			  if((closing != -1) && (now - closing > 500)) {
			  Message cm = new Message(MSG_CLOSE);
			  sendmsg(cm);
			  closing = now;
			  if(++ctries > 5)
			  getThreadGroup().interrupt();
			  }
			*/
						synchronized(pending) {
							if(pending.size() > 0) {
								for(Message msg : pending) {
									int txtime;
									if(msg.retx == 0)
										txtime = 0;
									else if(msg.retx == 1)
										txtime = 80;
									else if(msg.retx < 4)
										txtime = 200;
									else if(msg.retx < 10)
										txtime = 620;
									else
										txtime = 2000;
									if(now - msg.last > txtime) { /* XXX */
										msg.last = now;
										msg.retx++;
										Message rmsg = new Message(MSG_REL);
										rmsg.adduint16(msg.seq);
										rmsg.adduint8(msg.type);
										rmsg.addbytes(msg.blob);
										sendmsg(rmsg);
									}
								}
								beat = false;
							}
						}
						synchronized(objacks) {
							Message msg = null;
							for(Iterator<ObjAck> i = objacks.values().iterator(); i.hasNext();) {
								ObjAck a = i.next();
								boolean send = false, del = false;
								if(now - a.sent > 200)
									send = true;
								if(now - a.recv > 120)
									send = del = true;
								if(send) {
									if(msg == null) {
										msg = new Message(MSG_OBJACK);
									} else if(msg.blob.length > 1000 - 8) {
										sendmsg(msg);
										beat = false;
										msg = new Message(MSG_OBJACK);
									}
									msg.adduint32(a.id);
									msg.addint32(a.frame);
									a.sent = now;
								}
								if(del)
									i.remove();
							}
							if(msg != null) {
								sendmsg(msg);
								beat = false;
							}
						}
						synchronized(this) {
							if((acktime > 0) && (now - acktime >= ackthresh)) {
								byte[] msg = {MSG_ACK, 0, 0};
								Utils.uint16e(ackseq, msg, 1);
								sendmsg(msg);
								acktime = -1;
								beat = false;
							}
						}
						if(beat) {
							if(now - last > 5000) {
								sendmsg(new byte[] {MSG_BEAT});
								last = now;
							}
						}
					}
				}
			} catch(InterruptedException e) {
				for(int i = 0; i < 5; i++) {
					sendmsg(new Message(MSG_CLOSE));
					long f = System.currentTimeMillis();
					while(true) {
						synchronized(Session.this) {
							if((state == "conn") || (state == "fin") || (state == "dead"))
								break;
							state = "close";
							long now = System.currentTimeMillis();
							if(now - f > 500)
								break;
							try {
								Session.this.wait(500 - (now - f));
							} catch(InterruptedException e2) {}
						}
					}
				}
			} finally {
				ticker.interrupt();
				rworker.interrupt();
			}
		}
	}

	public Session(SocketAddress server, String username, byte[] cookie, Object... args) {
		this.server = server;
		this.details = new SessionDetails(this);
		this.username = username;
		this.cookie = cookie;
		this.args = args;
		glob = new Glob(this);
		try {
			sk = new DatagramSocket();
		} catch(SocketException e) {
			throw(new RuntimeException(e));
		}
		rworker = new RWorker();
		rworker.start();
		sworker = new SWorker();
		sworker.start();
		ticker = new Ticker();
		ticker.start();
	}

	private void sendack(int seq) {
		synchronized(sworker) {
			if(acktime < 0)
				acktime = System.currentTimeMillis();
			ackseq = seq;
			sworker.notifyAll();
		}
	}

	public void close() {
		sworker.interrupt();
		rworker.interrupt();
		ticker.interrupt();
		if (this.alive() && this.username != null) {
			MappingClient.getInstance(username).SetEndpoint("");
			MappingClient.getInstance(username).EnableGridUploads(false);
			MappingClient.getInstance(username).EnableTracking(false);
			MappingClient.removeInstance(username);
		}
	}

	public synchronized boolean alive() {
		return(state != "dead");
	}

	public void queuemsg(Message msg) {
		msg.seq = tseq;
		tseq = (tseq + 1) % 65536;
		synchronized(pending) {
			pending.add(msg);
		}
		synchronized(sworker) {
			sworker.notify();
		}
	}

	public Message getuimsg() {
		synchronized(uimsgs) {
			if(uimsgs.size() == 0)
				return(null);
			return(uimsgs.remove());
		}
	}

	public void sendmsg(Message msg) {
		byte[] buf = new byte[msg.blob.length + 1];
		buf[0] = (byte)msg.type;
		System.arraycopy(msg.blob, 0, buf, 1, msg.blob.length);
		sendmsg(buf);
	}

	public void sendmsg(byte[] msg) {
		try {
			sk.send(new DatagramPacket(msg, msg.length, server));
		} catch(IOException e) {
		}
	}

	@Override
	public Glob glob() {
		return glob;
	}

	@Override
	public List<ItemInfo> info() {
		return null;
	}
}
