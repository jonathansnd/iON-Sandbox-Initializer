package com.mule.hackathon;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sforce.soap.partner.*;

import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.modules.salesforce.config.SalesforceModuleConnectionManager;
import org.mule.modules.salesforce.config.SalesforceModuleLifecycleAdapter;
import org.mule.transformer.AbstractMessageTransformer;

public class SandboxInitializer extends AbstractMessageTransformer{

	private Map<String,ExtObject> mapObjectsState = new HashMap<String,ExtObject>();
	private List<String> lPrimaryKeys = new ArrayList<String>();
	private List<String> lForeignKeys = new ArrayList<String>();

	private SalesforceModuleLifecycleAdapter connection1;
	private SalesforceModuleLifecycleAdapter connection2;
	private String sMainObject = "";
	
	private String User1 = "";
	private String Pass1 = "";
	private String Token1 = "";
	private String User2 = "";
	private String Pass2 = "";
	private String Token2 = "";
	
	/* Internal Class for handling Objects state*/
	private class ExtObject{
		
		public String NewId = null;
		public String OldId = null;
		public String SObjectType = null;
		public Boolean IsLookup = false;
		public Map<String,Object> SFObject = null;
		
	}
	
	@Override
	public Object transformMessage(MuleMessage msg, String encoding) throws TransformerException{
		
		try {
			
			//Set credentials
			Map<String,String> postMap = (Map<String,String>)msg.getPayload();
			System.out.println("MAP VALUE "+postMap.toString());
			User1 = postMap.get("userOrig");
			Pass1 = postMap.get("passOrig");
			Token1 = postMap.get("tokenOrig");
			User2 = postMap.get("userDest");
			Pass2 = postMap.get("passDest");
			Token2 = postMap.get("tokenDest");
			sMainObject = postMap.get("objectsCombo");
			System.out.println("Selected Object : "+sMainObject);
			String sResult = InitializeSandbox(sMainObject,5);
			return true;
		} catch (Exception e) {
			return e.getMessage();
		}
	}
	
