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
 *         - cardinal 
 *         - ordinal 
 *         - year 
 *         - currency 
 *         Does not handle yet:
 *         - contractions and abbreviations
 *         - roman numerals
 *         - dashes (read each number singly) or (split into two words)
 *         - decimal points,minus (real numbers)
 *         - time
 */
public class Preprocess extends InternalModule {

	private RuleBasedNumberFormat rbnf;
	protected final String cardinalRule;
	protected final String ordinalRule;
	protected final String yearRule;

	// Regex matching patterns
	private static final Pattern moneyPattern;
	private static final Pattern timePattern;

	static {
		moneyPattern = Pattern.compile("(\\$|£|€)(\\d+)(\\.\\d+)?");
		timePattern = Pattern.compile("(0?[0-9]|1[0-9]|2[0-3]):([0-5][0-9])(:[0-5][0-9])?");
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
		checkForNumbers(doc);
		MaryData result = new MaryData(getOutputType(), d.getLocale());
		result.setDocument(doc);
		return result;
	}

	protected void checkForNumbers(Document doc) {
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
			// year
			} else if (MaryDomUtils.tokenText(t).matches("\\d{4}") && isYear == true) {
				MaryDomUtils.setTokenText(t, expandYear(Double.parseDouble(MaryDomUtils.tokenText(t))));
			// cardinal
			} else if (MaryDomUtils.tokenText(t).matches("\\d+")) {
				MaryDomUtils.setTokenText(t, expandNumber(Double.parseDouble(MaryDomUtils.tokenText(t))));
			// time
			} else if (MaryDomUtils.tokenText(t).matches(timePattern.pattern())) {
				Matcher timeMatch = timePattern.matcher(MaryDomUtils.tokenText(t));
				timeMatch.find();
				if (timeMatch.group(7) != null) {
					MaryDomUtils.setTokenText(t, expandDuration(MaryDomUtils.tokenText(t)));
				}
				else {
					MaryDomUtils.setTokenText(t, expandTime(MaryDomUtils.tokenText(t)));
				}
			// currency
			} else if (MaryDomUtils.tokenText(t).matches(moneyPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandMoney(MaryDomUtils.tokenText(t)));
			}
			// if token isn't ignored but there is no handling rule don't add MTU
			if (!origText.equals(MaryDomUtils.tokenText(t))) {
				MaryDomUtils.encloseWithMTU(t, origText, null);
			}
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
	
	protected String expandTime(String time) {
		return null;
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
