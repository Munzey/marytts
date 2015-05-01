package marytts.language.en;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.modules.InternalModule;
import marytts.util.dom.MaryDomUtils;
import marytts.util.dom.NameNodeFilter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import com.ibm.icu.util.ULocale;
import com.ibm.icu.text.RuleBasedNumberFormat;

/**
 * @author Tristan Hamilton
 * 
 *         Can process following formats: 
 *         - cardinal (handled by real number)
 *         - ordinal 
 *         - year 
 *         - currency 
 *         - numberandword together
 *         - dashes (read each number singly) or (split into two words)
 *         - decimal points,minus (real numbers) also handles %
 *         - time
 *         
 *         Does not handle yet:
 *         - abbreviations (have a resource of known expansions?)
 *         - acronyms (should they be expanded? have a resource of known expansions? or just expand into single letters? both?)
 *         for any word that is ALL capitals or has d.o.t.s ?
 *         - contractions -> first check lexicon, if not then
 *         				  -> don't expand but split before punctuation into two tokens
 *         				  -> for 's if word ends in c,f,k,p,t then add ph = s otherwise ph = z
 *         - single "A" character
 *         
 *         May include:
 *         - roman numerals
 *         - durations
 */
public class Preprocess extends InternalModule {

	// icu4j stuff
	private RuleBasedNumberFormat rbnf;
	protected final String cardinalRule;
	protected final String ordinalRule;
	protected final String yearRule;

	// Regex matching patterns
	private static final Pattern moneyPattern;
	private static final Pattern timePattern;
	private static final Pattern durationPattern;
	private static final Pattern abbrevPattern;
	private static final Pattern acronymPattern;
	private static final Pattern realNumPattern;

	static {
		moneyPattern = Pattern.compile("(\\$|£|€)(\\d+)(\\.\\d+)?");
		timePattern = Pattern.compile("((0?[0-9])|(1[0-1])|(1[2-9])|(2[0-3])):([0-5][0-9])(a\\.m\\.|AM|PM|am|pm|p\\.m\\.)?");
		durationPattern = Pattern.compile("(\\d+):([0-5][0-9])(:[0-5][0-9])(:[0-5][0-9])?");
		abbrevPattern = Pattern.compile("\\w+\\.(\\w+)?");
		acronymPattern = Pattern.compile("(\\w\\.)+");
		realNumPattern = Pattern.compile("(-)?(\\d+)?(\\.(\\d+)(%)?)?");
	}

	public Preprocess() {
		super("Preprocess", MaryDataType.TOKENS, MaryDataType.WORDS, Locale.ENGLISH);
		this.rbnf = new RuleBasedNumberFormat(ULocale.ENGLISH, RuleBasedNumberFormat.SPELLOUT);
		this.cardinalRule = "%spellout-numbering";
		this.ordinalRule = getOrdinalRuleName(rbnf);
		this.yearRule = getYearRuleName(rbnf);
	}

	public MaryData process(MaryData d) throws Exception {
		Document doc = d.getDocument();
		expand(doc);
		MaryData result = new MaryData(getOutputType(), d.getLocale());
		result.setDocument(doc);
		return result;
	}

