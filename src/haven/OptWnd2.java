/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Bjorn Johannessen <johannessen.bjorn@gmail.com>
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

import haven.GLSettings.BoolSetting;
import haven.minimap.ConfigGroup;
import haven.minimap.ConfigMarker;
import haven.minimap.MarkerFactory;
import haven.minimap.RadarConfig;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.*;
import java.util.List;

public class OptWnd2 extends Window {
    public static final RichText.Foundry foundry = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 10);
    public static OptWnd2 instance = null;
    private final CheckBox gob_path_color;
    private Tabs body;
    private Tabs.Tab radartab;
    private static RadarConfig rc=null;
    private static MarkerFactory mf=null;
    private String curcam;
    private Map<String, CamInfo> caminfomap = new HashMap<String, CamInfo>();
    private Map<String, String> camname2type = new HashMap<String, String>();
    private Comparator<String> camcomp = new Comparator<String>() {
	public int compare(String a, String b) {
	    if(a.startsWith("The ")) a = a.substring(4);
	    if(b.startsWith("The ")) b = b.substring(4);
	    return(a.compareTo(b));
	}
    };
    CheckBox opt_shadow;
    CheckBox opt_aa;
    CheckBox opt_qw;
    CheckBox opt_sb;
    CheckBox opt_flight;
    CheckBox opt_cel;
    CheckBox opt_show_tempers;
    
    private static class CamInfo {
	String name, desc;
	Tabs.Tab args;
	
	public CamInfo(String name, String desc, Tabs.Tab args) {
	    this.name = name;
	    this.desc = desc;
	    this.args = args;
	}
    }

    public OptWnd2(Coord c, Widget parent) {
	super(c, new Coord(500, 360), parent, "Options");
	justclose = true;
	body = new Tabs(Coord.z, new Coord(500, 360), this) {
		public void changed(Tab from, Tab to) {
		    Utils.setpref("optwndtab", to.btn.text.text);
		    from.btn.c.y = 0;
		    to.btn.c.y = -2;
		}};
	Widget tab;

	{ /* GENERAL TAB */
	    tab = body.new Tab(new Coord(0, 0), 60, "General");
	    new Button(new Coord(0, 30), 125, tab, "Quit") {
		public void click() {
		    HackThread.tg().interrupt();
		}};
	    new Button(new Coord(135, 30), 125, tab, "Switch character") {
		public void click() {
		    ui.gui.act("lo", "cs");
		}};
	    new Button(new Coord(0, 60), 125, tab, "Log out") {
		public void click() {
		    ui.gui.act("lo");
		}};
	    /*
	    new Button(new Coord(10, 100), 125, tab, "Toggle fullscreen") {
		public void click() {
		    if(ui.fsm != null) {
			if(ui.fsm.hasfs()) ui.fsm.setwnd();
			else               ui.fsm.setfs();
		    }
		}};
	    */

	    Widget editbox = new Frame(new Coord(310, 30), new Coord(90, 100), tab);
	    new Label(new Coord(20, 10), editbox, "Edit mode:");
	    RadioGroup editmode = new RadioGroup(editbox) {
		    public void changed(int btn, String lbl) {
			Utils.setpref("editmode", lbl.toLowerCase());
		    }};
	    editmode.add("Emacs", new Coord(10, 25));
	    editmode.add("PC",    new Coord(10, 50));
	    if(Utils.getpref("editmode", "pc").equals("emacs")) editmode.check("Emacs");
	    else                                                editmode.check("PC");
	    
	    int y = 100;
	    opt_show_tempers = new CheckBox(new Coord(0, y), tab, "Always show humor numbers"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.show_tempers = val;
		    Utils.setprefb("show_tempers", val);
		}
	    };
	    opt_show_tempers.a = Config.show_tempers;
	    //opt_show_tempers.enabled = Config.plain_tempers;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Store minimap"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.store_map = val;
		    Utils.setprefb("store_map", val);
		    if(val)ui.gui.mmap.cgrid = null;
		}
	    }.a = Config.store_map;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Study protection"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.flower_study = val;
		    Utils.setprefb("flower_study", val);
		}

		{tooltip = Text.render("Leave only 'Study' option in right-click menus, if they have one.");}
		
	    }.a = Config.flower_study;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Show aether as multiplier"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.pure_mult = val;
		    Utils.setprefb("pure_mult", val);
		}

		{tooltip = Text.render("Makes aether be displayed as the effective multiplier rather than the percentage.");}
		
	    }.a = Config.pure_mult;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Radar icons"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.radar_icons = val;
		    Utils.setprefb("radar_icons", val);
		}

		{tooltip = Text.render("Objects detected by radar will be shown by icons, if available");}
		
	    }.a = Config.radar_icons;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Blink radar objects"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.blink = val;
		    Utils.setprefb("blink", val);
		}

		{tooltip = Text.render("Objects detected by radar will blink");}
		
	    }.a = Config.blink;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Take screenshots silently"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.ss_silent = val;
		    Utils.setprefb("ss_slent", val);
		}

		{tooltip = Text.render("Screenshots will be taken without showing screenshot dialog");}
		
	    }.a = Config.ss_silent;
	    
	    new CheckBox(new Coord(200, y), tab, "Compress screenshots"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.ss_compress = val;
		    Utils.setprefb("ss_compress", val);
		}

		{tooltip = Text.render("Compressed screenshots use .JPEG, non-compressed .PNG");}
		
	    }.a = Config.ss_compress;
	    
	    new CheckBox(new Coord(200, y + 25), tab, "Include UI on screenshots"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.ss_ui = val;
		    Utils.setprefb("ss_ui", val);
		}

		{tooltip = Text.render("Sets default value of include UI on screenshot dialog");}
		
	    }.a = Config.ss_ui;

	    new CheckBox(new Coord(0, y += 25), tab, "Show weight widget"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.weight_wdg = val;
		    Utils.setprefb("weight_wdg", val);
		}

		{tooltip = Text.render("Shows small floating widget with current carrying weight");}

	    }.a = Config.weight_wdg;
	    
	    new CheckBox(new Coord(0, y += 25), tab, "Arrow home pointer"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
                    Config.hptr = val;
                    Utils.setprefb("hptr", val);
		    ui.gui.mainmenu.pv = Config.hpointv && !val; 
		}

		{tooltip = Text.render("Makes home pointer display as green arrow over character head");}
	    }.a = Config.hptr;
            
	    new CheckBox(new Coord(0, y += 25), tab, "Crafting menu resets"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
                    Config.menugrid_resets = val;
                    Utils.setprefb("menugrid_resets", val);
		}

		{tooltip = Text.render("Makes the crafting menu reset after selecting a recipe.");}
	    }.a = Config.menugrid_resets;
            
	    y = 125;
	    new CheckBox(new Coord(200, y += 25), tab, "Show item contents as icons"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.show_contents_icons = val;
		    Utils.setprefb("show_contents_icons", val);
		}

		{tooltip = Text.render("draws small icons of content of seed and flour bags");}
	    }.a = Config.show_contents_icons;
	    
	    new CheckBox(new Coord(200, y += 25), tab, "Auto open craft window"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.autoopen_craftwnd = val;
		    Utils.setprefb("autoopen_craftwnd", val);
		}

		{tooltip = Text.render("Makes craft window open if you click on any crafting item in menugrid or toolbelt.");}
	    }.a = Config.autoopen_craftwnd;

            
	    new CheckBox(new Coord(200, y += 25), tab, "Show gobble meters"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.gobble_meters = val;
		    Utils.setprefb("gobble_meters", val);
		}

		{tooltip = Text.render("During gobbling displays meters that show food efficiency.");}
	    }.a = Config.gobble_meters;
            
	    new CheckBox(new Coord(200, y += 25), tab, "Translate"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.translate = val;
		    Utils.setprefb("translate", val);
		}

		{tooltip = Text.render("Translate texts using trans.txt.");}
	    }.a = Config.translate;
            
	}

	
	{ //-* CAMERA TAB *-
	    curcam = Utils.getpref("defcam", MapView.DEFCAM);
	    tab = body.new Tab(new Coord(70, 0), 60, "Camera");

	    new Label(new Coord(10, 30), tab, "Camera type:");
	    final RichTextBox caminfo = new RichTextBox(new Coord(180, 25), new Coord(210, 180), tab, "", foundry);
	    caminfo.bg = new java.awt.Color(0, 0, 0, 64);
	    addinfo("ortho",	"Isometric Cam",	"Isometric camera centered on character. Use mousewheel scrolling to zoom in and out. Drag with middle mouse button to rotate camera.", null);
	    addinfo("sortho",	"Smooth Isometric Cam",	"Isometric camera centered on character with smoothed movement. Use mousewheel scrolling to zoom in and out. Drag with middle mouse button to rotate camera.", null);
	    addinfo("follow",	"Follow Cam",		"The camera follows the character. Use mousewheel scrolling to zoom in and out. Drag with middle mouse button to rotate camera.", null);
	    addinfo("sfollow",	"Smooth Follow Cam",	"The camera smoothly follows the character. Use mousewheel scrolling to zoom in and out. Drag with middle mouse button to rotate camera.", null);
	    addinfo("free",	"Freestyle Cam",	"You can move around freely within the larger area around character. Use mousewheel scrolling to zoom in and out. Drag with middle mouse button to rotate camera.", null);
	    addinfo("best",	"Smooth Freestyle Cam",	"You can move around freely within the larger area around character. Use mousewheel scrolling to zoom in and out. Drag with middle mouse button to rotate camera.", null);

	    final Tabs cambox = new Tabs(new Coord(100, 60), new Coord(300, 200), tab);
	    
	    final RadioGroup cameras = new RadioGroup(tab) {
		    public void changed(int btn, String lbl) {
			if(camname2type.containsKey(lbl))
			    lbl = camname2type.get(lbl);
			if(!lbl.equals(curcam)) {
			    setcamera(lbl);
			}
			CamInfo inf = caminfomap.get(lbl);
			if(inf == null) {
			    cambox.showtab(null);
			    caminfo.settext("");
			} else {
			    cambox.showtab(inf.args);
			    caminfo.settext(String.format("$size[12]{%s}\n\n$col[200,175,150,255]{%s}", inf.name, inf.desc));
			}
		    }};
	    List<String> clist = new ArrayList<String>();
	    for(String camtype : MapView.camtypes.keySet())
		clist.add(caminfomap.containsKey(camtype) ? caminfomap.get(camtype).name : camtype);
	    Collections.sort(clist, camcomp);
	    int y = 25;
	    for(String camname : clist)
		cameras.add(camname, new Coord(10, y += 25));
	    cameras.check(caminfomap.containsKey(curcam) ? caminfomap.get(curcam).name : curcam);

	    y+=40;
	    new CheckBox(new Coord(5, y), tab, "Rotate isometric cams by steps"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.isocam_steps = val;
		    Utils.setprefb("isocam_steps", val);
		    if(ui.gui != null && ui.gui.map != null && ui.gui.map.camera != null){
			ui.gui.map.camera.fixangle();
		    }
		}

		{tooltip = Text.render("Makes isometric cameras rotate in 90 degree steps.");}
	    }.a = Config.isocam_steps;

	    y = 200;
	    
	    opt_aa = new CheckBox(new Coord(180, y+=25), tab, "Antialiasing"){
		@Override
		public void set(boolean val) {
		    try {
			Config.glcfg.fsaa.set(val);
		    } catch(GLSettings.SettingException e) {
			val = false;
			getparent(GameUI.class).error(e.getMessage());
			return;
		    }
		    a = val;
		    Config.fsaa = val;
		    Config.glcfg.save();
		    Config.saveOptions();
		}
	    };
	    opt_aa.a = Config.fsaa;//Config.glcfg.fsaa.val;
	    checkVideoOpt(opt_aa, Config.glcfg.fsaa);
	    
	    opt_qw = new CheckBox(new Coord(180, y+=25), tab, "Quality water"){
		@Override
		public void set(boolean val) {
		    try {
			Config.glcfg.wsurf.set(val);
		    } catch(GLSettings.SettingException e) {
			val = false;
			getparent(GameUI.class).error(e.getMessage());
			return;
		    }
		    a = val;
		    Config.water = val;
		    Config.glcfg.save();
		    Config.saveOptions();
		}
	    };
	    opt_qw.a = Config.water;
	    checkVideoOpt(opt_qw, Config.glcfg.wsurf, Text.render("If character textures glitch, try turning Per-pixel lighting on."));
	    
	    opt_sb = new CheckBox(new Coord(180, y+=25), tab, "Skybox"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.skybox = val;
		    Utils.setprefb("skybox", val);
		}

		{tooltip = Text.render("Display the skybox.");}
		
	    };
            opt_sb.a = Config.skybox;
	    
	    y = 200;
	    int x = 290;
	    opt_flight = new CheckBox(new Coord(x, y+=25), tab, "Per-pixel lighting"){
		@Override
		public void set(boolean val) {
		    try {
			Config.glcfg.flight.set(val);
			if(!val){
			    Config.glcfg.flight.set(false);
			    Config.glcfg.cel.set(false);
			    Config.shadows = opt_shadow.a = false;
			    Config.cellshade = opt_cel.a = false;
			}
		    } catch(GLSettings.SettingException e) {
			val = false;
			getparent(GameUI.class).error(e.getMessage());
			return;
		    }
		    a = val;
		    Config.flight = val;
		    Config.glcfg.save();
		    Config.saveOptions();
		    checkVideoOpt(opt_shadow, Config.glcfg.lshadow);
		    checkVideoOpt(opt_cel, Config.glcfg.cel);
		}
		
	    };
	    opt_flight.a = Config.flight;
	    checkVideoOpt(opt_flight, Config.glcfg.flight, Text.render("Also known as per-fragment lighting"));
	    
	    opt_shadow = new CheckBox(new Coord(x, y+=25), tab, "Shadows"){
		@Override
		public void set(boolean val) {
		    try {
			Config.glcfg.lshadow.set(val);
		    } catch(GLSettings.SettingException e) {
			val = false;
			getparent(GameUI.class).error(e.getMessage());
			return;
		    }
		    a = val;
		    Config.shadows = val;
		    Config.glcfg.save();
		    Config.saveOptions();
		}
	    };
	    opt_shadow.a = Config.shadows;
	    checkVideoOpt(opt_shadow, Config.glcfg.lshadow);
	    
	    opt_cel = new CheckBox(new Coord(x, y+=25), tab, "Cel-shading"){
		@Override
		public void set(boolean val) {
		    try {
			Config.glcfg.cel.set(val);
		    } catch(GLSettings.SettingException e) {
			val = false;
			getparent(GameUI.class).error(e.getMessage());
			return;
		    }
		    a = val;
		    Config.cellshade = val;
		    Config.glcfg.save();
		    Config.saveOptions();
		}
	    };
	    opt_cel.a = Config.cellshade;
	    checkVideoOpt(opt_cel, Config.glcfg.cel);
	    
	    y = tab.sz.y - 50;
	    new Label(new Coord(10, y), tab, "Camera FOV:");
	    new HSlider(new Coord(85, y+5), 200, tab, 0, 1000, (int)(Config.camera_field_of_view * 1000)) {
		public void changed() {
                    Config.camera_field_of_view = val/1000.0f;
		    Utils.setpreff("camera_field_of_view",  val/1000.0f);
                    if(ui != null && ui.gui != null && ui.gui.map.camera != null)
                        ui.gui.map.camera.resized();
		}
	    };
            
	    y = tab.sz.y - 20;
	    new Label(new Coord(10, y), tab, "Brightness:");
	    new HSlider(new Coord(85, y+5), 200, tab, 0, 1000, (int)(Config.brighten * 1000)) {
		public void changed() {
		    Config.setBrighten(val/1000.0f);
		    ui.sess.glob.brighten();
		}
	    };
	}
	

	{ /* AUDIO TAB */
	    tab = body.new Tab(new Coord(140, 0), 60, "Audio");

            int y = 30;
            new Label(new Coord(0, y), tab, "Audio volume");
            y += 20;
            new HSlider(new Coord(0, y), 200, tab, 0, 1000, (int)(Audio.volume * 1000)) {
                public void changed() {
                    Audio.setvolume(val / 1000.0);
                }
            };
            y += 30;
            new Label(new Coord(0, y), tab, "Music volume");
            y += 20;
            new HSlider(new Coord(0, y), 200, tab, 0, 1000, (int)(Music.volume * 1000)) {
                public void changed() {
                    Music.setvolume(val / 1000.0);
                }
            };
	}

        { /* LATIKAI TAB */
            tab = body.new Tab(new Coord(210,0), 60, "Latikai");
            
            //project overgrown
	    new CheckBox(new Coord(0, 35), tab, "Enable 1x1 field fix"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.fieldfix = val;
		    Utils.setprefb("fieldfix", val);
		}

		{tooltip = Text.render("Only show a single instance of a crop, at the center of its field.");}
		
	    }.a = Config.fieldfix;
            final Label cropscale = new Label(new Coord(0, 60), tab, "Crop scaling: x"+Config.fieldproducescale);
	    new HSlider(new Coord(15, 80), 200, tab, 0, 9, Config.fieldproducescale-1) {
		public void changed() {
		    Config.setFieldproducescale(val+1);
                    cropscale.settext("Crop scaling: x"+Config.fieldproducescale);
		}
	    };
            
            //project marathon
	    new CheckBox(new Coord(0, 100), tab, "Show gob paths"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.showgobpath = val;
		    Utils.setprefb("showgobpath", val);
		}

		{tooltip = Text.render("Show paths of moving entities of the world.");}
		
	    }.a = Config.showgobpath;
            
            //project purity
	    new CheckBox(new Coord(0, 120), tab, "Always show purity percentage/multiplier"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.alwaysshowpurity = val;
		    Utils.setprefb("alwaysshowpurity", val);
		}

		{tooltip = Text.render("Always shows the purity on inventory items (as a percentage or as a multiplier, depending on the setting).");}
		
	    }.a = Config.alwaysshowpurity;
            
            //project free the camera
	    new CheckBox(new Coord(0, 140), tab, "Laptop mode for the mouse"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.laptopcontrols = val;
		    Utils.setprefb("laptopcontrols", val);
		}

		{tooltip = Text.render("Switches the mode for the mouse and world interactions");}
		
	    }.a = Config.laptopcontrols;
            
            final Label laptopcontrol1 = new Label(new Coord(10, 160), tab, "Laptop controls: Move the camera by pressing LMB, then dragging RMB.");
            final Label laptopcontrol2 = new Label(new Coord(10, 170), tab, "Zoom in with + and out with -, and rotate objects like that while pressing shift.");
            final Label laptopcontrol3 = new Label(new Coord(10, 180), tab, "Rotate in precise mode by pressing shift-alt rather than shift-ctrl.");
            
            //project raider trees
	    new CheckBox(new Coord(0, 200), tab, "Raider mode trees"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.raidermodetrees = val;
		    Utils.setprefb("raidermodetrees", val);
		}

		{tooltip = Text.render("All trees are rendered as tiny versions of themselves. Re-load your area after changing.");}
		
	    }.a = Config.raidermodetrees;
            
            //project raider braziers
	    new CheckBox(new Coord(150, 200), tab, "Raider mode braziers"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.raidermodebraziers = val;
		    Utils.setprefb("raidermodebraziers", val);
		}

		{tooltip = Text.render("Braziers are rendered in hot pink.");}
		
	    }.a = Config.raidermodebraziers;
            
            //project raider braziers
	    new CheckBox(new Coord(300, 200), tab, "Farmer mode trees/bushes"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.farmermodetrees = val;
		    Utils.setprefb("farmermodetrees", val);
		}

		{tooltip = Text.render("Fruit-bearing trees and flowered thornbushes are made clear.");}
		
	    }.a = Config.farmermodetrees;
            
            //project climber
	    new CheckBox(new Coord(150, 220), tab, "Show ridges on the minimap."){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.localmm_ridges = val;
		    Utils.setprefb("localmm_ridges", val);
		}
	    }.a = Config.localmm_ridges;
            
            //project ironborn
	    new CheckBox(new Coord(0, 220), tab, "Alternate prospecting"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.altprosp = val;
		    Utils.setprefb("altprosp", val);
		}

		{tooltip = Text.render("Shows the rough direction and the pie slice to search in, rather than the erratic arrow. Give it some time!");}
		
	    }.a = Config.altprosp;
            
            //project lazy tracker
	    new CheckBox(new Coord(0, 240), tab, "Enable tracking on log-in"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.alwaystrack = val;
		    Utils.setprefb("alwaystrack", val);
		}

		{tooltip = Text.render("Enable tracking as soon as a character logs in.");}
		
	    }.a = Config.alwaystrack;
            
            //project silent witness
	    new CheckBox(new Coord(0, 260), tab, "Lower framerate on unfocused instances"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.slowmin = val;
		    Utils.setprefb("slowmin", val);
		}

		{tooltip = Text.render("Lowers the target framerate for unfocused windows from 50 to 10.");}
		
	    }.a = Config.slowmin;
            
            //project awareness
	    new CheckBox(new Coord(0, 280), tab, "Always face the primary target"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.watchguard = val;
		    Utils.setprefb("watchguard", val);
		}

		{tooltip = Text.render("Always face the target at the top of the aggro list. WARNING: will not work when not rendering, e.g. when minimized.");}
		
	    }.a = Config.watchguard;
            
            //project patience
	    new CheckBox(new Coord(0, 300), tab, "Fast flower menus"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.fast_menu = val;
		    Utils.setprefb("fast_flowers", val);
		}

		{tooltip = Text.render("Get rid of the delays when opening flower menus.");}
		
	    }.a = Config.fast_menu;
            
            //project silent lamb
	    new CheckBox(new Coord(0, 320), tab, "Mute the violin player"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.mute_violin = val;
		    Utils.setprefb("mute_violin", val);
		}

		{tooltip = Text.render("The violin player will lose his strings. Please remember that he has a family of his own to support!");}
		
	    }.a = Config.mute_violin;
            
            //project librarian
            //chatlogs
	    new CheckBox(new Coord(0, 340), tab, "Log all chat messages to file"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.chatlogs = val;
		    Utils.setprefb("chatlogs", val);
		}

		{tooltip = Text.render("Chat messages will be available in \"C:\\Users\\<account>\\Salem\\logs\\<character>\\<channel>\\\" or similar.\nChanges only apply to new channels.");}
		
	    }.a = Config.chatlogs;
            
            //scalable chat UI
	    final RadioGroup fontsizes = new RadioGroup(tab) {
                    public void changed(int btn, String lbl) {
                        int basesize = 12;
                        if(lbl.equals("Base size 14"))
                        {
                            basesize=14;
                        }
                        else if(lbl.equals("Base size 16"))
                        {
                            basesize=16;
                        }
                        else if(lbl.equals("Base size 20"))
                        {
                            basesize=20;
                        }
                        OptWnd2.this.ui.gui.chat.setbasesize(basesize);
                        Utils.setpreff("chatfontsize", basesize);
                    }
                };
            new Label(new Coord(280,40),tab, "Chat font size:");
            fontsizes.add("Base size 12", new Coord(300,60));
            fontsizes.add("Base size 14", new Coord(300,85));
            fontsizes.add("Base size 16", new Coord(300,110));
            fontsizes.add("Base size 20", new Coord(300,135));
            int basesize = (int) Utils.getpreff("chatfontsize", 12);
	    fontsizes.check("Base size "+basesize);	    
            
            
	    new CheckBox(new Coord(300, 240), tab, "Remove all animations"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.remove_animations = val;
		    Utils.setprefb("remove_animations", val);
		}

		{tooltip = Text.render("Removes all animations of more than a single frame, should ease processing times.");}
		
	    }.a = Config.remove_animations;
            //not drawing parts of the UI
            new CheckBox(new Coord(300, 260), tab, "Hide the minimap"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.hide_minimap = val;
		    Utils.setprefb("hide_minimap", val);
                    this.ui.gui.updateRenderFilter();
		}

		{tooltip = Text.render("The minimap will not be rendered.");}
		
	    }.a = Config.hide_minimap;
            new CheckBox(new Coord(300, 280), tab, "Hide the humours"){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.hide_tempers = val;
		    Utils.setprefb("hide_tempers", val);
                    this.ui.gui.updateRenderFilter();
		}

		{tooltip = Text.render("The humours will not be rendered.");}
		
	    }.a = Config.hide_tempers;
            //sorting toggle
            new CheckBox(new Coord(300, 300), tab, "Enable continuous sorting."){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.alwayssort = val;
		    Utils.setprefb("alwayssort", val);
		}

		{tooltip = Text.render("Toggle between on-demand sorting and continuous sorting.");}
		
	    }.a = Config.alwayssort;
            //picky food transfer-same toggle
            new CheckBox(new Coord(300, 320), tab, "Picky Alt modifier."){
		@Override
		public void changed(boolean val) {
		    super.changed(val);
		    Config.pickyalt = val;
		    Utils.setprefb("pickyalt", val);
		}

		{tooltip = Text.render("The alt modifier will now only take items which contain the same ingredients, as well as having the same name.");}
		
	    }.a = Config.pickyalt;
        }
        
        /* RADAR TAB */
        makeRadarTab();
        
        /* HOTKEY TAB */
        {
            
	    tab = body.new Tab(new Coord(360, 0), 60, "Hotkeys");
            
            new Label(new Coord(10, 25), tab, "Enter commands to execute for configureable hotkeys.");
            new Label(new Coord(10, 35), tab, "Use your hotkeys through shift+ctrl+<key>.");
            new Label(new Coord(10, 45), tab, "Only single-character capitalized hotkeys will work.");
            new Label(new Coord(10, 55), tab, "Hotkeys in use by the UI itself are fixed and immutable.");
            
            
            int y = 85;
            for(int i = 0; i < Config.hotkeynr; i++)
            {
                final String hname = String.format("hotkey%d", i+1);
                final String hcommand = String.format("command%d", i+1);
                final int idx = i;
                
                String lt = String.format("Hotkey %d:", i+1);
                new Label(new Coord(10, y), tab, lt);
                new TextEntry(new Coord(60, y), 30, tab, Config.hnames[i])
                {
                    @Override
                    protected void changed()
                    {
                        if(super.text.length() > 0)
                        {
                            Utils.setpref(hname, super.text.substring(0,1));
                            Config.hnames[idx] = super.text;
                        }
                    }
                };
                
                lt = String.format("Command %d:", i+1);
                new Label(new Coord(105, y), tab, lt);
                new TextEntry(new Coord(170, y), 150, tab, Config.hcommands[i])
                {
                    @Override
                    protected void changed()
                    {
                        if(super.text.length() > 0)
                        {
                            Utils.setpref(hcommand, super.text);
                            Config.hcommands[idx] = super.text;
                        }
                    }
                };
                
                y+= 25;
            }
        }
        
        {
            tab = body.new Tab(new Coord(430, 0), 60, "Cheats"){
                FlowerList list = new FlowerList(new Coord(200, 55), this);
                Button add = new Button(new Coord(355, 308), 45, this, "Add");
                TextEntry value = new TextEntry(new Coord(200, 310), 150, this, "");
                {
                    value.canactivate = true;
                }

                @Override
                public void wdgmsg(Widget sender, String msg, Object... args) {
                    if((sender == add || sender == value) && msg.equals("activate")){
                        list.add(value.text);
                        value.settext("");
                    } else {
                        super.wdgmsg(sender, msg, args);
                    }
                }
            };
            new Label(new Coord(200, 30), tab, "Choose menu items to select automatically:");

            int y = 5;
            (new CheckBox(new Coord(0, y+=25), tab, "Auto sift"){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    Config.autosift = val;
                    Utils.setprefb("autosift", val);
                }
                {tooltip = Text.render("Clicks on ground with sift cursor will be repeated until non-sift click received.");}
            }).a = Config.autosift;

            (new CheckBox(new Coord(0, y+=25), tab, "Show actor path"){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    Config.gobpath = val;
                    Utils.setprefb("gobpath", val);
                    gob_path_color.enabled = val;
                }
                {tooltip = Text.render("Will draw line to position where actor is moving.");}
            }).a = Config.gobpath;

            gob_path_color = new CheckBox(new Coord(10, y+=25), tab, "Use kin color"){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    Config.gobpath_color = val;
                    Utils.setprefb("gobpath_color", val);
                }
                {tooltip = Text.render("Will draw actor path using color from kin list.");}
            };
            gob_path_color.a = Config.gobpath_color;
            gob_path_color.enabled = Config.gobpath;

            new Button(new Coord(10, y+=25), 75, tab, "options"){
                @Override
                public void click() {
                    GobPathOptWnd.toggle();
                }
            };

            (new CheckBox(new Coord(0, y+=35), tab, "Auto drop bats"){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    Config.auto_drop_bats = val;
                    Utils.setprefb("auto_drop_bats", val);
                }
                {tooltip = Text.render("Will automatically drop bats that sit on your neck.");}
            }).a = Config.auto_drop_bats;
            
            (new CheckBox(new Coord(0, y+=35), tab, "Auto logout"){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    Config.autolog = val;
                    Utils.setprefb("autolog", val);
                }
                {tooltip = Text.render("Will automatically log you out if you do not interact with the client for 10 minutes.");}
            }).a = Config.autolog;
            
            new CheckBox(new Coord(0, y+=35), tab, "Single item CTRL choose"){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    Config.singleItemCTRLChoose = val;
                    Utils.setprefb("singleItemCTRLChoose", val);
                }
                {tooltip = Text.render("If checked, will automatically select single item menus if CTRL is pressed when menu is opened.");}
            }.a = Config.singleItemCTRLChoose;
        }
        
	//new Frame(new Coord(-10, 20), new Coord(420, 330), this);
	String last = Utils.getpref("optwndtab", "");
	for(Tabs.Tab t : body.tabs) {
	    if(t.btn.text.text.equals(last))
		body.showtab(t);
	}
    }
    
    public static void setRadarInfo(RadarConfig rcf, MarkerFactory mf){
        OptWnd2.rc = rcf;
        OptWnd2.mf = mf;
        if(instance == null) return;
        instance.makeRadarTab();
    }
    
    private void makeRadarTab()
    {
        if(rc==null) return;
        
        boolean viewingradartab = body.curtab == radartab;
        //remove the old tab
        if(radartab !=null)
        {
            boolean success = body.tabs.remove(radartab);
            System.out.println("Removed the radartab step 1: "+success);
            radartab.unlink();
            radartab.btn.destroy();
            radartab.destroy();
        }
        
        //create the new one
        radartab = body.new Tab(new Coord(280,0), 70, "Radar config");

        int x = 0, y = 35;
        for(final ConfigGroup cg : rc.getGroups())
        {
            new CheckBox(new Coord(x, y), radartab, cg.name){
                @Override
                public void changed(boolean val) {
                    super.changed(val);
                    cg.show = val;
                    for(ConfigMarker cm : cg.markers)
                        cm.show = val;
                    if(mf != null)
                        mf.setConfig(rc);
                }		
            }.a = cg.show;
            y+=25;
            if(y > radartab.sz.y)
            {
                y = 35;
                x += 100;
            }
        }
        
        if(viewingradartab) body.showtab(radartab);
    }
   
    private static void checkVideoOpt(CheckBox check, BoolSetting setting){
	checkVideoOpt(check, setting, null);
    }
    
    private static void checkVideoOpt(CheckBox check, BoolSetting setting, Object tooltip){
	try {
	    setting.validate(true);
	    check.enabled = true;
	    check.tooltip = tooltip;
	} catch(GLSettings.SettingException e) {
	    check.enabled = false;
	    check.tooltip = Text.render(e.getMessage());
	}
    }


    private void setcamera(String camtype) {
	curcam = camtype;
	Utils.setpref("defcam", curcam);

	MapView mv = ui.gui.map;
	if(mv != null) {
	    mv.setcam(curcam);
	}
    }

    private int getsfxvol() {
	return((int)(100 - Double.parseDouble(Utils.getpref("sfxvol", "1.0")) * 100));
    }

    private void addinfo(String camtype, String title, String text, Tabs.Tab args) {
	caminfomap.put(camtype, new CamInfo(title, text, args));
	camname2type.put(title, camtype);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == cbtn)
	    super.wdgmsg(sender, msg, args);
    }

    public static class Frame extends Widget {
	private IBox box;
	private Color bgcoplor;

	public Frame(Coord c, Coord sz, Widget parent) {
	    super(c, sz, parent);
	    box = new IBox("gfx/hud", "tl", "tr", "bl", "br", "extvl", "extvr", "extht", "exthb");
	}
	
	public Frame(Coord c, Coord sz, Color bg, Widget parent) {
	   this(c, sz, parent);
	   bgcoplor = bg;
	}

	public void draw(GOut og) {
	    GOut g = og.reclip(Coord.z, sz);
	    if(bgcoplor != null){
		g.chcolor(bgcoplor);
		g.frect(box.btloff(), sz.sub(box.bisz()));
	    }
	    g.chcolor(150, 200, 125, 255);
	    box.draw(g, Coord.z, sz);
	    super.draw(og);
	}
    }

    public static void toggle() {
	UI ui = UI.instance;
	if(instance == null){
	    instance = new OptWnd2(Coord.z, ui.gui);
	} else {
	    ui.destroy(instance);
	}
    }

    @Override
    public void destroy() {
	instance = null;
	super.destroy();
    }

    public static void close() {
	if(instance != null){
	    UI ui = UI.instance;
	    ui.destroy(instance);
	}
    }
}