package com.saapp.encryptme;

import java.util.List;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.TokenPair;
import com.dropbox.client2.session.Session.AccessType;
import com.saapp.encryptme.R;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.net.NetworkInfo;

public class EncryptMe extends Activity {
    // If you'd like to change the access type to the full Dropbox instead of
    // an app folder, change this value.
    final static private AccessType ACCESS_TYPE = AccessType.APP_FOLDER;

    static final int DIALOG_ENTER_PWD_ENCRYPT_ID = 0;
    static final int DIALOG_ENTER_PWD_DECRYPT_ID = 1;
    
    //key for the transient attributes in the saving state bundle
    static final private String ENCRYPTED_TEXT = "ENCRYPTED_TEXT";
    static final private String IS_ENCRYPTED = "IS_ENCRYPTED";
    static final private String CURRENT_PWD = "CURRENT_PWD";
    static final private String MANNUALLY_SET_DB_CONNECTION = "MANNUALLY_SET_DB_CONNECTION";
    
    private DropboxAPI<AndroidAuthSession> mApi;
    private boolean mLinkedToDropBox;
    
    //Android widgets
    private EditText mEditNote;
    private LinearLayout mSearchDisplay;
    private EditText mSearchText;

    //search related variables, consider to add them in savedInstanceState
    private String mSearchKey;
    private int mSearchCursorPosition;
    private String mLastSearchDocumentContent;
    
