/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Clinton Begin
 */
public class XNode {

  /**
   * 重点包装的对象 org.w3c.dom.Node
   */
  private final Node node;

  /**
   * 冗余字段，节点的名称
   */
  private final String name;
  private final String body;

  /**
   * 冗余字段，节点的属性
   */
  private final Properties attributes;

  /**
   * 外部传入的变量
   */
  private final Properties variables;

  /**
   * 外部传入的变量，用于 XPath 语法解析 XML
   */
  private final XPathParser xpathParser;

  public XNode(XPathParser xpathParser, Node node, Properties variables) {
    this.xpathParser = xpathParser;
    this.node = node;
    this.name = node.getNodeName();
    this.variables = variables;
    this.attributes = parseAttributes(node);
    this.body = parseBody(node);
  }

  public XNode newXNode(Node node) {
    return new XNode(xpathParser, node, variables);
  }

  public XNode getParent() {
    Node parent = node.getParentNode();
    if (!(parent instanceof Element)) {
      return null;
    }
    return new XNode(xpathParser, parent, variables);
  }

  public String getPath() {
    StringBuilder builder = new StringBuilder();
    Node current = node;
    while (current instanceof Element) {
      if (current != node) {
        builder.insert(0, "/");
      }
      builder.insert(0, current.getNodeName());
      current = current.getParentNode();
    }
    return builder.toString();
  }

  public String getValueBasedIdentifier() {
    StringBuilder builder = new StringBuilder();
    XNode current = this;
    while (current != null) {
      if (current != this) {
        builder.insert(0, "_");
      }
      String value = current.getStringAttribute("id",
          current.getStringAttribute("value", current.getStringAttribute("property", (String) null)));
      if (value != null) {
        value = value.replace('.', '_');
        builder.insert(0, "]");
        builder.insert(0, value);
        builder.insert(0, "[");
      }
      builder.insert(0, current.getName());
      current = current.getParent();
    }
    return builder.toString();
  }

  public String evalString(String expression) {
    return xpathParser.evalString(node, expression);
  }

  public Boolean evalBoolean(String expression) {
    return xpathParser.evalBoolean(node, expression);
  }

  public Double evalDouble(String expression) {
    return xpathParser.evalDouble(node, expression);
  }

  public List<XNode> evalNodes(String expression) {
    return xpathParser.evalNodes(node, expression);
  }

  public XNode evalNode(String expression) {
    return xpathParser.evalNode(node, expression);
  }

  public Node getNode() {
    return node;
  }

  public String getName() {
    return name;
  }

  public String getStringBody() {
    return getStringBody(null);
  }

  public String getStringBody(String def) {
    return body == null ? def : body;
  }

  public Boolean getBooleanBody() {
    return getBooleanBody(null);
  }

  public Boolean getBooleanBody(Boolean def) {
    return body == null ? def : Boolean.valueOf(body);
  }

  public Integer getIntBody() {
    return getIntBody(null);
  }

  public Integer getIntBody(Integer def) {
    return body == null ? def : Integer.valueOf(body);
  }

  public Long getLongBody() {
    return getLongBody(null);
  }

  public Long getLongBody(Long def) {
    return body == null ? def : Long.valueOf(body);
  }

  public Double getDoubleBody() {
    return getDoubleBody(null);
  }

  public Double getDoubleBody(Double def) {
    return body == null ? def : Double.valueOf(body);
  }

  public Float getFloatBody() {
    return getFloatBody(null);
  }

  public Float getFloatBody(Float def) {
    return body == null ? def : Float.valueOf(body);
  }

