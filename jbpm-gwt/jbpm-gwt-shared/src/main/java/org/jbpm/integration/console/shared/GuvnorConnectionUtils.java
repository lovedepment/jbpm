/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.integration.console.shared;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.antlr.stringtemplate.StringTemplate;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jbpm.integration.console.shared.model.GuvnorPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuvnorConnectionUtils {
    public static final String GUVNOR_PROTOCOL_KEY = "guvnor.protocol";
    public static final String GUVNOR_HOST_KEY = "guvnor.host";
    public static final String GUVNOR_USR_KEY = "guvnor.usr";
    public static final String GUVNOR_PWD_KEY = "guvnor.pwd";
    public static final String GUVNOR_PWD_ENC_KEY = "guvnor.pwd.enc";
    public static final String GUVNOR_PACKAGES_KEY = "guvnor.packages";
    public static final String GUVNOR_SUBDOMAIN_KEY = "guvnor.subdomain";
    public static final String GUVNOR_CONNECTTIMEOUT_KEY = "guvnor.connect.timeout";
    public static final String GUVNOR_READTIMEOUT_KEY = "guvnor.read.timeout";
    public static final String GUVNOR_SNAPSHOT_NAME = "guvnor.snapshot.name";
    public static final String EXT_BPMN = "bpmn";
    public static final String EXT_BPMN2 = "bpmn2";
    private static final String externalPwdKey = "externalpwdkey";

    private static final Logger logger = LoggerFactory.getLogger(GuvnorConnectionUtils.class);
    private static Properties properties = new Properties();
    
    static {
        try {
            properties.load(GuvnorConnectionUtils.class.getResourceAsStream("/jbpm.console.properties"));
        } catch (IOException e) {
            throw new RuntimeException("Could not load jbpm.console.properties", e);
        }
    }
   
    // Guvnor communication
    
    public String getGuvnorProtocol() {
        return isEmpty(properties.getProperty(GUVNOR_PROTOCOL_KEY)) ? "" : properties.getProperty(GUVNOR_PROTOCOL_KEY).trim();
    }

    public String getGuvnorHost() {
        if(!isEmpty(properties.getProperty(GUVNOR_HOST_KEY))) {
            String retStr = properties.getProperty(GUVNOR_HOST_KEY).trim();
            if(retStr.startsWith("/")){
                retStr = retStr.substring(1);
            }
            if(retStr.endsWith("/")) {
                retStr = retStr.substring(0,retStr.length() - 1);
            }
            return retStr;
        } else {
            return "";
        }
    }

    public String getGuvnorSubdomain() {
        return isEmpty(properties.getProperty(GUVNOR_SUBDOMAIN_KEY)) ? "" : properties.getProperty(GUVNOR_SUBDOMAIN_KEY).trim();
    }

    public String getGuvnorUsr() {
        return isEmpty(properties.getProperty(GUVNOR_USR_KEY)) ? "" : properties.getProperty(GUVNOR_USR_KEY).trim();
    }

    public String getGuvnorPwd() {
        if(getGuvnorPwdEnc().equalsIgnoreCase("true")) {
            if(System.getProperty(externalPwdKey) == null) {
                throw new IllegalStateException("Unable to find system property: " + externalPwdKey);
            } else {
                try {
                    FileInputStream inputStream = new FileInputStream(System.getProperty(externalPwdKey));
                    String encKey = IOUtils.toString(inputStream);
                    encKey = encKey.replace("\n", "").replace("\r", "");
    
                    String _pwd = isEmpty(properties.getProperty(GUVNOR_PWD_KEY)) ? "" : properties.getProperty(GUVNOR_PWD_KEY).trim();
                    StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
                    encryptor.setPassword(encKey);
                    encryptor.setAlgorithm("PBEWithMD5AndTripleDES");
                    return encryptor.decrypt(_pwd);
                } catch (Exception e) {
                    throw new IllegalStateException("Unable to decrypt pwd: " + e.getMessage());
                }
            }
        } else {
            return isEmpty(properties.getProperty(GUVNOR_PWD_KEY)) ? "" : properties.getProperty(GUVNOR_PWD_KEY).trim();
        }
    
    }

    public String getGuvnorPwdEnc() {
        return isEmpty(properties.getProperty(GUVNOR_PWD_ENC_KEY)) ? "false" : properties.getProperty(GUVNOR_PWD_ENC_KEY).trim();
    }

    public String getGuvnorPackages() {
        return isEmpty(properties.getProperty(GUVNOR_PACKAGES_KEY)) ? "" : properties.getProperty(GUVNOR_PACKAGES_KEY).trim();
    }

    public String getGuvnorConnectTimeout() {
        return isEmpty(properties.getProperty(GUVNOR_CONNECTTIMEOUT_KEY)) ? "10000" : properties.getProperty(GUVNOR_CONNECTTIMEOUT_KEY).trim();
    }

    public String getGuvnorReadTimeout() {
        return isEmpty(properties.getProperty(GUVNOR_READTIMEOUT_KEY)) ? "10000" : properties.getProperty(GUVNOR_READTIMEOUT_KEY).trim();
    }

    public String getGuvnorSnapshotName() {
    	return isEmpty(properties.getProperty(GUVNOR_SNAPSHOT_NAME)) ? "LATEST" : properties.getProperty(GUVNOR_SNAPSHOT_NAME).trim();
    }

    protected Properties getGuvnorProperties() {
        return properties;
    }

    // public scope for testing
    public boolean isEmpty(final CharSequence str) {
        if ( str == null || str.length() == 0 ) {
            return true;
        }
        for ( int i = 0, length = str.length(); i < length; i++ ){
            if ( str.charAt( i ) != ' ' ) {
                return false;
            }
        }
        return true;
    }

    private InputStream getInputStreamForURL(String urlLocation,
            String requestMethod) throws Exception {
        URL url = new URL(urlLocation);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    
        connection.setRequestMethod(requestMethod);
        connection
        .setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.6; en-US; rv:1.9.2.16) Gecko/20110319 Firefox/3.6.16");
        connection.setRequestProperty("Accept", "text/plain,text/html,application/xhtml+xml,application/xml");
        connection.setRequestProperty("charset", "UTF-8");
        connection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
        connection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
        applyAuth(connection);
        connection.connect();
    
        BufferedReader sreader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), "UTF-8"));
        StringBuilder stringBuilder = new StringBuilder();
    
        String line = null;
        while ((line = sreader.readLine()) != null) {
            stringBuilder.append(line + "\n");
        }
        
        return new ByteArrayInputStream(stringBuilder.toString().getBytes(
                "UTF-8"));
    }

    // Image, Form, Template methods ----------------------------------------------------------------------------------------------
    
    public String getProcessImageURLFromGuvnor(String processId) {
        List<String> allPackages = getPackageNames();
        for(String pkg : allPackages) {
            // query the package to get a list of all processes in this package
            List<String> allProcessesInPackage = getAllProcessesInPackage(pkg);
            // check each process to see if it has the matching id set
            for(String process : allProcessesInPackage) {
                String processContent = getProcessSourceContent(pkg, process);
                Pattern p = Pattern.compile("<\\S*process[\\s\\S]*id=\"" + processId + "\"", Pattern.MULTILINE);
                Matcher m = p.matcher(processContent);
                if(m.find()) {
                    try {
                        String imageBinaryURL = getGuvnorProtocol()
                        + "://"
                        + getGuvnorHost()
                        + "/"
                        + getGuvnorSubdomain()
                        + "/org.drools.guvnor.Guvnor/package/"
                        + pkg
                        + "/" + getGuvnorSnapshotName() + "/"
                        + URLEncoder.encode(processId, "UTF-8")
                        + "-image.png";
                        
                        URL checkURL = new URL(imageBinaryURL);
                        HttpURLConnection checkConnection = (HttpURLConnection) checkURL.openConnection();
                        checkConnection.setRequestMethod("GET");
                        checkConnection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
                        checkConnection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
                        applyAuth(checkConnection);
                        checkConnection.connect();
                       
                        if (checkConnection.getResponseCode() == 200) {
                            return imageBinaryURL;    
                        }
                        
                    } catch (Exception e) {
                       logger.error("Could not read process image: " + e.getMessage());
                       throw new RuntimeException("Could not read process image: " + e.getMessage());
                    }
                }
            }
        }
        logger.info("Did not find process image for: " + processId);
        return null;
    }
    
    private InputStream getInputStreamForImageURL(String urlLocation, String requestMethod) throws Exception {
        URL url = new URL(urlLocation);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    
        connection.setRequestMethod(requestMethod);
        connection
        .setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.6; en-US; rv:1.9.2.16) Gecko/20110319 Firefox/3.6.16");
        connection.setRequestProperty("Accept", "text/plain,text/html,application/xhtml+xml,application/xml");
        connection.setRequestProperty("charset", "UTF-8");
        connection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
        connection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
        applyAuth(connection);
    
        connection.connect();
        return connection.getInputStream();
    }

    public String getFormTemplateURLFromGuvnor(String templateName) {
        return getFormTemplateURLFromGuvnor(templateName, "drl");
    }
    
    public String getFormTemplateURLFromGuvnor(String templateName, String format) {
        List<String> allPackages = getPackageNames();
        try {
            for(String pkg : allPackages) {
                String templateURL = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/"
                + pkg
                + "/assets/"
                + URLEncoder.encode(templateName, "UTF-8");
                
                URL checkURL = new URL(templateURL);
                HttpURLConnection checkConnection = (HttpURLConnection) checkURL.openConnection();
                checkConnection.setRequestMethod("GET");
                checkConnection.setRequestProperty("Accept", "application/atom+xml");
                checkConnection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
                checkConnection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
                applyAuth(checkConnection);
                checkConnection.connect();
                if(checkConnection.getResponseCode() == 200) {
                    
                    String toReturnURL = getGuvnorProtocol()
                    + "://"
                    + getGuvnorHost()
                    + "/"
                    + getGuvnorSubdomain()
                    + "/org.drools.guvnor.Guvnor/package/"
                    + pkg
                    + "/" + getGuvnorSnapshotName() + "/"
                    + URLEncoder.encode(templateName, "UTF-8")
                    + "." + format;
                    
                    return toReturnURL;
                }
            }
        } catch (Exception e) {
           logger.error("Exception returning template url : " + e.getMessage());
           return null;
        }
        logger.info("Could not find process template url for: " + templateName);
        return null;
    }
    
    public InputStream getFormTemplateFromGuvnor(String templateName) {
        String formTemplateURL = getFormTemplateURLFromGuvnor(templateName);
        if(formTemplateURL != null) {
            try {
                return getInputStreamForURL(formTemplateURL, "GET");
            } catch (Exception e) {
                logger.error("Exception getting input stream for form template url: " + formTemplateURL);
                return null;
            }
        } else {
            logger.info("Could not get the form template from guvnor");
            return null;
        }
    }
    
    public byte[] getProcessImageFromGuvnor(String processId) {
        String processImageURL = getProcessImageURLFromGuvnor(processId);
        if(processImageURL != null) {
            try {
                InputStream is = getInputStreamForImageURL(processImageURL, "GET");
                if (is != null) {
                    return IOUtils.toByteArray(is);
                } else {
                    return null;
                }
            } catch (Exception e) {
               logger.error("Exception reading process image: " + e.getMessage());
               throw new RuntimeException("Could not read process image: " + e.getMessage());
            }
        } else {
            logger.info("Invalid process image for: " + processId);
            return null;
        }
    }
    
    private String getProcessSourceContent(String packageName, String assetName) {
        String assetSourceURL = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/" + packageName + "/assets/" + assetName
                + "/source/";

        try {
            InputStream in = getInputStreamForURL(assetSourceURL, "GET");
            StringWriter writer = new StringWriter();
            IOUtils.copy(in, writer);
            return writer.toString();
        } catch (Exception e) {
            logger.error("Error retrieving asset content: " + e.getMessage());
            return "";
        }
    }
    
    public List<String> getAllProcessesInPackage(String pkgName) {
        List<String> processes = new ArrayList<String>();
        String assetsURL = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/"
                + pkgName
                + "/assets/";
        
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(getInputStreamForURL(assetsURL, "GET"));

            String format = "";
            String title = ""; 
            while (reader.hasNext()) {
                int next = reader.next();
                if (next == XMLStreamReader.START_ELEMENT) {
                    if ("format".equals(reader.getLocalName())) {
                        format = reader.getElementText();
                    } 
                    if ("title".equals(reader.getLocalName())) {
                        title = reader.getElementText();
                    }
                    if ("asset".equals(reader.getLocalName())) {
                        if(format.equals(EXT_BPMN) || format.equals(EXT_BPMN2)) {
                            processes.add(title);
                            title = "";
                            format = "";
                        }
                    }
                }
            }
            // last one
            if(format.equals(EXT_BPMN) || format.equals(EXT_BPMN2)) {
                processes.add(title);
            }
        } catch (Exception e) {
            logger.error("Error finding processes in package: " + e.getMessage());
        } 
        return processes;
    }
   
    public boolean templateExistsInRepo(String templateName) throws Exception {
        List<String> allPackages = getPackageNames();
        try {
            for(String pkg : allPackages) {
                String templateURL = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/"
                + pkg
                + "/assets/"
                + URLEncoder.encode(templateName, "UTF-8");
                
                URL checkURL = new URL(templateURL);
                HttpURLConnection checkConnection = (HttpURLConnection) checkURL.openConnection();
                checkConnection.setRequestMethod("GET");
                checkConnection.setRequestProperty("Accept", "application/atom+xml");
                checkConnection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
                checkConnection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
                applyAuth(checkConnection);
                checkConnection.connect();
                if(checkConnection.getResponseCode() == 200) {
                    
                    XMLInputFactory factory = XMLInputFactory.newInstance();
                    XMLStreamReader reader = factory
                            .createXMLStreamReader(checkConnection.getInputStream());
                    
                    boolean foundFormFormat = false;
                    while (reader.hasNext()) {
                        if (reader.next() == XMLStreamReader.START_ELEMENT) {
                            if ("format".equals(reader.getLocalName())) {
                                reader.next();
                                String pname = reader.getElementText();
                                if ("flt".equalsIgnoreCase(pname)) {
                                    foundFormFormat = true;
                                    break;
                                }
                            }
                        }
                    }
                    return foundFormFormat;
                }
            }
        } catch (Exception e) {
           logger.error("Exception checking template url : " + e.getMessage());
           return false;
        }
        logger.info("Could not find process template for: " + templateName);
        return false;
    }

    // Package information methods -----------------------------------------------------------------------------------------------
   
    // public scope for testing
    public List<GuvnorPackage> getPackagesFromGuvnor() {
        String packagesURL = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/";
        
        InputStream inputStream = null;
        try {
            inputStream = getInputStreamForURL(packagesURL, "GET");
        } catch (Exception e) {
            logger.error("Error retriving packages from guvnor: " + e.getMessage());
        }
        List<GuvnorPackage> packages = getPackagesFromXmlInputStream(inputStream);
        return packages;
    }

    // package scope for testing
    static List<GuvnorPackage> getPackagesFromXmlInputStream(InputStream inputStream) { 
        List<GuvnorPackage> packages = new ArrayList<GuvnorPackage>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            GuvnorPackage pkg = null;
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamReader.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if( "package".equals(name) ) { 
                        if( pkg != null && ! "Packages".equalsIgnoreCase(pkg.getTitle()) ) { 
                           packages.add(pkg); 
                        }
                        pkg = new GuvnorPackage();
                    }
                    if( pkg != null ) { 
                        if( "uuid".equals(name) ) { 
                            pkg.setUuid(reader.getElementText());
                        } else if( "title".equals(name) ) { 
                            pkg.setTitle(reader.getElementText());
                        } else if( "archived".equals(name) ) { 
                           String text =  reader.getElementText();
                           pkg.setArchived(Boolean.valueOf(text));
                        }
                    }
                }
            }
            if( pkg != null && ! "Packages".equalsIgnoreCase(pkg.getTitle()) ) { 
                packages.add(pkg); 
            }
        } catch (Exception e) {
            logger.error("Error retriving packages from guvnor: " + e.getMessage());
        }
        return packages;
    }

    private List<String> getPackageNamesFromGuvnor() {
       List<GuvnorPackage> guvnorPkgs = getPackagesFromGuvnor();
       List<String> pkgNames = new ArrayList<String>();
       if( guvnorPkgs.size() > 0 ) { 
          for( GuvnorPackage pkg : guvnorPkgs ) { 
              pkgNames.add(pkg.getTitle());
          }
       }
       return pkgNames;
    }

    private List<String> filterPackageNamesByUserDefinedList(List<String> allPackages) { 
        if(!isEmpty(properties.getProperty(GUVNOR_PACKAGES_KEY))) {
            // make sure that all user defined package names are in the list of provided packages
            String[] providedPackages = properties.getProperty(GUVNOR_PACKAGES_KEY).trim().split( ",\\s*" );
            List<String> retList = new ArrayList<String>();
            for(String pkg : providedPackages) {
                if(allPackages.contains(pkg)) {
                    retList.add(pkg);
                }
            }
            return retList;
        } else {
            return allPackages;
        }
    }

    // public scope for testing purposes
    public List<GuvnorPackage> filterPackagesByUserDefinedList(List<GuvnorPackage> allPackages) { 
        if(!isEmpty(properties.getProperty(GUVNOR_PACKAGES_KEY))) {
            // make sure that all user defined package names are in the list of provided packages
            String[] providedPkgArr = properties.getProperty(GUVNOR_PACKAGES_KEY).trim().split( ",\\s*" );
            Set<String> providedPkgSet = new HashSet<String>(Arrays.asList(providedPkgArr));
            List<GuvnorPackage> retList = new ArrayList<GuvnorPackage>();
            for(GuvnorPackage pkg : allPackages) {
                if(providedPkgSet.contains(pkg.getTitle())) {
                    retList.add(pkg);
                }
            }
            return retList;
        } else {
            return allPackages;
        }
    }

    private List<String> getPackageNames() {
        List<String> allPackages = getPackageNamesFromGuvnor();
        List<String> filteredPkgNames = filterPackageNamesByUserDefinedList(allPackages);
        return filteredPkgNames;
    }
  
    // public scope for testing
    public boolean canBuildPackage(String packageName) {
    	try {	
    		String packagesBinaryURL = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/" + packageName + "/binary";
    	
    	
    		URL checkURL = new URL(packagesBinaryURL);
            HttpURLConnection checkConnection = (HttpURLConnection) checkURL.openConnection();
            checkConnection.setRequestMethod("GET");
            checkConnection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
            checkConnection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
            applyAuth(checkConnection);
            checkConnection.connect();
            return checkConnection.getResponseCode() == 200;
    	} catch(Exception e) {
    		return false;
    	}
    }

    public List<String> getBuiltPackageNames() {
    	List<String> allPackageNames = getPackageNames();
    	
    	List<String> builtPackageNames = new ArrayList<String>();
    	for(String nextPkg : allPackageNames) {
    		if(canBuildPackage(nextPkg)) {
    			builtPackageNames.add(nextPkg);
    		} else {
    			logger.info("Excluding package: " + nextPkg + " because it cannot be built.");
    		}
    	}
    	
    	return builtPackageNames;
    }
    
    public List<GuvnorPackage> getBuiltPackages() { 
        List<GuvnorPackage> guvnorPkgs = getPackagesFromGuvnor();
        guvnorPkgs = filterPackagesByUserDefinedList(guvnorPkgs);
        List<GuvnorPackage> builtPkgs = new ArrayList<GuvnorPackage>();
        for(GuvnorPackage nextPkg : guvnorPkgs) {
            if(canBuildPackage(nextPkg.getTitle())) {
                builtPkgs.add(nextPkg);
            } else {
                logger.info("Excluding package: " + nextPkg.getTitle() + " because it cannot be built.");
            }
        }
        return builtPkgs;
    }
   
    protected void applyAuth(HttpURLConnection connection) {
        try {
            String auth = getGuvnorUsr() + ":" + getGuvnorPwd();

            connection.setRequestProperty("Authorization", "Basic "
                    + new String(Base64.encodeBase64(auth.getBytes("UTF-8"))));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    public boolean guvnorExists() {
        String checkURLStr = getGuvnorProtocol()
                + "://"
                + getGuvnorHost()
                + "/"
                + getGuvnorSubdomain()
                + "/rest/packages/";
        
        try {
            URL checkURL = new URL(checkURLStr);
            HttpURLConnection checkConnection = (HttpURLConnection) checkURL.openConnection();
            checkConnection.setRequestMethod("GET");
            checkConnection.setRequestProperty("Accept", "application/atom+xml");
            checkConnection.setConnectTimeout(Integer.parseInt(getGuvnorConnectTimeout()));
            checkConnection.setReadTimeout(Integer.parseInt(getGuvnorReadTimeout()));
            applyAuth(checkConnection);
            checkConnection.connect();
            return (checkConnection.getResponseCode() == 200);
        } catch (Exception e) {
            logger.error("Error checking guvnor existence: " + e.getMessage());
            return false;
        } 
    }
  
    // ChangeSet creation ---------------------------------------------------------------------------------------------------------

    // Is this still used?
    @Deprecated
    public StringReader createChangeSet() {
        return createChangeSet(getBuiltPackageNames());
    }
    
    public StringReader createChangeSet(List<String> packageNames) {
        try {
            StringTemplate changeSetTemplate = new StringTemplate(
                    readFile(GuvnorConnectionUtils.class.getResourceAsStream("/ChangeSet.st")));
            TemplateInfo info = new TemplateInfo(getGuvnorProtocol(), getGuvnorHost(), 
                    getGuvnorUsr(), getGuvnorPwd(), getGuvnorSubdomain(), packageNames);
            changeSetTemplate.setAttribute("data",  info.getData());
            return new StringReader(changeSetTemplate.toString());
        } catch (IOException e) {
            logger.error("Exception creating changeset: " + e.getMessage());
            return new StringReader("");
        }
    }
    
    private String readFile(InputStream inStream) throws IOException {
        StringBuilder fileContents = new StringBuilder();
        Scanner scanner = new Scanner(inStream);
        String lineSeparator = System.getProperty("line.separator");
        try {
            while(scanner.hasNextLine()) {        
                fileContents.append(scanner.nextLine() + lineSeparator);
            }
            return fileContents.toString();
        } finally {
            scanner.close();
        }
    }

    private class TemplateInfo {
        List<String> data = new ArrayList<String>();
        
        public TemplateInfo(String protocol, String host, String usr, String pwd,
                String subdomain, List<String> packages) {
            for(String pkg : packages) {
                StringBuffer sb = new StringBuffer();
                sb.append("<resource source=\"");
                sb.append(protocol).append("://");
                sb.append(host).append("/");
                sb.append(subdomain).append("/").append("org.drools.guvnor.Guvnor/package/");
                sb.append(pkg).append("/" + getGuvnorSnapshotName() + "\"");
                sb.append(" type=\"PKG\"");
                if(!isEmpty(usr) && !isEmpty(pwd)) {
                    sb.append(" basicAuthentication=\"enabled\"");
                    sb.append(" username=\"").append(StringEscapeUtils.escapeXml(usr)).append("\"");
                    sb.append(" password=\"").append(StringEscapeUtils.escapeXml(pwd)).append("\"");
                }
                sb.append(" />");
                data.add(sb.toString());
            }
        }

        public List<String> getData() {
            return data;
        }

        public void setData(List<String> data) {
            this.data = data;
        }
    }
    
}