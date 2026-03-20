package com.codemaster.git_repo_analyzer.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public final class XmlConfigParser {

  private static final Logger logger = LoggerFactory.getLogger(XmlConfigParser.class);

  private XmlConfigParser() {
    throw new IllegalStateException("Unable to create instance of a utility class");
  }

  public static Set<RepositoryInfo> getRepositoriesInfo(String repoConfigFilePath) {
    Set<RepositoryInfo> repoInfos = new HashSet<>();
    try {
      File xmlFile = new File(repoConfigFilePath);
      DocumentBuilder dBuilder = createDocumentBuilder();
      Document document = createDocument(dBuilder, xmlFile);
      repoInfos = extractRepositoriesInfo(document);
    } catch (Exception e) {
      logger.error("Failed to parse XML from file: {}", repoConfigFilePath, e);
    }
    return repoInfos;
  }

  public static Set<RepositoryInfo> getRepositoriesInfo(InputStream inputStream) {
    Set<RepositoryInfo> repoInfos = new HashSet<>();
    try {
      DocumentBuilder dBuilder = createDocumentBuilder();
      Document document = dBuilder.parse(inputStream);
      document.getDocumentElement().normalize();
      repoInfos = extractRepositoriesInfo(document);
    } catch (Exception e) {
      logger.error("Failed to parse XML from input stream", e);
    }
    return repoInfos;
  }

  private static Set<RepositoryInfo> extractRepositoriesInfo(Document document) {
    Set<RepositoryInfo> repoData = new HashSet<>();
    NodeList repositoriesList = document.getElementsByTagName("repository");

    iterateNodeList(repositoriesList, workspaceElement -> {
      String repoUrl = workspaceElement.getElementsByTagName("url").item(0).getTextContent();
      repoData.add(new RepositoryInfo(repoUrl));
    });
    return repoData;
  }

  private static void iterateNodeList(NodeList nodeList, NodeProcessor processor) {
    for (int i = 0; i < nodeList.getLength(); i++) {
      Element element = (Element) nodeList.item(i);
      processor.process(element);
    }
  }

  private static Document createDocument(DocumentBuilder dBuilder, File xmlFile) throws SAXException, IOException {
    Document doc = dBuilder.parse(xmlFile);
    doc.getDocumentElement().normalize();
    return doc;
  }

  private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newDefaultInstance();
    return dbFactory.newDocumentBuilder();
  }

}
