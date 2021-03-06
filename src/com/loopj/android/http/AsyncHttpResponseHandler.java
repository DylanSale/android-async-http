/*
    Android Asynchronous Http Client
    Copyright (c) 2011 James Smith <james@loopj.com>
    http://loopj.com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.loopj.android.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.util.EntityUtils;

import android.os.Handler;
import android.os.Message;
import android.os.Looper;

/**
 * Used to intercept and handle the responses from requests made using 
 * {@link AsyncHttpClient}. The {@link #onSuccess(String)} method is 
 * designed to be anonymously overridden with your own response handling code.
 * <p>
 * Additionally, you can override the {@link #onFailure(Throwable, String)},
 * {@link #onStart()}, and {@link #onFinish()} methods as required.
 * <p>
 * For example:
 * <p>
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get("http://www.google.com", new AsyncHttpResponseHandler() {
 *     &#064;Override
 *     public void onStart() {
 *         // Initiated the request
 *     }
 *
 *     &#064;Override
 *     public void onSuccess(String response) {
 *         // Successfully got a response
 *     }
 * 
 *     &#064;Override
 *     public void onFailure(Throwable e, String response) {
 *         // Response failed :(
 *     }
 *
 *     &#064;Override
 *     public void onFinish() {
 *         // Completed the request (either success or failure)
 *     }
 * });
 * </pre>
 */
public class AsyncHttpResponseHandler {
    private static final int SUCCESS_MESSAGE = 0;
    private static final int FAILURE_MESSAGE = 1;
    private static final int START_MESSAGE = 2;
    private static final int FINISH_MESSAGE = 3;

    private Handler handler;
    private File saveContentToFile = null;
    
    /**
     * Creates a new AsyncHttpResponseHandler
     * @param convertResponseToString if this is false, the callbacks will be given null as the content string
     * and you have to get the data from the response object's entity directly.
     */
    public AsyncHttpResponseHandler(File saveContentToFile) {
        // Set up a handler to post events back to the correct thread if possible
        if(Looper.myLooper() != null) {
            handler = new Handler(){
                public void handleMessage(Message msg){
                    AsyncHttpResponseHandler.this.handleMessage(msg);
                }
            };
        }
        this.saveContentToFile = saveContentToFile;
    }
    
    public AsyncHttpResponseHandler() {
    	this(null);
    }


    //
    // Callbacks to be overridden, typically anonymously
    //

    /**
     * Fired when the request is started, override to handle in your own code
     */
    public void onStart() {}

    /**
     * Fired in all cases when the request is finished, after both success and failure, override to handle in your own code
     */
    public void onFinish() {}

    /**
     * Fired when a request returns successfully, override to handle in your own code
     * @param content the body of the HTTP response from the server
     */
    public void onSuccess(HttpResponse response, String content) {}

    /**
     * Fired when a request that was given a file to save the response to is successful
     * @param response the response object
     * @param responseFile the file that the response was saved to 
     */
    public void onSuccess(HttpResponse response, File responseFile) {}
    
    
    /**
     * Fired when a request fails to complete, override to handle in your own code
     * @param error the underlying cause of the failure
     * @deprecated use {@link #onFailure(Throwable, String)}
     */
    public void onFailure(Throwable error) {}

 
    /**
     * Fired when a request fails to complete, override to handle in your own code
     * @param error the underlying cause of the failure
     * @param response the response returned from the server
     * @param content the response body, if any
     */
    public void onFailure(Throwable error, String content) {
        // By default, call the deprecated onFailure(Throwable) for compatibility
        onFailure(error);
    }

    /**
     * Fired when a request fails to complete, override to handle in your own code
     * @param error the underlying cause of the failure
     * @param response the response returned from the server
     * @param content the response body, if any
     */
    public void onFailure(Throwable error, HttpResponse response, String content) {
    	onFailure(error, content);
    }


    //
    // Pre-processing of messages (executes in background threadpool thread)
    //

    protected void sendSuccessMessage(HttpResponse response, String responseBody, File saveContentToFile) {
        sendMessage(obtainMessage(SUCCESS_MESSAGE, new Object[]{response, responseBody, saveContentToFile}));
    }

    protected void sendFailureMessage(Throwable e, HttpResponse response, String responseBody) {
        sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{e, response, responseBody}));
    }

    protected void sendStartMessage() {
        sendMessage(obtainMessage(START_MESSAGE, null));
    }

    protected void sendFinishMessage() {
        sendMessage(obtainMessage(FINISH_MESSAGE, null));
    }


    //
    // Pre-processing of messages (in original calling thread, typically the UI thread)
    //

    protected void handleSuccessMessage(HttpResponse response, String responseBody, File responseFile) {
    	if(responseFile != null) {
        	onSuccess(response, responseFile);
    	}
    	else 
    	{
    		onSuccess(response, responseBody);
    	}
    }

	protected void handleFailureMessage(Throwable e, HttpResponse response, String responseBody) {
        onFailure(e, response, responseBody);
    }



    // Methods which emulate android's Handler and Message methods
    protected void handleMessage(Message msg) {
    	Object[] response = (Object[])msg.obj;
        switch(msg.what) {
            case SUCCESS_MESSAGE:
                handleSuccessMessage((HttpResponse)response[0], (String)response[1], (File)response[2]);
                break;
            case FAILURE_MESSAGE:
                handleFailureMessage((Throwable)response[0], (HttpResponse)response[1], (String)response[2]);
                break;
            case START_MESSAGE:
                onStart();
                break;
            case FINISH_MESSAGE:
                onFinish();
                break;
        }
    }

    protected void sendMessage(Message msg) {
        if(handler != null){
            handler.sendMessage(msg);
        } else {
            handleMessage(msg);
        }
    }

    protected Message obtainMessage(int responseMessage, Object response) {
        Message msg = null;
        if(handler != null){
            msg = this.handler.obtainMessage(responseMessage, response);
        }else{
            msg = new Message();
            msg.what = responseMessage;
            msg.obj = response;
        }
        return msg;
    }


    // Interface to AsyncHttpRequest
    void sendResponseMessage(HttpResponse response) {
        StatusLine status = response.getStatusLine();
        String responseBody = null;
        if(saveContentToFile == null) {
	        try {
	            HttpEntity entity = null;
	            HttpEntity temp = response.getEntity();
	            if(temp != null) {
	                entity = new BufferedHttpEntity(temp);
	                responseBody = EntityUtils.toString(entity, "UTF-8");
	            }
	        } catch(IOException e) {
	            sendFailureMessage(new Throwable("BufferedHttpEntity IO exception"), response, null);
	        }
        } else {
        	try {
	            HttpEntity entity = response.getEntity();
	            if(entity != null) {
	            	OutputStream outFileStream = new FileOutputStream(saveContentToFile);
	            	entity.writeTo(outFileStream);
	            	outFileStream.flush();
	            	outFileStream.close();
	            }
        	}
        	catch(IOException e) {
	            sendFailureMessage(new Throwable("BufferedHttpEntity IO exception"), response, null);        		
        	}
        }
        
        if(status.getStatusCode() >= 300) {
            //sendFailureMessage(new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()), responseBody);
        	String detailMessage = status.getStatusCode() + " - " + status.getReasonPhrase();
        	sendFailureMessage(new Throwable(detailMessage), response, responseBody);
        } else {
            sendSuccessMessage(response, responseBody, saveContentToFile);
        }
    }
}