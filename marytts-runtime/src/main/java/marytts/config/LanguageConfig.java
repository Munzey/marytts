/**
 * Copyright 2011 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.config;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import marytts.exceptions.MaryConfigurationException;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;

/**
 * @author marc
 *
 *         if a default Locale is not set for a language and a language config contains only one locale, then that locale is set
 *         as the defaultLocale, else the default locale is set to the language name
 *
 */
public class LanguageConfig extends MaryConfig {

	private Set<Locale> locales = new HashSet<Locale>();
	private Locale defaultLocale;

	public LanguageConfig(InputStream propertyStream) throws MaryConfigurationException {
		super(propertyStream);
		String localeProp = getProperties().getProperty("locale");
		if (localeProp == null) {
			throw new MaryConfigurationException("property stream does not contain a locale property");
		}
		for (StringTokenizer st = new StringTokenizer(localeProp); st.hasMoreTokens();) {
			String localeString = st.nextToken();
			locales.add(MaryUtils.string2locale(localeString));
		}
		if (locales.isEmpty()) {
			throw new MaryConfigurationException("property stream does not define any locale");
		}
		String defLocale = getProperties().getProperty("locale.default");
		if (defLocale != null) {
			defaultLocale = MaryUtils.string2locale(defLocale);
		} else if (localeProp.split("\\s+").length == 1) {
			defaultLocale = MaryUtils.string2locale(localeProp);
		} else {
			defaultLocale = MaryUtils.string2locale(getProperties().getProperty("name"));
		}
	}

	@Override
	public boolean isLanguageConfig() {
		return true;
	}

	public Set<Locale> getLocales() {
		return locales;
	}

	public String getLanguageName() {
		return getProperties().getProperty("name");
	}

	public Locale getDefaultLocale() {
		return defaultLocale;
	}

	protected AllophoneSet getAllophoneSetFor(Locale locale) throws MaryConfigurationException {
		if (!locales.contains(locale)) {
			locale = getDefaultLocale();
		}
		return MaryRuntimeUtils.needAllophoneSet(locale.toString() + ".allophoneset");
	}
}