	protected void expand(Document doc) {
		boolean isYear;
		TreeWalker tw = ((DocumentTraversal) doc).createTreeWalker(doc, NodeFilter.SHOW_ELEMENT,
				new NameNodeFilter(MaryXML.TOKEN), false);
		Element t = null;
		while ((t = (Element) tw.nextNode()) != null) {
			isYear = true;
			if (MaryDomUtils.hasAncestor(t, MaryXML.SAYAS) || t.hasAttribute("ph") || t.hasAttribute("sounds_like")) {
				// ignore token
				continue;
			}
			String origText = MaryDomUtils.tokenText(t);
			// remove commas
			if (MaryDomUtils.tokenText(t).matches("[\\$|£|€]?\\d+,[\\d,]+")) {
				MaryDomUtils.setTokenText(t, MaryDomUtils.tokenText(t).replaceAll(",", ""));
				if (MaryDomUtils.tokenText(t).matches("\\d{4}")) {
					isYear = false;
				}
			}
			// ordinal
			if (MaryDomUtils.tokenText(t).matches("\\d+(st|nd|rd|th|ST|ND|RD|TH)")) {
				String matched = MaryDomUtils.tokenText(t).split("st|nd|rd|th|ST|ND|RD|TH")[0];
				MaryDomUtils.setTokenText(t, expandOrdinal(Double.parseDouble(matched)));
			// wordAndNumber
			} else if (MaryDomUtils.tokenText(t).contains("\\w+") && MaryDomUtils.tokenText(t).contains("\\d+")) {
				MaryDomUtils.setTokenText(t, expandWordNumber(MaryDomUtils.tokenText(t)));
			// year
			} else if (MaryDomUtils.tokenText(t).matches("\\d{4}") && isYear == true) {
				MaryDomUtils.setTokenText(t, expandYear(Double.parseDouble(MaryDomUtils.tokenText(t))));
			// real number
			} else if (MaryDomUtils.tokenText(t).matches(realNumPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandRealNumber(MaryDomUtils.tokenText(t)));
			// time
			} else if (MaryDomUtils.tokenText(t).matches(timePattern.pattern())) {
				Element testNode = MaryDomUtils.getNextOfItsKindIn(t, (Element) t.getParentNode());
				boolean nextTokenIsTime = false;
				if (testNode != null && MaryDomUtils.tokenText(testNode).matches("a\\.m\\.|AM|PM|am|pm|p\\.m\\.")) {
					nextTokenIsTime = true;
				}
				MaryDomUtils.setTokenText(t, expandTime(MaryDomUtils.tokenText(t), nextTokenIsTime));
			// duration
			} else if (MaryDomUtils.tokenText(t).matches(durationPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandDuration(MaryDomUtils.tokenText(t)));
			// currency
			} else if (MaryDomUtils.tokenText(t).matches(moneyPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandMoney(MaryDomUtils.tokenText(t)));
			// dashes 
			} else if (MaryDomUtils.tokenText(t).contains("-")) {
				String[] tokens = MaryDomUtils.tokenText(t).split("-");
				int i = 0;
				for(String tok:tokens){
					if (tok.matches("\\d+")) {
						String newTok = "";
						for(char c:tok.toCharArray()){
							newTok += expandNumber(Double.parseDouble(String.valueOf(c))) + " ";
						}
						tokens[i] = newTok;
					}
					i++;
				}
				MaryDomUtils.setTokenText(t, Arrays.toString(tokens).replaceAll("[,\\]\\[]", ""));
			}
			// if token isn't ignored but there is no handling rule don't add MTU
			if (!origText.equals(MaryDomUtils.tokenText(t))) {
				MaryDomUtils.encloseWithMTU(t, origText, null);
			}
			//finally, split new expanded token seperated by spaces into seperate tokens
			
		}
	}

	protected String expandNumber(double number) {
		this.rbnf.setDefaultRuleSet(cardinalRule);
		return this.rbnf.format(number);
	}

	protected String expandOrdinal(double number) {
		this.rbnf.setDefaultRuleSet(ordinalRule);
		return this.rbnf.format(number);
	}

	protected String expandYear(double number) {
		this.rbnf.setDefaultRuleSet(yearRule);
		return this.rbnf.format(number);
	}
	
	protected String expandDuration(String duration) {
		return null;
	}
	
