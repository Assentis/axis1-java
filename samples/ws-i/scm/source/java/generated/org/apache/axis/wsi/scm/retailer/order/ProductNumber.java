/**
 * ProductNumber.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis #axisVersion# #today# WSDL2Java emitter.
 */

package org.apache.axis.wsi.scm.retailer.order;

public class ProductNumber  implements java.io.Serializable, org.apache.axis.encoding.SimpleType {
    private java.math.BigInteger value;

    public ProductNumber() {
    }

    // Simple Types must have a String constructor
    public ProductNumber(java.math.BigInteger value) {
        this.value = value;
    }
    public ProductNumber(java.lang.String value) {
        this.value = new java.math.BigInteger(value);
    }

    // Simple Types must have a toString for serializing the value
    public java.lang.String toString() {
        return value == null ? null : value.toString();
    }


    /**
     * Gets the value value for this ProductNumber.
     * 
     * @return value 
     */
    public java.math.BigInteger getValue() {
        return value;
    }


    /**
     * Sets the value value for this ProductNumber.
     * 
     * @param value 
     */
    public void setValue(java.math.BigInteger value) {
        this.value = value;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof ProductNumber)) return false;
        ProductNumber other = (ProductNumber) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.value==null && other.getValue()==null) || 
             (this.value!=null &&
              this.value.equals(other.getValue())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getValue() != null) {
            _hashCode += getValue().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(ProductNumber.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://www.ws-i.org/SampleApplications/SupplyChainManagement/2002-08/RetailOrder.xsd", "productNumber"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("value");
        elemField.setXmlName(new javax.xml.namespace.QName("", "value"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "integer"));
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.SimpleSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.SimpleDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}