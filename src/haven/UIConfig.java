package haven;

import haven.minimap.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class UIConfig {
    public static final String def_config = Config.userhome+"/ui.xml";
    private final File file;
    private List<FontConfig> fonts = new ArrayList<FontConfig>();

    public UIConfig(String configfile) {
        this(new File(configfile));
    }

    public UIConfig(File configfile) {
        file = configfile;
        try {
            load();
            //add a new tab to the optwnd2...somehow...
            //remove the old one!
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public UIConfig() {
        this(new File(def_config));
    }

    private void load() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);

        fonts.clear();

        NodeList fontNodes = doc.getElementsByTagName("font");
        for (int i = 0; i < fontNodes.getLength(); i++) {
            FontConfig font = parseFont(fontNodes.item(i));
            if (font != null){
                fonts.add(font);
            }
        }
    }

    private FontConfig parseFont(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE)
            return null;
        Element el = (Element) node;

        FontConfig font = new FontConfig();
        font.target = el.getAttribute("target");
        font.family = el.getAttribute("family");
        font.color = Utils.parseColor(el.getAttribute("color"));
        font.size = Integer.parseInt(el.getAttribute("size"));
        font.aa = Boolean.parseBoolean(el.getAttribute("antialias"));
        switch(el.getAttribute("style")){
            case "BOLD":
                font.style = Font.BOLD;
                break;
            case "ITALIC":
                font.style = Font.ITALIC;
                break;
            default:
                font.style = Font.PLAIN;
                break;
        }
        System.out.println("Configured font "+font.target+" with family "+font.family+" color "+font.color+" size "+font.size);
        return font;
    }

    public FontConfig getFontConfig(String target){
        for(FontConfig fc : fonts){
            if (target.equals(fc.target)){
                return fc;
            }
        }
        FontConfig fc = new FontConfig();
        if (target.equals("buttonFont")) {
            fc.family = Font.SERIF;
            fc.size = 15;
            fc.color = new Color(248, 240, 193);
            fc.style = Font.PLAIN;
            fc.aa = true;
        }
        return fc;
    }
}