	public String InitializeSandbox(String sSelectedObjectName, int totalRecords){
		
		try{
			
			SalesforceModuleConnectionManager manager =((SalesforceModuleConnectionManager) muleContext.getRegistry().lookupObject(("Salesforce")));
			connection1 = manager.acquireConnection(new SalesforceModuleConnectionManager.ConnectionParameters(User1,Pass1,Token1));			

			System.out.println("==>  Connection : "+manager.getUsername());
			
			sMainObject = sSelectedObjectName;
			DescribeSObjectResult describeSObjectResult = connection1.describeSObject(sSelectedObjectName);
			InspectObject(describeSObjectResult,totalRecords,null,null,null,null,false);
			StartCopy();
			
			return "Success";
			
		}catch(Exception e){
			
			System.out.println(e.toString());
			return null;
		}
		
	}

	
	private void InspectObject(DescribeSObjectResult sObjResult,int totalRecords,List<String> lParentIds,String sParentField, Set<String> setIgnoreParents, Set<String> setIgnoreChildren,Boolean bIgnoreChildren){
		
		try {
		
			System.out.println("==> Processing Object : "+sObjResult.getName());
			
			//Prevent Recursion, Save Parent and Children references
			Set<String> setNewIgnoreParent = new HashSet<String>();
			Set<String> setNewIgnoreChildren = new HashSet<String>();
			List<String> lNewParentIds = new ArrayList<String>();
			
			//Ignore current object as parent in next relations
			setNewIgnoreParent.add(sObjResult.getName());
			
			//Ignore current object as children in next relations
			setNewIgnoreChildren.add(sObjResult.getName());
			
			//Ignore current record child relations for subsequent children
			ChildRelationship[] crs1 = sObjResult.getChildRelationships();
			for(ChildRelationship cr1 : crs1){
				if(cr1.getRelationshipName() != null && cr1.getRelationshipName().contains("__r"))
					setNewIgnoreChildren.add(cr1.getChildSObject());
			}
			
			//Get supported field for current Object
			Field[] sfFields = sObjResult.getFields();
			Map<String,Field> mapFields = new HashMap<String,Field>();
			String sFields = "Id";
			Boolean bHasLookups = false;
			
			//Auxiliary collections to save references of Parent Lookups
			Map<String,List<String>> mapLookups = new HashMap<String,List<String>>(); //Key => Object Name , Value => List of IDs
			Map<String,String> mapLookUpFields = new HashMap<String,String>(); //Key => Field Name, Value => Object Name
			
//			System.out.println("==> Current Parent Lookups ....");
//			System.out.println("-------------------------------");
			
			Boolean bIsPersonAccount = false;
			
			//Iterate through fields and create dynamic query
			for(Field f :sfFields){
				
				//Ignore Personal Account fields
				if(sObjResult.getName().equals("Account") && f.getName().contains("__pc")){
					
					bIsPersonAccount =true;
					continue;
					
				}else{
					
					mapFields.put(f.getName(), f);
					if(f.isCreateable() && (f.isNameField() || f.isCustom() || f.isIdLookup() || f.isExternalId())){
							
						sFields+=" , ";
						sFields+=f.getName();
						
						//System.out.println("!!!!!!!!!! >>> "+f.getName() );
						//System.out.println("!!!!!!!!!! >>> "+f.getExternalId()+" "+f.getType()+" "+f.getRelationshipName()+" "+f.getCustom()+" "+f.isCreateable()+" "+f.isNameField() );
						if(f.getRelationshipName() != null){
							
							//System.out.println("!!!!!!!!!! >>> IS LOOKUP");
							String[] lReferences = f.getReferenceTo();
							System.out.println("********** Relationship Target Object : "+lReferences[0]);
							String sRef = lReferences[0];
	
							//Ignore lookups to User object and self-references
							if(!sRef.equals("User") && !sRef.equals(sObjResult.getName())){
							
								bHasLookups = true;
								System.out.println("********** Relationship Name : "+f.getRelationshipName());
		
								mapLookups.put(sRef,new ArrayList<String>());
								System.out.println("********** Relationship Field : "+f.getName());
								mapLookUpFields.put(f.getName(),sRef);
								System.out.println("-------------------------------");
							}
						}
						
						
					}
				}
			}

			
			//Query Database
			String sQuery = "select "+sFields+" from "+sObjResult.getName();
			
			//Add Where Clause if applicable

			Boolean bSkipQuery = false;;
			
			//Process Child RelationShips
			if(lParentIds != null){
				
				String sParentIDs = "";
				for(String sId : lParentIds){
					if(sParentIDs != "")
						sParentIDs+=" , ";
					sParentIDs+="'"+sId+"'";
				}
				
				if(sParentIDs == "")
					bSkipQuery = true;
				
				int totalRecs = totalRecords*totalRecords;
				if(sParentField != null )
					sQuery +=" Where "+sParentField+" in ("+sParentIDs+") limit "+totalRecs;
				if(sParentField == null)
					sQuery +=" Where Id in ("+sParentIDs+")";
				
			}else{
				
				sQuery += " limit "+totalRecords;
			}

//			System.out.println("==> Running query : "+sQuery);
			
			if(!bSkipQuery){
				
				//Populate Object State Map
				List<Map<String,Object>> QueryResult = connection1.query(sQuery);
//				System.out.println("==> Query returned : "+QueryResult.toString());
				
				if(QueryResult.size() > 0){
					
					for(Map<String,Object> obj : QueryResult){
						
						//Create an object for the object state map for every record
						ExtObject eobj = new ExtObject();
						eobj.OldId = (String)obj.get("Id");
						eobj.SObjectType = sObjResult.getName();
						
						eobj.SFObject = obj;
						
						if(!bHasLookups){
							eobj.IsLookup = true;
						}else{
							
							for(String sField : mapLookUpFields.keySet()){
								if(obj.containsKey(sField)){
									
									System.out.println("********** SField 1 "+sField);
									if(obj.get(sField) != null && obj.get(sField).getClass().getName() != "java.util.HashMap") {
										System.out.println("********** SField 2 "+sField);
										mapLookups.get(mapLookUpFields.get(sField)).add((String)obj.get(sField));
										String sVal = (String)obj.get(sField);
										lPrimaryKeys.add(eobj.OldId);
										lForeignKeys.add((String)obj.get(sField));
										eobj.SFObject.put(sField,"#@#@#"+(String)obj.get(sField));
										System.out.println("********** Putting #@#@#"+(String)obj.get(sField));
									}
								}
							}					
						}
						
						DateFormat df = new SimpleDateFormat ("yyyy-MM-dd");
						//Clear null values
						for(String sField : obj.keySet()){
							
							if(obj.get(sField).getClass().getName() == "java.util.HashMap"){
								//System.out.println("********** FOUND NULL");
								obj.put(sField,"");
							}else{
							
								if(mapFields.containsKey(sField)){
									if(mapFields.get(sField).getType() == FieldType._boolean){
										obj.put(sField, (String)obj.get(sField)=="true"?true:false);
									}else if(mapFields.get(sField).getType() == FieldType.date){
										obj.put(sField, df.parse((String)obj.get(sField)));
									}
								}
								
							}
						}
						
						mapObjectsState.put(eobj.OldId , eobj);
						
						//Add current Id to list of new (processed) parents
						lNewParentIds.add(eobj.OldId);		
					}
					
					//Set up Next Step
					
					//Look for Parent References
					if(bHasLookups){
						
						for(String sRelObjectName : mapLookups.keySet()){
							
							if((setIgnoreParents != null && !setIgnoreParents.contains(sRelObjectName)) || setIgnoreParents == null){
								
								List<String> lParentLUIds = new ArrayList<String>();
								lParentLUIds.addAll(mapLookups.get(sRelObjectName));
								DescribeSObjectResult describeSObjectResult = connection1.describeSObject(sRelObjectName);
								System.out.println("********** Processing Parent "+sRelObjectName);
								
								//If this is the main node, Only look for  parents
								if(setIgnoreParents == null)
									bIgnoreChildren = true;
						
								InspectObject(describeSObjectResult,totalRecords,lParentLUIds,null,setNewIgnoreParent,setNewIgnoreChildren,bIgnoreChildren);
							}
						}
							
					}
					
					//Look for Child References
					ChildRelationship[] crs = sObjResult.getChildRelationships();
					int iCount = 0;
					
					//Only explore child relationships when the current object isn't a parent of a previous child
					if((lParentIds == null && sParentField == null) || (lParentIds != null && sParentField == null)){
						
						for(ChildRelationship cr : crs){
							
//							String sOutput = "==> Child "+iCount+" : Name : "+cr.getRelationshipName()+" SObject Name : "+ cr.getChildSObject()+" SObject Field : "+cr.getField();
		
							if((setIgnoreChildren != null && !setIgnoreChildren.contains(cr.getChildSObject()) || setIgnoreChildren == null)){
			
								if(cr.getRelationshipName() != null && cr.getRelationshipName().contains("__r") && !cr.getChildSObject().equals(sMainObject)){
//									sOutput+=" [ OK ]";
									String sRelObjectName = cr.getChildSObject();
									String sPField = cr.getField();
									DescribeSObjectResult describeSObjectResult = connection1.describeSObject(sRelObjectName);
//									System.out.println(sOutput);
									System.out.println("********** Processing Child : "+sRelObjectName);
									
									//If this is the main node, Only look for  parents
									if(setIgnoreParents == null)
										bIgnoreChildren = true;
									
									InspectObject(describeSObjectResult,totalRecords,lNewParentIds,sPField,setNewIgnoreParent,setNewIgnoreChildren,bIgnoreChildren);
								}else{
//									sOutput+=" [ IGNORED ]";
//									System.out.println(sOutput);
								}
								
							}
							//Just count for fun
							iCount++;
			
						}
					
					}
				
				}
			
			}
			
			System.out.println("==> Record Count : "+mapObjectsState.size());
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void StartCopy(){

		try{
			
			System.out.println("==> Starting Copy ");
			SalesforceModuleConnectionManager manager2 =((SalesforceModuleConnectionManager) muleContext.getRegistry().lookupObject(("Salesforce")));
			connection2 = manager2.acquireConnection(new SalesforceModuleConnectionManager.ConnectionParameters(User2,Pass2,Token2));	
			
			Map<String,List<ExtObject>> mapLUInsert = new HashMap<String,List<ExtObject>>();
			
			Set<String> setLookupIds = new HashSet<String>();
			Set<String> setCoreIds = new HashSet<String>();
			
			System.out.println("==> Classifying Objects ");
			for(ExtObject eo : mapObjectsState.values()){
				
				if(eo.IsLookup){
					setLookupIds.add(eo.OldId);
				}else{
					setCoreIds.add(eo.OldId);
				}
			}
			
			//Insert Lookups
			System.out.println("==> Inserting Lookup Objects ");
			if(setLookupIds.size()>0){
				
				for(String eoId : setLookupIds){
					
					ExtObject eo = mapObjectsState.get(eoId);
					//Remove Id so it's treated as a new object
					eo.SFObject.remove("Id");
					if(!mapLUInsert.containsKey(eo.SObjectType)){
						List<ExtObject> l = new ArrayList<ExtObject>();
						l.add(eo);
						mapLUInsert.put(eo.SObjectType,l);
					}else{
						List<ExtObject> l = mapLUInsert.get(eo.SObjectType);
						l.add(eo);		
						mapLUInsert.put(eo.SObjectType,l);
					}
					
				}
				
				System.out.println("==> Inserting Lookups ");
				//Create Lookups
				for(String sObjName : mapLUInsert.keySet()){
					
					System.out.println("==> Inserting records for : "+sObjName);
					List<ExtObject> lEo = mapLUInsert.get(sObjName);
					List<Map<String,Object>> lSFObjects = new ArrayList<Map<String,Object>>();
					for(ExtObject eo : lEo){
						lSFObjects.add(eo.SFObject);
					}
					
					List<SaveResult> lResults = connection2.create(sObjName,lSFObjects);
					
					//Save new Ids
					for(int i = 0;i < lResults.size();i++){
						
						SaveResult sr = lResults.get(i);
		
						ExtObject eo = mapLUInsert.get(sObjName).get(i);
						if(sr.isSuccess()){
							eo.NewId = sr.getId();
							System.out.println("==> OLD ID : "+eo.OldId+" NEW ID :"+eo.NewId);
						}else{
							String sErrMessage = sr.getErrors()[0].getMessage();
							System.out.println("==> SAVE RESULT : "+sErrMessage);
							if(sErrMessage.contains("duplicates value")){
								String[] lId = sErrMessage.split("id: ");
								if(lId.length > 0){
									System.out.println(eo.OldId+" ==> Using existing record with Id :"+lId[1]);
									eo.NewId = lId[1];
								}
							}
						}
					}
				}
				
			}
			
			System.out.println("==> Inserting Core Objects "+setCoreIds.size());
			
			Boolean bMissingRecords = true;
			int iCount = 0;
			
			while(bMissingRecords && iCount < 10){
				
				Map<String,List<ExtObject>> mapCoreInsert = new HashMap<String,List<ExtObject>>();
				
				bMissingRecords = false;
				iCount++;
				
				if(setCoreIds.size()>0){
					
					for(String eoId : setCoreIds){
						
						ExtObject eo = mapObjectsState.get(eoId);
						
						//Only Continue if the object
						if(eo.NewId == null){
							
							Boolean bContinue = true;
							
							//Verify Foreign Keys exist
							for(String s : eo.SFObject.keySet()){
								
								System.out.println("==>  Field "+s+" => "+(String)eo.SFObject.get(s));
								
								if(eo.SFObject.get(s).getClass().getName() == "java.lang.String"){
									
									String sVal = (String)eo.SFObject.get(s);
									if(sVal.contains("#@#@#")){
			
										String sLUId = sVal.replaceAll("#@#@#", "");
										System.out.println("==> Found LU Field "+s+" => "+sLUId);
										ExtObject refEo = mapObjectsState.get(sLUId);
										
										if(refEo.NewId != null){
											eo.SFObject.put(s, refEo.NewId);
											System.out.println("==> Found record match for "+s+" : "+sLUId+" using new id : "+refEo.NewId);
										}else{
											System.out.println("==> Couldn't find record match for : "+sLUId);
											bContinue = false;
											bMissingRecords = true;
											break;
										}
									}
								}
								
							}
							
							if(bContinue){
								
								//Remove Id so it's treated as a new object
								eo.SFObject.remove("Id");
								if(!mapCoreInsert.containsKey(eo.SObjectType)){
									
									List<ExtObject> l = new ArrayList<ExtObject>();
									l.add(eo);
									mapCoreInsert.put(eo.SObjectType,l);
								}else{
	
									List<ExtObject> l = mapCoreInsert.get(eo.SObjectType);
									l.add(eo);		
								}
								
								
								for(String sField : eo.SFObject.keySet()){
									System.out.println("==> Object Field "+sField+" : "+eo.SFObject.get(sField).toString());
									
								}
								
							}
						
						}
					}
	
					System.out.println("==> Inserting Core Objects ");
					//Create Lookups
					for(String sObjName : mapCoreInsert.keySet()){
						
						System.out.println("==> Inserting "+mapCoreInsert.get(sObjName).size()+" records for : "+sObjName);
						List<ExtObject> lEo = mapCoreInsert.get(sObjName);
						List<Map<String,Object>> lSFObjects = new ArrayList<Map<String,Object>>();
						for(ExtObject eo : lEo){
							lSFObjects.add(eo.SFObject);
						}
						
						List<SaveResult> lResults = connection2.create(sObjName,lSFObjects);
						
						//Save new Ids
						for(int i = 0;i < lResults.size();i++){
							
							SaveResult sr = lResults.get(i);
							ExtObject eo = mapCoreInsert.get(sObjName).get(i);
							
							if(sr.isSuccess()){
								eo.NewId = sr.getId();
								System.out.println("==> OLD ID : "+eo.OldId+" NEW ID :"+eo.NewId);
							}else{
								String sErrMessage = sr.getErrors()[0].getMessage();
								System.out.println("==> SAVE RESULT : "+sErrMessage);
								if(sErrMessage.contains("duplicates value")){
									String[] lId = sErrMessage.split("id: ");
									if(lId.length > 0){
										System.out.println(eo.OldId+" ==> Using existing record with Id :"+lId[1]);
										eo.NewId = lId[1];
										mapObjectsState.put(eo.OldId,eo);
									}
								}
							}
						}
					}
					
				}
			
			}
			
			
		}catch(Exception e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}
