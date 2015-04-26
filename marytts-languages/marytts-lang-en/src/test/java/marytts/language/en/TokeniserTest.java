package marytts.language.en;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import marytts.LocalMaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.util.dom.DomUtils;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.testng.annotations.*;
import org.testng.Assert;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class TokeniserTest {

	private LocalMaryInterface mary;

	@BeforeSuite
	public void setUp() throws MaryConfigurationException {
		mary = new LocalMaryInterface();
		mary.setInputType("TEXT");
		mary.setOutputType("TOKENS");
	}

	@Test
	public void testTokenise() throws Exception, ParserConfigurationException, SAXException, IOException {
		Document tokenisedDoc;
		Document expectedDoc;
		String lemma = "no-one";
		Document actualDoc = mary.generateXML(lemma);
		String tokens = "<maryxml xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"0.5\" xml:lang=\"en-US\" xmlns=\"http://mary.dfki.de/2002/MaryXML\"><p><s><t>"
				+ lemma + "</t></s></p></maryxml>";
		expectedDoc = DomUtils.parseDocument(tokens);
		printDocument(expectedDoc, System.out);
		printDocument(actualDoc, System.out);
		Diff diff = XMLUnit.compareXML(expectedDoc, actualDoc);
		Assert.assertTrue(diff.identical());
	}
	
	public static void printDocument(Document doc, OutputStream out) throws IOException, TransformerException {
	    TransformerFactory tf = TransformerFactory.newInstance();
	    Transformer transformer = tf.newTransformer();
	    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
	    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
	    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
	    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

	    transformer.transform(new DOMSource(doc), 
	         new StreamResult(new OutputStreamWriter(out, "UTF-8")));
	}
}
