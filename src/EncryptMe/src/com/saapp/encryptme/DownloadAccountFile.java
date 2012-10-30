package com.saapp.encryptme;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.saapp.encryptme.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.widget.EditText;
import android.widget.Toast;

public class DownloadAccountFile extends AsyncTask<Void, Long, Boolean> {

	private ProgressDialog mDialog = null;
	private EncryptMe mActivity;
	private Context mContext;
	private DropboxAPI<?> mApi;
	private EditText mEditTextView;
    private String mFileName;
    private Long mFileLen;
    private String mFileCachePath;
    private boolean mDownloadFile;
    private String mErrorMsg;
    
    public DownloadAccountFile(Context context, DropboxAPI<?> api, EditText editTextView){
    	
    	// We set the context this way so we don't accidentally leak activities
    	mActivity = (EncryptMe) context;
    	mContext = context.getApplicationContext();
    	mApi = api;
    	mEditTextView = editTextView;
    		
    	mDownloadFile = false;
  		mActivity.setIsEncrypted(false);
  		
    	Resources res = mContext.getResources();
    	mFileName = "/" + res.getString(R.string.dropbox_file_name);
    	
         mFileCachePath = mContext.getCacheDir().getAbsolutePath() + "/" + res.getString(R.string.cache_file_name);
    	 //check network connection
    	 if(mActivity.isOnline()){
    		//get file version number, compare with the local one (from share preference)
          	//if the same, check whether the cache file still exists, if yes, no need to download again
          	//get file meta data
          	try {
          		List<Entry> searchRlt = mApi.search("/", res.getString(R.string.dropbox_file_name), 1, false);
          		if(searchRlt != null && !searchRlt.isEmpty()){
//          			Entry fileMetaData = searchRlt.get(0);
      			Entry fileMetaData = mApi.metadata(mFileName, 1000, null, true, null);
          			
          			if(fileMetaData != null && !fileMetaData.isDeleted){
          			//get rev from shared preference, and compare with fileMetaData.rev
                  		SharedPreferences settings = mContext.getSharedPreferences(res.getString(R.string.shared_pref), 0);
                  		String localRev = settings.getString(res.getString(R.string.shared_pref_file_rev), null);
                  		String dbFileRev = fileMetaData.rev; //!dbFileRev.equals(localRev) 
                  		if(!dbFileRev.equals(localRev)){             			
                  			
                          	mFileLen = fileMetaData.bytes;

                          	if(mFileLen > 0){
                      			mDownloadFile = true;
                      			mDialog = new ProgressDialog(context);
                              	mDialog.setMessage("Downloading File");
                              	mDialog.show();
                          	}
                          	else{
                          		mDownloadFile = false;
                          		mEditTextView.setText(null);
                          		mActivity.setIsEncrypted(false);
                          	}
                  		}    
                  		//display the content from cached file
                  		else{
                  			mDownloadFile = false;
                  			showFileContent();
                  		}
          			}
          		}
              	
      		} 
          	catch (DropboxUnlinkedException e){
          		showToast("This app wasn't authenticated properly. Need to re-authenticate user");
          	}
      		catch (DropboxServerException e){
      			showToast("Error code is " + e.error);
      		}
      	catch (DropboxException e) {
			//first time open the app, the folder doesn;t exist, mApi.metadata will fail
			showToast("some unknown error");
		}
    	 }
    	 else{
    		//device is not online, using local cache
    		mDownloadFile = false;
    		showFileContent();
    		showToast("Device is not on line.");
    	 }
    	
    	
    }
    
	@Override
	protected Boolean doInBackground(Void... params) {
		if(!mDownloadFile){
			return true;
		}
		else{
			FileOutputStream outputStream = null;
	        try {
	        	try {
					outputStream = new FileOutputStream(mFileCachePath);
				} catch (FileNotFoundException e) {
					mErrorMsg = "Couldn't create a local file to store the file";
	                return false;
				}
	        	mApi.getFile(mFileName, null, outputStream, null);
	        	return true;
			} catch (DropboxException e) {
				mErrorMsg = "Get file from drop box failed.";
			} 
			finally {
				if(outputStream != null){
					try {
						outputStream.close();
					} catch (IOException e) {
						mErrorMsg = e.getMessage();
					}
				}
			}
			
			return false;
		}
	}

	 @Override
	 protected void onProgressUpdate(Long... progress) {
		 if(mFileLen != null){
			 int percent = (int)(100.0*(double)progress[0]/mFileLen + 0.5);
		     mDialog.setProgress(percent);
		 }	        
	 }
	 
	 @Override
	 protected void onPostExecute(Boolean result) {
		 if (result) {
			 if(mDownloadFile){
				 mDialog.dismiss(); 
				 showFileContent();	 
			 }
	        } else {
	            // Couldn't download it, so show an error
	            showToast(mErrorMsg);
	        }
	 }
	 
	 private void showFileContent(){
		 try {
			 File file = new File(mFileCachePath);
			 if(file.exists()){
				 InputStream fstream = new FileInputStream(mFileCachePath);
				 DataInputStream in = new DataInputStream(fstream);
				 BufferedReader br = new BufferedReader(new InputStreamReader(in));
				 StringBuilder strBuf = new StringBuilder((int)file.length());
				 String strLine = null;
				 while((strLine = br.readLine()) != null){
					 strBuf.append(strLine);
				 }
				 mActivity.setEncryptedText(strBuf.toString());
				 mEditTextView.setText(strBuf.toString());
				 mActivity.setIsEncrypted(true);
			 }
//				byte[] encryptedBytes = getBytesFromFile();
//				if(encryptedBytes != null){					
//					mActivity.setEncryptedText(encryptedBytes);
//					mEditTextView.setText(AES.asHex(encryptedBytes));
//					mActivity.setIsEncrypted(true);
					//temp, display plain text
//					String content = new String(encryptedBytes);
//					mEditTextView.setText(content);
//					mActivity.setIsEncrypted(false);
				}
//				else{
//					showToast("get file error!");
//				}
			 catch (FileNotFoundException e) {
				showToast(e.getMessage());
			} catch (IOException e) {
				showToast(e.getMessage());
			} 
	 }
	 
	// Returns the contents of the file in a byte array.
//	 private byte[] getBytesFromFile() throws IOException {		
//		 byte[] bytes = null; 
//		 File file = new File(mFileCachePath);
//		 if(file.exists()){
//			 Long byteLength = file.length(); //get the number of bytes in the file
//			 
//		     InputStream is = new FileInputStream(mFileCachePath);
//		    
//		     
//		     // You cannot create an array using a long type.
//		     // It needs to be an int type.
//		     // Before converting to an int type, check
//		     // to ensure that file is not larger than Integer.MAX_VALUE.
//		     if (byteLength < Integer.MAX_VALUE) {
//		    	// Create the byte array to hold the data
//			     bytes = new byte[(int)byteLength.longValue()];
//
//			     // Read in the bytes
//			     int offset = 0;
//			     int numRead = 0;
//			     while (offset < bytes.length
//			            && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
//			         offset += numRead;
//			     }
//
//			     // Ensure all the bytes have been read in
//			     if (offset < bytes.length) {
//			         throw new IOException("Could not completely read file "+mFileCachePath);
//			     }
//
//			     // Close the input stream and return bytes
//			     is.close();
//		     }
//		 }
//		 
//	     
//	     return bytes;
//	 }
	 
	 private void showToast(String msg) {
	        Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
	        error.show();
	    }

}
