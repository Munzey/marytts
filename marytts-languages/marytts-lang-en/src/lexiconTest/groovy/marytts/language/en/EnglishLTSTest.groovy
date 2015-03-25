package marytts.language.en

import org.testng.annotations.*
import org.testng.Assert

import marytts.LocalMaryInterface
import marytts.client.RemoteMaryInterface
import marytts.exceptions.MaryConfigurationException

import groovy.xml.*

public class EnglishLTSTest {

    private maryOld
    private maryNew
    private xmlParser

    @BeforeClass
    public void setUp() throws MaryConfigurationException {
        maryOld = new LocalMaryInterface()
        maryOld.outputType = "PHONEMES"
        maryNew = new RemoteMaryInterface("localhost", 59130)
        maryNew.outputType = "PHONEMES"
        xmlParser = new XmlParser()
    }

    @DataProvider
    public Object[][] lexicon() {
        return this.getClass().getClassLoader().getResource("en.txt").readLines().collect {
            it.split('\t').take(2)
        }
    }

    @Test
    public void testLexDataProvider() {
        Assert.assertNotNull(lexicon())
    }

    /***
     * lemmas such as 'csaszar' or anything which JTokeniser or another preprocesser processes before
     * JPhonemiser will result in the produced mary output containing the extra element <mtu> meaning the
     * xmlParser wont work in this case
     */
    @Test
    public void compareSingleLemma() {
        def lemma = 'csaszar'
        def maryXmlDocOld = maryOld.generateXML(lemma)
        def maryXmlDocNew = maryNew.generateXML(lemma)
        def maryXmlOld = XmlUtil.serialize(maryXmlDocOld.documentElement)
        def maryXmlNew = XmlUtil.serialize(maryXmlDocNew.documentElement)
        def phStrOld = xmlParser.parseText(maryXmlOld).p.s.mtu.t.@ph[0]
        def phStrNew = xmlParser.parseText(maryXmlNew).p.voice.s.mtu.t.@ph[0]
        def oldTranscription = phStrOld.replaceAll(' ', '')
        def newTranscription = phStrNew.replaceAll(' ', '')
        Assert.assertEquals(oldTranscription, newTranscription)
    }

    @Test(dataProvider = "lexicon")
    public void compareSyllabifierOldNew(String lemma, String expectedTranscription) {
        def maryXmlDocOld = maryOld.generateXML(lemma)
        def maryXmlDocNew = maryNew.generateXML(lemma)
        def maryXmlOld = XmlUtil.serialize(maryXmlDocOld.documentElement)
        def maryXmlNew = XmlUtil.serialize(maryXmlDocNew.documentElement)
        def phStrOld = xmlParser.parseText(maryXmlOld).p.s.t.@ph[0]
        def phStrNew = xmlParser.parseText(maryXmlNew).p.voice.s.t.@ph[0]
        def oldTranscription = phStrOld.replaceAll(' ', '')
        def newTranscription = phStrNew.replaceAll(' ', '')
        Assert.assertEquals(oldTranscription, newTranscription)
    }


    @Test(dataProvider = "lexicon")
    public void testTranscriptions(String lemma, String expectedTranscription) {
        def maryXmlDoc = maryOld.generateXML(lemma)
        def maryXml = XmlUtil.serialize(maryXmlDoc.documentElement)
        def phStr = xmlParser.parseText(maryXml).p.s.t.@ph[0]
        def actualTranscription = phStr.replaceAll(' ', '')
        Assert.assertEquals(actualTranscription, expectedTranscription)
    }

    /***
     * checks if g2p method is being used for lexicon entries
     *
     * this test seems to fail for entries in the lexicon which(perhaps after being deemed not english)
     * are tokenised into single characters and each single character is looked up in the lexicon
     * the other curious case is if the character 'a' is entered alone, it calls the g2p lookup instead of lexicon rule
     * however if 'a' is in a sentence it is looked up in lexicon
     */
    @Test(dataProvider = "lexicon")
    public void testG2pMethod(String lemma, String expectedTranscription) {
        def maryXmlDoc = maryOld.generateXML(lemma)
        def maryXml = XmlUtil.serialize(maryXmlDoc.documentElement)
        def g2pMethod = xmlParser.parseText(maryXml).p.s.t.@g2p_method[0]
        Assert.assertTrue(g2pMethod.equals("lexicon"))
    }
}
