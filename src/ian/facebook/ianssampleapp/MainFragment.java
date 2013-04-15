package ian.facebook.ianssampleapp;


import ian.facebook.ianssampleapp.R.color;

import java.util.Arrays;
import java.util.List;

import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookRequestError;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphObject;
import com.facebook.widget.UserSettingsFragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class MainFragment extends Fragment {
	
	private static final List<String> PERMISSIONS = Arrays.asList("publish_actions");
	private static final String TAG = "MainFragment";
	private UiLifecycleHelper uiHelper;
	private Button postPhotoButton;
	private PendingAction pendingAction = PendingAction.NONE;
	private UserSettingsFragment userSettingsFragment;
	private boolean isFragmentDestroy;
	
	private enum PendingAction {
		NONE,
		POST_PHOTO,
	}
	
	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state, Exception exception) {
			onSessionStateChange(session, state, exception);
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		isFragmentDestroy = false;
		uiHelper = new UiLifecycleHelper(this.getActivity(), callback);
		uiHelper.onCreate(savedInstanceState);
	}
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, 
			ViewGroup container, 
			Bundle savedInstanceState) {
		
		View view = inflater.inflate(R.layout.login_fragment_activity, container, false);
		
		FragmentManager fragmentManager = this.getActivity().getSupportFragmentManager();
		userSettingsFragment = (UserSettingsFragment) fragmentManager.findFragmentById(R.id.login_fragment);

		postPhotoButton = (Button) view.findViewById(R.id.button1);
		postPhotoButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				onClickPostPhoto();
				postPhotoButton.setEnabled(false);
			}
		});
		updateUI();

		return view;
	}

	
	private void onSessionStateChange(Session session, SessionState state, Exception exception) {		
		
		if (pendingAction != PendingAction.NONE &&
				(exception instanceof FacebookOperationCanceledException ||
				exception instanceof FacebookAuthorizationException)) {
				new AlertDialog.Builder(this.getActivity())
					.setTitle(R.string.cancelled)
					.setMessage(R.string.permission_not_granted)
					.setPositiveButton(R.string.ok, null)
					.show();
			pendingAction = PendingAction.NONE;
		} else if (state == SessionState.OPENED_TOKEN_UPDATED) {
			handlePendingAction();
		}
		updateUI();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		Session session = Session.getActiveSession();
		if (session != null &&
			   (session.isOpened() || session.isClosed()) ) {
			onSessionStateChange(session, session.getState(), null);
		}
		uiHelper.onResume();
		
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		uiHelper.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onPause() {
		super.onPause();
		uiHelper.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		uiHelper.onDestroy();
		isFragmentDestroy = true;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		uiHelper.onSaveInstanceState(outState);
	}
	
	private void onClickPostPhoto() {
		performPublish(PendingAction.POST_PHOTO);
	}
	
	private void postPhoto() {
		if (hasPublishPermission()) {
			//  https://developers.facebook.com/docs/reference/android/3.0/Request
			/*  newUploadPhotoRequest(Session session, File file, Request.Callback callback) throws FileNotFoundException
				Creates a new Request configured to upload a photo to the user's default photo album.
			 *  you can replace the arguments of newUploadPhotoRequest method to send the specific image file
			*/
			Bitmap image = BitmapFactory.decodeResource(this.getResources(), R.drawable.j20);
			Request request = Request.newUploadPhotoRequest(Session.getActiveSession(), image, new Request.Callback() {
				@Override
				public void onCompleted(Response response) {
					if(isFragmentDestroy == false){
						showPublishResult(getString(R.string.photo_name), response.getGraphObject(), response.getError());
						postPhotoButton.setEnabled(true);
					}
				}
			});
			request.executeAsync();
		} else {
		   pendingAction = PendingAction.POST_PHOTO;
		}
	}
	
	private interface GraphObjectWithId extends GraphObject {
		String getId();
	}
	   
	private void showPublishResult(String message, GraphObject result, FacebookRequestError error) {
		String title = null;
		String alertMessage = null;
		if (error == null) {
			title = getString(R.string.success);
			String id = result.cast(GraphObjectWithId.class).getId();
			alertMessage = getString(R.string.successfully_posted_post, message, id);
		} else {
			title = getString(R.string.error);
			alertMessage = error.getErrorMessage();
		}

		new AlertDialog.Builder(this.getActivity())
				.setTitle(title)
				.setMessage(alertMessage)
				.setPositiveButton(R.string.ok, null)
				.show();
	}   
	
	private void performPublish(PendingAction action) {
		Session session = Session.getActiveSession();
		if (session != null) {
			pendingAction = action;
			if (hasPublishPermission()) {
				// We can do the action right away.
				handlePendingAction();
			} else {
				// We need to get new permissions, then complete the action when we get called back.
				session.requestNewPublishPermissions(new Session.NewPermissionsRequest(this, PERMISSIONS));
			}
		}
	}
	
	private boolean hasPublishPermission() {
		Session session = Session.getActiveSession();
		return session != null && session.getPermissions().contains("publish_actions");
	}
	
	private void handlePendingAction() {
		PendingAction previouslyPendingAction = pendingAction;
		// These actions may re-set pendingAction if they are still pending, but we assume they
		// will succeed.
		pendingAction = PendingAction.NONE;

		switch (previouslyPendingAction) {
			case POST_PHOTO:
				postPhoto();
				break;
		}
	}
	
	private void updateUI() {
		Session session = Session.getActiveSession();
		boolean enableButtons = (session != null && session.isOpened());
		
		postPhotoButton.setEnabled(enableButtons);
		if(enableButtons == false){
			postPhotoButton.setText(R.string.loginfirst);
		}else{
			postPhotoButton.setText(R.string.post_photo);
			postPhotoButton.setBackgroundColor(color.color_for_send);
		}

	}


}
