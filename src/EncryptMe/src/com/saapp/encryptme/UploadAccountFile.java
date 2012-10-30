package com.saapp.encryptme;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.saapp.encryptme.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.widget.Toast;

public class UploadAccountFile extends AsyncTask<Void, Long, Boolean> {
	
	private ProgressDialog mDialog = null;
	private Context mContext;
	private DropboxAPI<?> mApi;
	private String mEncryptedContent;
    private String mFileName;
    private String mErrorMsg;
    private long mFileLength;
    private String mUpdatedFileVersion;

    public UploadAccountFile(Context context, DropboxAPI<?> api, String encryptedContent){
		mContext = context.getApplicationContext();
    	mApi = api;
    	mEncryptedContent = encryptedContent;
    	mFileLength = encryptedContent.length();
    	
    	Resources res = context.getResources();
    	mFileName = "/" + res.getString(R.string.dropbox_file_name);
       
        mDialog = new ProgressDialog(context);
        mDialog.setMax(100);
        mDialog.setMessage("Uploading " + mFileName);
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setProgress(0);
        mDialog.show();
    }

	@Override
	protected Boolean doInBackground(Void... params) {
		try{
			ByteArrayInputStream inputStream = new ByteArrayInputStream(mEncryptedContent.getBytes());
			Entry fileMetaData = mApi.putFileOverwrite(mFileName, inputStream, mFileLength, new ProgressListener(){

				@Override
                public long progressInterval() {
                    // Update the progress bar every half-second or so
                    return 500;
                }
				
				@Override
				public void onProgress(long bytes, long total) {
					publishProgress(bytes);
				}
				
			});
			
			mUpdatedFileVersion = fileMetaData.rev;
			return true;
			
		} catch(DropboxUnlinkedException e) {
			// This session wasn't authenticated properly or user unlinked
            mErrorMsg = "This app wasn't authenticated properly.";
		} catch (DropboxFileSizeException e){
			 // File size too big to upload via the API
            mErrorMsg = "This file is too big to upload";
		} catch (DropboxPartialFileException e) {
            // We canceled the operation
            mErrorMsg = "Upload canceled";
        } catch (DropboxServerException e) {
            // Server-side exception.  These are examples of what could happen,
            // but we don't do anything special with them here.
            if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                // Unauthorized, so we should unlink them.  You may want to
                // automatically log the user out in this case.
            	mErrorMsg = "Dropbox error: unauthorized error";
            } else if (e.error == DropboxServerException._403_FORBIDDEN) {
                // Not allowed to access this
            	mErrorMsg = "Dropbox error: not allowed to access";
            } else if (e.error == DropboxServerException._404_NOT_FOUND) {
                // path not found 
            	mErrorMsg = "Dropbox error: path not found";
            } else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
                // user is over quota
            	mErrorMsg = "Dropbox error: user is over quota";
            } else {
                // Something else
            	mErrorMsg = "Dropbox error";
            }
            // This gets the Dropbox error, translated into the user's language
            mErrorMsg = e.body.userError;
            if (mErrorMsg == null) {
                mErrorMsg = e.body.error;
            }
        } catch (DropboxIOException e) {
            // Happens all the time, probably want to retry automatically.
            mErrorMsg = "Network error.  Try again.";
        }catch (DropboxParseException e) {
            // Probably due to Dropbox server restarting, should retry
            mErrorMsg = "Dropbox error.  Try again.";
        }catch (DropboxException e){
        	 // Unknown error
            mErrorMsg = "Unknown error.  Try again.";
		}			
		
		return false;
	}

	@Override
    protected void onProgressUpdate(Long... progress) {
        int percent = (int)(100.0*(double)progress[0]/mFileLength + 0.5);
        mDialog.setProgress(percent);
    }
	
	@Override
    protected void onPostExecute(Boolean result) {
		if(result){
			mDialog.dismiss();
			Resources res = mContext.getResources();
			String msg = "Text successfully uploaded";
			//save cached file and version number
			SharedPreferences prefs = mContext.getSharedPreferences(res.getString(R.string.shared_pref), 0);       
	        Editor edit = prefs.edit();
	        edit.putString(res.getString(R.string.shared_pref_file_rev), mUpdatedFileVersion);
	        edit.commit();
	        
	        
	        String fileCachePath = mContext.getCacheDir().getAbsolutePath() + "/" + res.getString(R.string.cache_file_name);
	        OutputStream outputStream = null;
	        try {
				outputStream = new FileOutputStream(fileCachePath);
				outputStream.write(mEncryptedContent.getBytes());
				outputStream.close();
			} catch (FileNotFoundException e) {
				msg = res.getString(R.string.cache_file_name) + " not found";
			} catch (IOException e) {
				msg = "Read " + res.getString(R.string.cache_file_name) + " failed";
			} finally{
				if(outputStream != null){
					try {
						outputStream.close();
					} catch (IOException e) {
						msg = "Close " + res.getString(R.string.cache_file_name) + " failed";
					}
				}
			}
			showToast(msg);
		}
		else {
			showToast(mErrorMsg);
		}
    }
	
	private void showToast(String msg) {
        Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        error.show();
    }
}
