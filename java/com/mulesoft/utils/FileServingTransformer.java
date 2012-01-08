package com.mulesoft.utils;

import java.io.IOException;

import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transformer.TransformerException;
import org.mule.transformer.AbstractTransformer;
import org.mule.util.IOUtils;

public class FileServingTransformer  extends AbstractTransformer{
	private String filePath;
	private volatile String fileContents=null;
	
	@Override
	protected Object doTransform(Object src, String enc)
			throws TransformerException {
		return fileContents;
	}
	
	@Override
	public void initialise() throws InitialisationException
    {
        try {
        	fileContents=IOUtils.getResourceAsString(filePath, this.getClass());
		} catch (IOException e) {
			throw new InitialisationException(e,this);
		}
    }

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
}

