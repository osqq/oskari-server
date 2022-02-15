package org.oskari.capabilities.ogc;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.util.IOHelper;
import fi.nls.oskari.util.XmlHelper;
import org.oskari.capabilities.RawCapabilitiesResponse;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

public class CapabilitiesValidator {
    private static final Logger LOG = LogFactory.getLogger(CapabilitiesValidator.class);

    public static String validateXmlResponse(RawCapabilitiesResponse response)
            throws ServiceException {
        String encoding = response.getEncoding();

        try  {
            XMLStreamReader xsr = getXMLStreamReader(response.getResponse());

            // Check XML prolog for character encoding
            String xmlEncoding = xsr.getCharacterEncodingScheme();
            if (xmlEncoding != null) {
                if (encoding != null && !xmlEncoding.equalsIgnoreCase(encoding)) {
                    throw new ServiceException("Content-Type header specified a different encoding than XML prolog!");
                }
                encoding = xmlEncoding;
            }
            if (encoding == null) {
                LOG.debug("Charset wasn't set on either the Content-Type or the XML prolog"
                        + "using UTF-8 as default value");
                encoding = IOHelper.DEFAULT_CHARSET;
            }

            // Convert "utf-8" to "UTF-8" for example
            encoding = encoding.toUpperCase();
            String xml = new String(response.getResponse(), encoding);
            // Strip the potential prolog from XML so that we
            // don't have to worry about the specified charset
            return XmlHelper.stripPrologFromXML(xml);
        } catch (XMLStreamException e) {
            throw new ServiceException("Failed to parse XML from response", e);
        } catch (UnsupportedEncodingException e) {
            throw new ServiceException("Failed to Encode byte[] to String using encoding " + encoding, e);
        }
    }

    private static XMLStreamReader getXMLStreamReader(byte[] data) throws XMLStreamException{
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return xif.createXMLStreamReader(new ByteArrayInputStream(data));
    }
}
