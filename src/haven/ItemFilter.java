package haven;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static haven.Tempers.anm;
import static haven.Tempers.rnm;

public abstract class ItemFilter {
    private static final Pattern q = Pattern.compile("(?:(?<tag>\\w+):)?(?<text>[\\w\\*]+)(?:(?<sign>[<>=])(?<value>\\d+(?:\\.\\d+)?)?)?");
    public boolean matches(List<ItemInfo> info){
	for(ItemInfo item : info){
	    String className = item.getClass().getCanonicalName();
	    if(item instanceof ItemInfo.Name){
		if(match((ItemInfo.Name) item)){ return true;}
	    } else if(item instanceof FoodInfo){
		if(match((FoodInfo)item)){return true;}
	    } else if(item instanceof Inspiration){
		if(match((Inspiration)item)){return true;}
	    } else if(item instanceof GobbleInfo){
		if(match((GobbleInfo)item)){return true;}
	    } else if(className.equals("Slotted")){
	    }
	}
	return false;
    }

    protected boolean match(Inspiration item) {
	return false;
    }

    protected boolean match(GobbleInfo item) {
	return false;
    }

    protected boolean match(FoodInfo item) {
	return false;
    }

    protected boolean match(ItemInfo.Name item) {
	return false;
    }

    public static ItemFilter create(String query){
	Compound result = new Compound();
	Matcher m = q.matcher(query);
	while(m.find()){
	    String tag = m.group("tag");
	    String text = m.group("text");
	    String sign = m.group("sign");
	    String value = m.group("value");

	    ItemFilter filter = null;
	    if(tag == null){
		filter = new Text(text);
	    } else {
		if(tag.equals("heal")){
		    filter = new Heal(text, sign, value);
		} else if(tag.equals("xp")){
		    filter = new XP(text, sign, value);
		}
	    }
	    if(filter != null){
		result.add(filter);
	    }
	}
	return result;
    }

    public static class Compound extends ItemFilter{
	List<ItemFilter> filters = new LinkedList<ItemFilter>();
	@Override
	public boolean matches(List<ItemInfo> info) {
	    if(filters.isEmpty()){return false;}
	    for(ItemFilter filter : filters){
		if(!filter.matches(info)){return false;}
	    }
	    return true;
	}

	public void add(ItemFilter filter){
	    filters.add(filter);
	}
    }

    public static class Complex extends ItemFilter{
	protected final String text;
	protected final Sign sign;
	protected float value;
	protected final boolean all;
	protected final boolean any;

	public Complex(String text, String sign, String value){
	    this.text = text;
	    this.sign = getSign(sign);
	    float tmp = 0;
	    try {
		tmp = Float.parseFloat(value);
	    } catch (Exception ignored){}
	    this.value = tmp;

	    all = text.equals("*") || text.equals("all");
	    any = text.equals("any");
	}

	protected boolean test(float actual, float target){
	    switch(sign){
		case GREATER	: return actual >= target;
		case LESS	: return actual <= target;
		case EQUAL	: return actual == target;
		default		: return actual > 0;
	    }
	}

	protected static Sign getSign(String sign){
	    if(sign == null){
		return Sign.DEFAULT;
	    }
	    if(sign.equals(">")){
		return Sign.GREATER;
	    } else if(sign.equals("<")){
		return Sign.LESS;
	    } else if(sign.equals("=")) {
		return Sign.EQUAL;
	    } else {
		return Sign.DEFAULT;
	    }
	}

	public static enum Sign {GREATER, LESS, EQUAL, DEFAULT}
    }

    public static class Heal extends Complex{

	public Heal(String text, String sign, String value) {
	    super(text, sign, value);
	    this.value = 1000*this.value;
	}

	@Override
	protected boolean match(FoodInfo item) {
	    int[] tempers = item.tempers;
	    if(all){
		for(int k = 0; k < anm.length; k++){
		    if(!test(tempers[k], value)){return false;}
		}
	    } else {
		for(int k = 0; k < anm.length; k++){
		    if(anm[k].equals(text) && test(tempers[k], value)){return true;}
		    if(rnm[k].toLowerCase().contains(text) && test(tempers[k], value)){return true;}
		}
	    }
	    return false;
	}
    }

    public static class XP extends Complex {

	public XP(String text, String sign, String value) {
	    super(text, sign, value);
	}

	@Override
	protected boolean match(Inspiration item) {
	    for(int k = 0; k<item.attrs.length; k++){
		boolean enough = test(item.exp[k], value);
		if((any || item.attrs[k].equals(text)) && enough){return true;}
		if((any || CharWnd.attrnm.get(item.attrs[k]).toLowerCase().contains(text)) && enough){return true;}
	    }
	    return false;
	}
    }

    public static class Text extends ItemFilter{
	private String text;

	public  Text(String text){
	    this.text = text.toLowerCase();
	}

	public void update(String text){
	    this.text = text.toLowerCase();
	}

	@Override
	protected boolean match(ItemInfo.Name item) {
	    return item.str.text.toLowerCase().contains(text);
	}

	@Override
	protected boolean match(FoodInfo item) {
	    for(int k = 0; k < anm.length; k++){
		boolean notEmpty = item.tempers[k] > 0;
		if(anm[k].equals(text) && notEmpty){return true;}
		if(rnm[k].toLowerCase().contains(text) && notEmpty){return true;}
	    }
	    return false;
	}

	@Override
	protected boolean match(GobbleInfo item) {
	    for(int k = 0; k < anm.length; k++){
		if(anm[k].equals(text) && item.h[k] > 0){return true;}
	    }
	    for(int k = 0; k < rnm.length; k++){
		if(rnm[k].toLowerCase().contains(text) && item.h[k] > 0){return true;}
	    }
	    return false;
	}

	@Override
	protected boolean match(Inspiration item) {
	    for(String attr : item.attrs){
		if(attr.equals(text)){return true;}
		if(CharWnd.attrnm.get(attr).toLowerCase().contains(text)){return true;}
	    }
	    return false;
	}
    }
}
