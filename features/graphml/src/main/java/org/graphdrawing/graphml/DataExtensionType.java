//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.05.17 at 02:59:53 PM CEST 
//


package org.graphdrawing.graphml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * 
 *       Extension mechanism for the content of <data> and <default>.
 *       The complex type data-extension.type is empty per default.
 *       Users may redefine this type in order to add content to 
 *       the complex types data.type and default.type which are 
 *       extensions of data-extension.type.
 *     
 * 
 * <p>Java class for data-extension.type complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="data-extension.type"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "data-extension.type", propOrder = {
    "content"
})
@XmlSeeAlso({
    DataType.class,
    DefaultType.class
})
public class DataExtensionType {

    @XmlValue
    protected String content;

    /**
     * 
     *       Extension mechanism for the content of <data> and <default>.
     *       The complex type data-extension.type is empty per default.
     *       Users may redefine this type in order to add content to 
     *       the complex types data.type and default.type which are 
     *       extensions of data-extension.type.
     *     
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the value of the content property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContent(String value) {
        this.content = value;
    }

}
