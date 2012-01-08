package com.mulesoft.utils;

import java.util.ArrayList;

import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractMessageTransformer;

public class ObjectNamesHTMLTransformer extends AbstractMessageTransformer{

	@Override
	public Object transformMessage(MuleMessage msg, String encoding) throws TransformerException{
		
//		String lObjectNames;
//		try {
//			lObjectNames = (ArrayList)msg.getPayload();
//
//			String sHTML = "<html><body><h1>Results for Source connection</h1>";
//	//		for(String s : lObjectNames){
//	//			sHTML+="<li>"+lObjectNames+"</li>";
//	//		}
//			sHTML+=lObjectNames;
//			sHTML+="</body></html>";
//			return sHTML;
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		return null;
//		ArrayList<String> lPrices = (ArrayList<String>)msg.getPayload();
		String lPrices = (String)msg.getPayload();
		System.out.println(lPrices);
		return null;
//		String sHTML = "<html><body><h1>Results for "+msg.getSessionProperty("DestinationCode")+": </h1><ul> ";
//		for(String s : lPrices){
//			sHTML+="<li>"+s+"</li>";
//		}
//		sHTML+="</body></html>";
//		return sHTML;
	}
	
}