  public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name) {
    return getEnumAttribute(enumType, name, null);
  }

  public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name, T def) {
    String value = getStringAttribute(name);
    return value == null ? def : Enum.valueOf(enumType, value);
  }

  /**
   * Return a attribute value as String.
   * <p>
   * If attribute value is absent, return value that provided from supplier of default value.
   *
   * @param name
   *          attribute name
   * @param defSupplier
   *          a supplier of default value
   *
   * @return the string attribute
   *
   * @since 3.5.4
   */
  public String getStringAttribute(String name, Supplier<String> defSupplier) {
    String value = attributes.getProperty(name);
    return value == null ? defSupplier.get() : value;
  }

  public String getStringAttribute(String name) {
    return getStringAttribute(name, (String) null);
  }

  public String getStringAttribute(String name, String def) {
    String value = attributes.getProperty(name);
    return value == null ? def : value;
  }

  public Boolean getBooleanAttribute(String name) {
    return getBooleanAttribute(name, null);
  }

  public Boolean getBooleanAttribute(String name, Boolean def) {
    String value = attributes.getProperty(name);
    return value == null ? def : Boolean.valueOf(value);
  }

  public Integer getIntAttribute(String name) {
    return getIntAttribute(name, null);
  }

  public Integer getIntAttribute(String name, Integer def) {
    String value = attributes.getProperty(name);
    return value == null ? def : Integer.valueOf(value);
  }

  public Long getLongAttribute(String name) {
    return getLongAttribute(name, null);
  }

  public Long getLongAttribute(String name, Long def) {
    String value = attributes.getProperty(name);
    return value == null ? def : Long.valueOf(value);
  }

  public Double getDoubleAttribute(String name) {
    return getDoubleAttribute(name, null);
  }

  public Double getDoubleAttribute(String name, Double def) {
    String value = attributes.getProperty(name);
    return value == null ? def : Double.valueOf(value);
  }

  public Float getFloatAttribute(String name) {
    return getFloatAttribute(name, null);
  }

  public Float getFloatAttribute(String name, Float def) {
    String value = attributes.getProperty(name);
    return value == null ? def : Float.valueOf(value);
  }

  public List<XNode> getChildren() {
    List<XNode> children = new ArrayList<>();
    NodeList nodeList = node.getChildNodes();
    if (nodeList != null) {
      for (int i = 0, n = nodeList.getLength(); i < n; i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          children.add(new XNode(xpathParser, node, variables));
        }
      }
    }
    return children;
  }

  public Properties getChildrenAsProperties() {
    Properties properties = new Properties();

    // 因为 properties 可能有子节点 比如 <property>
    for (XNode child : getChildren()) {
      // 类似于 key-value 键值对
      String name = child.getStringAttribute("name");
      String value = child.getStringAttribute("value");

      // 如果两个都有值，就设置属性
      if (name != null && value != null) {
        properties.setProperty(name, value);
      }
    }
    return properties;
  }

  @Override
  public String toString() {
    return buildToString(new StringBuilder(), 0).toString();
  }

  private StringBuilder buildToString(StringBuilder builder, int indentLevel) {
    indent(builder, indentLevel).append("<").append(name);
    for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
      builder.append(" ");
      builder.append(entry.getKey());
      builder.append("=\"");
      builder.append(entry.getValue());
      builder.append("\"");
    }

    NodeList nodeList = node.getChildNodes();
    if (nodeList == null || nodeList.getLength() == 0) {
      builder.append(" />\n");
    } else {
      builder.append(">\n");
      for (int i = 0, n = nodeList.getLength(); i < n; i++) {
        Node node = nodeList.item(i);
        short nodeType = node.getNodeType();
        if (nodeType == Node.ELEMENT_NODE) {
          new XNode(xpathParser, node, variables).buildToString(builder, indentLevel + 1);
        } else {
          String text = getBodyData(node).trim();
          if (text.length() > 0) {
            indent(builder, indentLevel + 1).append(text).append("\n");
          }
        }
      }
      indent(builder, indentLevel).append("</").append(name).append(">\n");
    }

    return builder;
  }

  private StringBuilder indent(StringBuilder builder, int level) {
    for (int i = 0; i < level; i++) {
      builder.append("  ");
    }
    return builder;
  }

  private Properties parseAttributes(Node n) {
    Properties attributes = new Properties();
    // 获得节点的属性
    NamedNodeMap attributeNodes = n.getAttributes();
    if (attributeNodes != null) {
      for (int i = 0; i < attributeNodes.getLength(); i++) {
        Node attribute = attributeNodes.item(i);

        // 使用属性解析器得到 value
        String value = PropertyParser.parse(attribute.getNodeValue(), variables);
        attributes.put(attribute.getNodeName(), value);
      }
    }
    return attributes;
  }

  private String parseBody(Node node) {
    String data = getBodyData(node);
    if (data == null) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        data = getBodyData(child);
        if (data != null) {
          break;
        }
      }
    }
    return data;
  }

  private String getBodyData(Node child) {
    if (child.getNodeType() == Node.CDATA_SECTION_NODE || child.getNodeType() == Node.TEXT_NODE) {
      String data = ((CharacterData) child).getData();
      return PropertyParser.parse(data, variables);
    }
    return null;
  }

}