	protected String expandTime(String time, boolean isNextTokenTime) {
		boolean pastNoon = false;
		String theTime = "";
		String hour = "";
		Double pmHour;
		Matcher timeMatch = timePattern.matcher(time);
		timeMatch.find();
		//hour
		if (timeMatch.group(2) != null || timeMatch.group(3) != null) {
			hour = (timeMatch.group(2) != null) ? timeMatch.group(2) : timeMatch.group(3);
			if (hour.equals("00")) {
				hour = "12";
			}
			theTime += expandNumber(Double.parseDouble(hour));
		}
		else {
			pastNoon = true;
			hour = (timeMatch.group(4) != null) ? timeMatch.group(4) : timeMatch.group(5);
			pmHour = Double.parseDouble(hour) - 12;
			if (pmHour == 0) {
				hour = "12";
				theTime += expandNumber(Double.parseDouble(hour));
			}
			else {
				theTime += expandNumber(pmHour);
			}	
		}
		//minutes
		if (timeMatch.group(7) != null && !isNextTokenTime) {
			if (!timeMatch.group(6).equals("00")) {
				if (timeMatch.group(6).matches("0\\d")) {
					theTime += " oh " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
				else {
					theTime += " " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
			}
			for (char c: timeMatch.group(7).replaceAll("\\.", "").toCharArray()){
				theTime += " " + c;
			}
		}
		else if (!isNextTokenTime) {
			if (!timeMatch.group(6).equals("00")) {
				if (timeMatch.group(6).matches("0\\d")) {
					theTime += " oh " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
				else {
					theTime += " " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
			}
			theTime += !pastNoon ? " a m" : " p m";
		}
		else {
			if (!timeMatch.group(6).equals("00")) {
				if (timeMatch.group(6).matches("0\\d")) {
					theTime += " oh " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
				else {
					theTime += " " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
			}
		}
		return theTime;
	}
	
	protected String expandRealNumber(String number) {
		Matcher realNumMatch = realNumPattern.matcher(number);
		realNumMatch.find();
		String newTok = "";
		if (realNumMatch.group(1) != null){
			newTok += "minus ";
		}
		if (realNumMatch.group(2) != null){
			newTok += expandNumber(Double.parseDouble(realNumMatch.group(2))) + " ";
		}
		if (realNumMatch.group(3) != null){
			newTok += "point ";
			for(char c:realNumMatch.group(4).toCharArray()){
				newTok += expandNumber(Double.parseDouble(String.valueOf(c))) + " ";
			}
			if (realNumMatch.group(5) != null){
				newTok += "per cent";
			}
		}
		return newTok.trim();
	}
	
	protected String expandWordNumber(String wordnumseq){
		String[] groups = wordnumseq.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
		int i = 0;
		for(String g:groups){
			if (g.matches("\\d+")) {
				groups[i] = expandNumber(Double.parseDouble(g));
			}
			i++;
		}
		return Arrays.toString(groups).replaceAll("[,\\]\\[]", "");
	}
	
	protected String expandMoney(String money) {
		String origText = money;
		Matcher currencyMatch = moneyPattern.matcher(money);
		currencyMatch.find();
		switch (currencyMatch.group(1)) {
		case "$":
			if (Double.parseDouble(currencyMatch.group(2)) > 1) {
				money = expandNumber(Double.parseDouble(currencyMatch.group(2))) + " dollars";
			} else {
				money = expandNumber(Double.parseDouble(currencyMatch.group(2))) + " dollar";
			}
			if (currencyMatch.group(3) != null) {
				int dotIndex = origText.indexOf('.');
				money = money + " " + expandNumber(Double.parseDouble(origText.substring(dotIndex + 1))) + " cents";
			}
			break;
		case "£":
			money = expandNumber(Double.parseDouble(currencyMatch.group(2))) + " pound sterling";
			if (currencyMatch.group(3) != null) {
				int dotIndex = origText.indexOf('.');
				money = money + " " + expandNumber(Double.parseDouble(origText.substring(dotIndex + 1))) + " pence";
			}
			break;
		case "€":
			money = expandNumber(Double.parseDouble(currencyMatch.group(2))) + " euro";
			if (currencyMatch.group(3) != null) {
				int dotIndex = origText.indexOf('.');
				money = money + " " + expandNumber(Double.parseDouble(origText.substring(dotIndex + 1))) + " cents";
			}
			break;
		default:
			break;
		}
		return money;
	}

	/**
	 * Try to extract the rule name for "expand ordinal" from the given RuleBasedNumberFormat.
	 * <p/>
	 * The rule name is locale sensitive, but usually starts with "%spellout-ordinal".
	 *
	 * @param rbnf
	 *            The RuleBasedNumberFormat from where we will try to extract the rule name.
	 * @return The rule name for "ordinal spell out".
	 */
	protected static String getOrdinalRuleName(final RuleBasedNumberFormat rbnf) {
		List<String> l = Arrays.asList(rbnf.getRuleSetNames());
		if (l.contains("%spellout-ordinal")) {
			return "%spellout-ordinal";
		} else if (l.contains("%spellout-ordinal-masculine")) {
			return "%spellout-ordinal-masculine";
		} else {
			for (String string : l) {
				if (string.startsWith("%spellout-ordinal")) {
					return string;
				}
			}
		}
		throw new UnsupportedOperationException("The locale " + rbnf.getLocale(ULocale.ACTUAL_LOCALE)
				+ " doesn't support ordinal spelling.");
	}

	/**
	 * Try to extract the rule name for "expand year" from the given RuleBasedNumberFormat.
	 * <p/>
	 * The rule name is locale sensitive, but usually starts with "%spellout-numbering-year".
	 *
	 * @param rbnf
	 *            The RuleBasedNumberFormat from where we will try to extract the rule name.
	 * @return The rule name for "year spell out".
	 */
	protected static String getYearRuleName(final RuleBasedNumberFormat rbnf) {
		List<String> l = Arrays.asList(rbnf.getRuleSetNames());
		if (l.contains("%spellout-numbering-year")) {
			return "%spellout-numbering-year";
		} else {
			for (String string : l) {
				if (string.startsWith("%spellout-numbering-year")) {
					return string;
				}
			}
		}
		throw new UnsupportedOperationException("The locale " + rbnf.getLocale(ULocale.ACTUAL_LOCALE)
				+ " doesn't support year spelling.");
	}
}
