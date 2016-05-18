/*
 * Copyright (c) 2013, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese 
 * opensource volunteers. you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Any questions about this component can be directed to it's project Web address 
 * https://code.google.com/p/opencloudb/.
 *
 */
package org.opencloudb.config.model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opencloudb.MycatConfig;
import org.opencloudb.MycatServer;
import org.opencloudb.config.loader.xml.XMLServerLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.spi.MySqlWallProvider;

/**
 * 隔离区配置定义
 * 
 * @author songwie
 */
public final class QuarantineConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(QuarantineConfig.class);
	
    private Map<String, List<UserConfig>> whitehost;
    private List<String> blacklist;
    private boolean check = false;
    
    private WallConfig wallConfig = new WallConfig();
     
    private static WallProvider provider ;
    
    public QuarantineConfig() { }
    
    public void init(){
    	if(check){
    		provider = new MySqlWallProvider(wallConfig);
    		provider.setBlackListEnable(true);
    	}
    }
    
    public WallProvider getWallProvider(){
    	return provider;
    }

	public Map<String, List<UserConfig>> getWhitehost() {
		return this.whitehost;
	}
	public void setWhitehost(Map<String, List<UserConfig>> whitehost) {
		this.whitehost = whitehost;
	}
	
	public boolean addWhitehost(String host, List<UserConfig> Users) {
		if (existsHost(host)){
			return false;	
		}
		else {
		 this.whitehost.put(host, Users);
		 return true;
		}
	}
	
	public List<String> getBlacklist() {
		return this.blacklist;
	}
	public void setBlacklist(List<String> blacklist) {
		this.blacklist = blacklist;
	}
	
	public WallProvider getProvider() {
		return provider;
	}

	public boolean existsHost(String host) {
		return this.whitehost==null ? false : whitehost.get(host)!=null ;
	}
	public boolean canConnect(String host,String user) {
		if(whitehost==null || whitehost.size()==0){
			MycatConfig config = MycatServer.getInstance().getConfig();
			Map<String, UserConfig> users = config.getUsers();
			return users.containsKey(user);
		}else{
			List<UserConfig> list = whitehost.get(host);
			if(list==null){
				return false;
			}
			for(UserConfig userConfig : list){
				if(userConfig.getName().equals(user)){
					return true;
				}
			}
		}
		return false ;
	}
	
	public static void setProvider(WallProvider provider) {
		QuarantineConfig.provider = provider;
	}

	public void setWallConfig(WallConfig wallConfig) {
		this.wallConfig = wallConfig;
		
	}

	public boolean isCheck() {
		return this.check;
	}

	public void setCheck(boolean check) {
		this.check = check;
	}

	public WallConfig getWallConfig() {
		return this.wallConfig;
	}
	
	public synchronized static void updateToFile(String host, List<UserConfig> userConfigs) throws Exception{
		LOGGER.debug("set white host:" + host + "user:" + userConfigs);
		String filename = SystemConfig.getHomePath()+ File.separator +"conf"+ File.separator +"server.xml";
		//String filename = "E:\\MyProject\\Mycat-Server\\src\\main\\resources\\server.xml";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new IgnoreDTDEntityResolver());
        Document xmldoc = builder.parse(filename);
        Element whitehost = (Element) xmldoc.getElementsByTagName("whitehost").item(0);
        Element quarantine = (Element) xmldoc.getElementsByTagName("quarantine").item(0);
        
        if(quarantine==null){
        	quarantine = xmldoc.createElement("quarantine");
            Element root = xmldoc.getDocumentElement();
            root.appendChild(quarantine);
            if(whitehost==null){
            	whitehost = xmldoc.createElement("whitehost");
            	quarantine.appendChild(whitehost);
            }
        }

        for(UserConfig userConfig : userConfigs){
        	String user = userConfig.getName();
        	Element hostEle = xmldoc.createElement("host");
        	hostEle.setAttribute("host", host);
        	hostEle.setAttribute("user", user);

        	whitehost.appendChild(hostEle);
        }
        
             
        TransformerFactory factory2 = TransformerFactory.newInstance();
        Transformer former = factory2.newTransformer();
        String systemId = xmldoc.getDoctype().getSystemId();
        if(systemId!=null){
            former.setOutputProperty(javax.xml.transform.OutputKeys.DOCTYPE_SYSTEM, systemId);    
        }
        former.transform(new DOMSource(xmldoc), new StreamResult(new File(filename)));

	}
	static class IgnoreDTDEntityResolver implements EntityResolver{
		public InputSource resolveEntity(java.lang.String publicId, java.lang.String systemId) throws SAXException, java.io.IOException{
			if (systemId.contains("server.dtd")){ 
				//InputSource is = new InputSource(new ByteArrayInputStream("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes()));
				InputStream dtd = XMLServerLoader.class.getResourceAsStream("/server.dtd");
				InputSource is = new InputSource(dtd);
				return is; 
		    } else 
		    	return null; 
			} 
	}
	public static void main(String[] args) throws Exception {
        List<UserConfig> userConfigs = new ArrayList<UserConfig>();
        UserConfig user = new UserConfig();
        user.setName("mycat");
        userConfigs.add(user);
		updateToFile("127.0.0.1",userConfigs);
	}
	
	
}