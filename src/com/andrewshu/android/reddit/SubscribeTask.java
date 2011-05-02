package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class SubscribeTask extends AsyncTask<Void, Void, Boolean> {
	private static final String TAG = "Subscribe To Subreddit";

	private String mSubreddit;
	private String mUserError = "Error Subscribing.";
	private String mUrl;
	private RedditSettings mSettings;
	private Context mContext;
	
	private final DefaultHttpClient mClient = Common.getGzipHttpClient();

	
	
	public SubscribeTask(String mSubreddit, Context context, RedditSettings mSettings) {
		// TODO Auto-generated constructor stub
		this.mUrl = "http://www.reddit.com/api/subscribe";
		this.mContext = context;
		this.mSettings = mSettings;
		this.mSubreddit = mSubreddit;
	}

	
	@Override
	public void onPreExecute() {
		if (!mSettings.isLoggedIn()) {
    		Common.showErrorToast("You must be logged in to subscribe.", Toast.LENGTH_LONG, mContext);
    		cancel(true);
    		return;
    	}
		Toast.makeText(mContext, "Subscribed!", Toast.LENGTH_SHORT).show();
	}
	
	@Override
	protected Boolean doInBackground(Void... params) {
		
		String status = "";
    	HttpEntity entity = null;
		
    	if (!mSettings.isLoggedIn()) {
    		mUserError = "You must be logged in to subscribe.";
    		return false;
    	}
    	
    	updateModHash();
    	
		// Construct data
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("action", "sub"));
		nvps.add(new BasicNameValuePair("sr", Util.getSubredditId(mSubreddit)));
		nvps.add(new BasicNameValuePair("r", mSubreddit));
		nvps.add(new BasicNameValuePair("uh", mSettings.modhash.toString()));
		nvps.add(new BasicNameValuePair("renderstyle", "html"));

		
		try {
			HttpPost request = new HttpPost(mUrl);
			request.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			
			HttpResponse response = mClient.execute(request);
	    	status = response.getStatusLine().toString();
	    	
        	if (!status.contains("OK")) {
        		mUserError = mUrl;
        		throw new HttpException(mUrl);
        	}
        	
        	
			ArrayList<String> mSubredditsList = CacheInfo.getCachedSubredditList(mContext);	
			mSubredditsList.add(mSubreddit.toLowerCase());

			Collections.sort(mSubredditsList);
			
			CacheInfo.setCachedSubredditList(mContext, mSubredditsList);
        	
        	entity = response.getEntity();

        	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
        	String line = in.readLine();
        	in.close();
        	if (line == null || Constants.EMPTY_STRING.equals(line)) {
        		mUserError = "Connection error when subscribing. Try again.";
        		throw new HttpException("No content returned from subscribe POST");
        	}
        	if (line.contains("WRONG_PASSWORD")) {
        		mUserError = "Wrong password.";
        		throw new Exception("Wrong password.");
        	}
        	if (line.contains("USER_REQUIRED")) {
        		// The modhash probably expired
        		throw new Exception("User required. Huh?");
        	}
        	
        	Common.logDLong(TAG, line);
        	
        	entity.consumeContent();
        	return true;
        	
		} catch (Exception e) {
    		if (entity != null) {
    			try {
    				entity.consumeContent();
    			} catch (Exception e2) {
    				if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
    			}
    		}
    		if (Constants.LOGGING) Log.e(TAG, "SubscribeTask", e);
    	}
		
		return false;
	}
	
	private boolean updateModHash(){
    	// Update the modhash if necessary
    	if (mSettings.modhash == null) {
    		String modhash = Common.doUpdateModhash(mClient);
    		if (modhash == null) {
    			// doUpdateModhash should have given an error about credentials
    			Common.doLogout(mSettings, mClient, mContext);
    			if (Constants.LOGGING) Log.e(TAG, "updating save status failed because doUpdateModhash() failed");
    			return false;
    		}
    		mSettings.setModhash(modhash);
    	}
    	return true;
	}

}
