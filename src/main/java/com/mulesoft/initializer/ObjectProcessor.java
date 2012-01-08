package com.mulesoft.initializer;

public class ObjectProcessor {
	public String getPrice(String destination) {
		if (destination != null && "SFO".equalsIgnoreCase(destination.trim())) {
			return "300";
		}else if(destination != null && "NYC".equalsIgnoreCase(destination.trim())){
			return "500";
		}else
		{
			return "This destination is not available";
		}
		
	}
}