    //the following transient variable needs to be saved in the savedInstanceState
    private String mEncryptedText;
    private boolean mIsEncrypted;
    private String mCurrentPwd;
    private boolean mManuallySetDropboxConnection;
    
    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
            
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {
        	
            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
        
    }

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.app_main_layout);
    	mEditNote = (EditText) findViewById(R.id.note_content);
    	mSearchDisplay = (LinearLayout)findViewById(R.id.search_layout);
    	mSearchText = (EditText) findViewById(R.id.search_keyword);
    	mSearchDisplay.setVisibility(View.GONE);    	
    	
    	// We create a new AuthSession so that we can use the Dropbox API.
    	AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<AndroidAuthSession>(session);
        
        mLinkedToDropBox = session.isLinked(); 
        
    	if(savedInstanceState != null){
    		mEncryptedText = savedInstanceState.getString(ENCRYPTED_TEXT);
    		mIsEncrypted = savedInstanceState.getBoolean(IS_ENCRYPTED);
    		mCurrentPwd = savedInstanceState.getString(CURRENT_PWD);
    		mManuallySetDropboxConnection = savedInstanceState.getBoolean(MANNUALLY_SET_DB_CONNECTION);
    	}
        
    	if(!mLinkedToDropBox && (!mManuallySetDropboxConnection)){
    		mApi.getSession().startAuthentication(EncryptMe.this);
    	}
    	else{
    		if(savedInstanceState == null){
    			dowloadNote();
    		}
    	}
    	
    	getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
    }
    
    private void dowloadNote(){
    	DownloadAccountFile download = new DownloadAccountFile(EncryptMe.this, mApi, mEditNote);
		download.execute();
    }
    
    public void setEncryptedText(String encryptedText){
    	mEncryptedText = encryptedText;
    }
    
    public void setIsEncrypted(boolean isEncrypted){
    	mIsEncrypted = isEncrypted;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
		if(!mLinkedToDropBox && authenticateDropboxSession() && (!mManuallySetDropboxConnection)){			
	        dowloadNote();
		}
        
    }
    
    private boolean authenticateDropboxSession(){
    	boolean authenticationSuccess = false;
    	AndroidAuthSession session = mApi.getSession();

        // The next part must be inserted in the onResume() method of the
        // activity from which session.startAuthentication() was called, so
        // that Dropbox authentication completes properly.
        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();

                // Store it locally in our app for later use
                TokenPair tokens = session.getAccessTokenPair();
                storeKeys(tokens.key, tokens.secret);
                mLinkedToDropBox=true;
                authenticationSuccess = true;
            } catch (IllegalStateException e) {
                showToast("Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
            }
        }
        return authenticationSuccess;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_restore:
                restoreFileToLastVersion();
                return true;
            case R.id.link_dropbox:
            	relinkToDropbox();
            	return true;
            case R.id.unlink_dropbox:
            	unlinkFromDropbox();
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    private void restoreFileToLastVersion(){
    	if(isOnline()){
    		Resources res = getResources();
        	String dropboxmFileName = "/" + res.getString(R.string.dropbox_file_name);
    		try {
				List<Entry> revisions = mApi.revisions(dropboxmFileName, 2);
				if(revisions.size()>1 && !revisions.get(1).isDeleted){
					Entry previousVersion = revisions.get(1);
					mApi.restore(dropboxmFileName, previousVersion.rev);
					dowloadNote();
				}
				else{
					mEncryptedText=null;
					mEditNote.setText(null);
					mIsEncrypted=false;
					showToast("Previous version is not available, so revert to empty file.");
				}
			} catch (DropboxException e) {
				showToast("Dropbox error");
			}
    	}else{
    		showToast("Device is off line. Cannot restore to the previous version.");
    	}
    }
    
    private void relinkToDropbox(){
    	clearKeys();
    	mLinkedToDropBox = false;
    	mManuallySetDropboxConnection = true;
    	mApi.getSession().startAuthentication(EncryptMe.this);
    }
    
    private void unlinkFromDropbox() {
        // Remove credentials from the session
        mApi.getSession().unlink();

        // Clear our stored keys
        clearKeys();
        // Change UI state to display logged out version
        mLinkedToDropBox = false;
        mManuallySetDropboxConnection = true;
    }
    
    private void clearKeys() {
    	Resources res = getResources();
        SharedPreferences prefs = getSharedPreferences(res.getString(R.string.shared_pref), 0);
        Editor edit = prefs.edit();        
        edit.putString(res.getString(R.string.shared_pref_dropbox_access_key), null);
        edit.putString(res.getString(R.string.shared_pref_dropbox_access_secrect), null);
        edit.commit();
    }
    
    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeKeys(String key, String secret) {
        // Save the access key for later
    	Resources res = getResources();
        SharedPreferences prefs = getSharedPreferences(res.getString(R.string.shared_pref), 0);
        Editor edit = prefs.edit();
        edit.putString(res.getString(R.string.shared_pref_dropbox_access_key), key);
        edit.putString(res.getString(R.string.shared_pref_dropbox_access_secrect), secret);
        edit.commit();
    }
    
    
    @Override
    public boolean onKeyDown(int keycode, KeyEvent e){
    	switch(keycode){
    		case KeyEvent.KEYCODE_SEARCH:
    			mSearchDisplay.setVisibility(View.VISIBLE);
    			System.out.println("press search button ...");
    	}
    	
    	return super.onKeyDown(keycode, e);
    }
    
    public void onClickSearchForward(View v){
    	String newSearchKey = mSearchText.getText().toString();
    	Editable text = mEditNote.getText();		
    	String newDocumentContent = text.toString();
    	
    	//start a new search if the search key changes, or the document text changes
    	if(mSearchKey == null || !mSearchKey.equals(newSearchKey) || mLastSearchDocumentContent == null || !mLastSearchDocumentContent.equals(newDocumentContent)){
    		mSearchKey = newSearchKey;
    		mLastSearchDocumentContent = newDocumentContent;
    		mSearchCursorPosition = -1;
    	}

    	int keyLength = mSearchKey.length(); 
		int selectStart = mLastSearchDocumentContent.indexOf(mSearchKey, mSearchCursorPosition + 1);
		if(selectStart >= 0){
			mEditNote.requestFocus();
			Selection.setSelection(text, selectStart);			
    		mEditNote.setSelection(selectStart, selectStart + keyLength);
    		
    		//handle the case where the search key is the last occurrence 
    		if(mLastSearchDocumentContent.indexOf(mSearchKey, selectStart+1)<0){
    			mSearchCursorPosition = selectStart + 1;
    		}
    		else{
    			mSearchCursorPosition = selectStart;        		
    		}    		
		}
		//not found
		else{
			showToast("Text not found");
		}
    }
    
    public void onClickSearchBackward(View v){
    	String newSearchKey = mSearchText.getText().toString();
    	Editable text = mEditNote.getText();		
    	String newDocumentContent = text.toString();
    	
    	//start a new search if the search key changes, or the document text changes
    	if(mSearchKey == null || !mSearchKey.equals(newSearchKey) || mLastSearchDocumentContent == null || !mLastSearchDocumentContent.equals(newDocumentContent)){
    		mSearchKey = newSearchKey;
    		mLastSearchDocumentContent = newDocumentContent;
    		mSearchCursorPosition = -1;
    	}
    	
    	int selectStart = mLastSearchDocumentContent.lastIndexOf(mSearchKey, mSearchCursorPosition - 1);
    	if(selectStart >= 0){
    		mEditNote.requestFocus();
    		Selection.setSelection(text, selectStart);    		
    		mEditNote.setSelection(selectStart, selectStart + mSearchKey.length());
    		
    		//handle the case where the search key is the first occurrence 
    		if((mLastSearchDocumentContent.lastIndexOf(mSearchKey, selectStart-1))<0){
    			mSearchCursorPosition = selectStart - 1;
    		}
    		else{
    			mSearchCursorPosition = selectStart;
    		}
    		
    	}
    	//not found
		else{
			showToast("Text not found");
		}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState){    	
    	outState.putString(ENCRYPTED_TEXT, mEncryptedText);
    	outState.putBoolean(IS_ENCRYPTED, mIsEncrypted);
    	outState.putString(CURRENT_PWD, mCurrentPwd);
    	outState.putBoolean(MANNUALLY_SET_DB_CONNECTION, mManuallySetDropboxConnection);
    	super.onSaveInstanceState(outState);
    }


    private AndroidAuthSession buildSession() {
    	Resources res = getResources();
        AppKeyPair appKeyPair = new AppKeyPair(res.getString(R.string.dropbox_app_key), res.getString(R.string.dropbox_app_secrect));
        AndroidAuthSession session;

        String[] stored = getKeys();
        if (stored != null) {
            AccessTokenPair accessToken = new AccessTokenPair(stored[0], stored[1]);
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE, accessToken);
        } else {
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
        }

        return session;
    }
    
    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *
     * @return Array of [access_key, access_secret], or null if none stored
     */
    private String[] getKeys() {
    	Resources res = getResources();
        SharedPreferences prefs = getSharedPreferences(res.getString(R.string.shared_pref), 0);
        String key = prefs.getString(res.getString(R.string.shared_pref_dropbox_access_key), null);
        String secret = prefs.getString(res.getString(R.string.shared_pref_dropbox_access_secrect), null);
        if (key != null && secret != null) {
        	String[] ret = new String[2];
        	ret[0] = key;
        	ret[1] = secret;
        	return ret;
        } else {
        	return null;
        }
    }
    
    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
  
    
    //the device is using android sdk version 10, so cannot use the recommand implementation: DialogFragment, which comes in version 11
	@SuppressWarnings("deprecation")
	public void onClickSave(View v){
		if(mIsEncrypted){
			//directly upload the encrypted text
			uploadText();
		}
		else{
			showDialog(DIALOG_ENTER_PWD_ENCRYPT_ID);
		}		
    }
	
	@SuppressWarnings("deprecation")
	public void onClickDecrypt(View v){
		if(mIsEncrypted){
			showDialog(DIALOG_ENTER_PWD_DECRYPT_ID);
		}
		else{
			showToast("The text is already decrypted.");
		}
	}
	
	private void invokeWarningDialog(EditText pwd){
		final EditText enteredPwd = pwd;
		final Activity thisActivity = this;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("The password is different from the current one. Are you sure you want to encrypt with the new password?")
			   .setPositiveButton("Yes", 
					   new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							encryptAndUploadText(enteredPwd.getText().toString());
						}
					})
			   .setNegativeButton("No", 
					   new DialogInterface.OnClickListener() {
						
						@SuppressWarnings("deprecation")
						@Override
						public void onClick(DialogInterface dialog, int which) {
							enteredPwd.setText(null);
							thisActivity.showDialog(DIALOG_ENTER_PWD_ENCRYPT_ID);
						}
					});
		builder.create().show();
	}
    
    @Override
    protected Dialog onCreateDialog(int id){
    	Dialog dialog = null;
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    	View layout = inflater.inflate(R.layout.enter_password_dialog, null);
    	final EditText pwdText = (EditText) layout.findViewById(R.id.password);
    	
    	switch(id){
    	case DIALOG_ENTER_PWD_ENCRYPT_ID:    		    		
        	builder.setView(layout)
        	       .setTitle("Enter password:")
              	   .setPositiveButton("OK",  
        				   new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            	String enteredPwd = pwdText.getText().toString();
                            	if(mCurrentPwd != null &&  !mCurrentPwd.equals(enteredPwd)){
                        			invokeWarningDialog(pwdText);
                        		}
                        		else{
                        			encryptAndUploadText(enteredPwd);
                        		}
                            }
                        } )
                   .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.cancel();
                            }
                        }
                    );
        	dialog = builder.create();
        	break;
    	case DIALOG_ENTER_PWD_DECRYPT_ID:
    		builder.setView(layout)
 	       .setTitle("Enter password:")
       	   .setPositiveButton("OK",  
 				   new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface dialog, int whichButton) {
                         decryptText(pwdText.getText().toString());
                     }
                 } )
            .setNegativeButton("Cancel",
                 new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface dialog, int whichButton) {
                         dialog.cancel();
                     }
                 }
             );
 	dialog = builder.create();
 	break;
    	default: dialog = null;
    	}
    	
    	return dialog;
    }
    
    
    private void encryptAndUploadText(String pwd){
		try {
			mEncryptedText = AES.encrypt(mEditNote.getText().toString(), pwd);
			mEditNote.setText(mEncryptedText);
			mIsEncrypted = true;
			uploadText();
		} catch (Exception e) {
			showToast("Encryption error" + e.getLocalizedMessage());
		}
    	
    }
    private void uploadText(){
    	if(isOnline()){
    		UploadAccountFile upload = new UploadAccountFile(EncryptMe.this, mApi, mEncryptedText);
        	upload.execute();
    	}
    	else{
    		showToast("Device is not online. Please try again later.");
    	}
    }
    
    private void decryptText(String pwd){
    	try {
			String plainText = AES.decrypt(mEncryptedText, pwd);
			mEditNote.setText(plainText);
			mIsEncrypted = false;
			mCurrentPwd = pwd;
		} catch (Exception e) {
			showToast("Incorrect password. Please try again. The worst case, you can use menu -> Restore to restore to the previous version.");
		}
    	
    }
    
    public boolean isOnline() {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }
    
    


}
