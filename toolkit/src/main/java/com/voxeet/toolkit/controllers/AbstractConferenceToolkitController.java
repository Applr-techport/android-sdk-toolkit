package com.voxeet.toolkit.controllers;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.voxeet.android.media.MediaStream;
import com.voxeet.android.media.audio.AudioRoute;
import com.voxeet.toolkit.R;
import com.voxeet.toolkit.implementation.VoxeetConferenceView;
import com.voxeet.toolkit.implementation.overlays.OverlayState;
import com.voxeet.toolkit.implementation.overlays.abs.AbstractVoxeetOverlayView;
import com.voxeet.toolkit.providers.containers.IVoxeetOverlayViewProvider;
import com.voxeet.toolkit.providers.logics.IVoxeetSubViewProvider;
import com.voxeet.toolkit.providers.rootview.AbstractRootViewProvider;
import com.voxeet.toolkit.utils.LoadLastSavedOverlayStateEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import voxeet.com.sdk.core.VoxeetSdk;
import voxeet.com.sdk.core.impl.ConferenceSdkService;
import voxeet.com.sdk.core.preferences.VoxeetPreferences;
import voxeet.com.sdk.core.services.AudioService;
import voxeet.com.sdk.events.error.ConferenceCreatedError;
import voxeet.com.sdk.events.error.ConferenceJoinedError;
import voxeet.com.sdk.events.error.ConferenceLeftError;
import voxeet.com.sdk.events.error.ReplayConferenceErrorEvent;
import voxeet.com.sdk.events.success.ConferenceCreatingEvent;
import voxeet.com.sdk.events.success.ConferenceCreationSuccess;
import voxeet.com.sdk.events.success.ConferenceEndedEvent;
import voxeet.com.sdk.events.success.ConferenceJoinedSuccessEvent;
import voxeet.com.sdk.events.success.ConferenceLeftSuccessEvent;
import voxeet.com.sdk.events.success.ConferencePreJoinedEvent;
import voxeet.com.sdk.events.success.ConferenceRefreshedEvent;
import voxeet.com.sdk.events.success.ConferenceUpdatedEvent;
import voxeet.com.sdk.events.success.ConferenceUserCallDeclinedEvent;
import voxeet.com.sdk.events.success.ConferenceUserJoinedEvent;
import voxeet.com.sdk.events.success.ConferenceUserLeftEvent;
import voxeet.com.sdk.events.success.ConferenceUserUpdatedEvent;
import voxeet.com.sdk.events.success.IncomingCallEvent;
import voxeet.com.sdk.events.success.InvitationReceived;
import voxeet.com.sdk.events.success.ScreenStreamAddedEvent;
import voxeet.com.sdk.events.success.ScreenStreamRemovedEvent;
import voxeet.com.sdk.events.success.UserInvitedEvent;
import voxeet.com.sdk.json.ConferenceDestroyedPush;
import voxeet.com.sdk.json.InvitationReceivedEvent;
import voxeet.com.sdk.json.RecordingStatusUpdateEvent;
import voxeet.com.sdk.json.UserInvited;
import voxeet.com.sdk.models.ConferenceUserStatus;
import voxeet.com.sdk.models.RecordingStatus;
import voxeet.com.sdk.models.impl.DefaultConferenceUser;
import voxeet.com.sdk.models.impl.DefaultInvitation;
import voxeet.com.sdk.models.impl.DefaultUserProfile;
import voxeet.com.sdk.utils.AudioType;
import voxeet.com.sdk.utils.ScreenHelper;

/**
 * Implements the common logic to any controller this SDK provides
 * <p>
 * The general idea is that it will receive relevants events and dispatch
 * them to its children
 * <p>
 * children are subviews for overlay
 * <p>
 * implementations must provide the view which will be used later
 */

public abstract class AbstractConferenceToolkitController {

    private Context mContext;
    @NonNull
    private EventBus mEventBus = EventBus.getDefault();

    /**
     * The Media streams.
     * <p>
     * Empty by default
     */
    @NonNull
    protected Map<String, MediaStream> mMediaStreams = new HashMap<>();

    /**
     * The ScreenShare Media streams.
     * <p>
     * Empty by default
     */
    @NonNull
    protected Map<String, MediaStream> mScreenShareMediaStreams = new HashMap<>();

    /**
     * The Handler.
     */
    @NonNull
    protected Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * MainView represent the view showed to the user
     */
    @Nullable
    private AbstractVoxeetOverlayView mMainView;

    private FrameLayout mMainViewParent;

    /**
     * Information about the mParams of the
     */
    @NonNull
    private FrameLayout.LayoutParams mParams;

    private IVoxeetOverlayViewProvider mVoxeetOverlayViewProvider;
    private IVoxeetSubViewProvider mVoxeetSubViewProvider;
    private OverlayState mDefaultOverlayState;
    private boolean mEnabled;
    private String TAG = VoxeetConferenceView.class.getSimpleName();
    private boolean mIsViewRetainedOnLeave;
    private AbstractRootViewProvider mRootViewProvider;
    private OverlayState mSavedOverlayState;

    private AbstractConferenceToolkitController() {

    }

    protected AbstractConferenceToolkitController(Context context, EventBus eventbus) {
        mContext = context;
        mEventBus = eventbus;

        mHandler = new Handler(Looper.getMainLooper());

        mRootViewProvider = VoxeetToolkit.getInstance().getDefaultRootViewProvider();

        setViewRetainedOnLeave(false);
        setParams();

        register();
    }

    /**
     * Init the controller
     * <p>
     * ensures the main view is valid
     */
    protected void init() {
        Activity activity = VoxeetToolkit.getInstance().getCurrentActivity();

        ConferenceSdkService service = VoxeetSdk.getInstance().getConferenceService();

        //load the maps into the view
        mMediaStreams = service.getMapOfStreams();
        mScreenShareMediaStreams = service.getMapOfScreenShareStreams();

        mMainViewParent = new FrameLayout(activity);
        mMainViewParent.setLayoutParams(createMatchParams());

        if (null == mSavedOverlayState) mSavedOverlayState = getDefaultOverlayState();

        boolean is_new_conference = false; //TODO implement conference switch

        OverlayState state = mSavedOverlayState;
        mMainView = mVoxeetOverlayViewProvider.createView(activity,
                mVoxeetSubViewProvider,
                state);

        List<DefaultConferenceUser> list = VoxeetSdk.getInstance().getConferenceService().getLastInvitationUsers();
        mergeConferenceUsers(list);

        mMainView.onMediaStreamsListUpdated(mMediaStreams);
        mMainView.onScreenShareMediaStreamUpdated(mScreenShareMediaStreams);
        mMainView.onConferenceUsersListUpdate(getConferenceUsers());

        if (null != AudioService.getSoundManager()) {
            AudioService.getSoundManager().requestAudioFocus();
        }
    }

    /**
     * Register the controller to the instance of eventbus given in constructor
     * <p>
     * If the mainview is valid, we also call the interface's method to give possible new
     */
    public void register() {
        if (!mEventBus.isRegistered(this))
            mEventBus.register(this);

        //set the relevant streams info
        if (mMainView != null) {
            mMainView.onMediaStreamsListUpdated(mMediaStreams);
            mMainView.onScreenShareMediaStreamUpdated(mScreenShareMediaStreams);
            mMainView.onConferenceUsersListUpdate(getConferenceUsers());
        }
    }

    /**
     * Unregister the controller from the EvenBus
     * <p>
     * In a typical workflow, this method is never called
     */
    public void unregister() {
        if (mEventBus.isRegistered(this)) {
            mEventBus.unregister(this);
        }
    }

    /**
     * Inject an overlay view provider
     *
     * @param provider a non-null provider
     */
    public AbstractConferenceToolkitController setVoxeetOverlayViewProvider(@NonNull IVoxeetOverlayViewProvider provider) {
        mVoxeetOverlayViewProvider = provider;

        return this;
    }

    /**
     * @param provider
     */
    public AbstractConferenceToolkitController setVoxeetSubViewProvider(@NonNull IVoxeetSubViewProvider provider) {
        mVoxeetSubViewProvider = provider;

        return this;
    }

    /**
     * Release.
     */
    public void onDestroy() {
        mMainView.onDestroy();
    }

    public boolean isOverlayEnabled() {
        return VoxeetToolkit.getInstance().isEnabled();
    }

    public void setRootViewProvider(@NonNull AbstractRootViewProvider provider) {
        mRootViewProvider = provider;
    }

    private AbstractRootViewProvider getRootViewProvider() {
        return mRootViewProvider;
    }

    /**
     * Toggles overlay visibility.
     */
    public void onOverlayEnabled(boolean enabled) {
        if (enabled)
            displayView();
        else
            removeView(false, RemoveViewType.FROM_EVENT);
    }

    private void displayView() {
        //display the view
        boolean in_conf = false;
        if (null != VoxeetSdk.getInstance()) {
            in_conf = VoxeetSdk.getInstance().getConferenceService().isInConference()
                    || VoxeetSdk.getInstance().getConferenceService().isLive();
        }


        Log.d(TAG, "displayView: " + mMainView + " " + in_conf + " " + isOverlayEnabled());

        boolean should_send_user_join = false;
        if (mMainView == null && in_conf) {
            init();
            should_send_user_join = true;
        }

        if (isOverlayEnabled() && in_conf) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    //request audio focus and set in voice call
                    if (null != VoxeetSdk.getInstance()) {
                        AudioService service = VoxeetSdk.getInstance().getAudioService();
                        service.requestAudioFocus();
                        service.setInVoiceCallSoundType();
                    }

                    log("run: add view" + mMainView);
                    if (mMainView != null) {
                        Activity activity = getRootViewProvider().getCurrentActivity();
                        ViewGroup root = getRootViewProvider().getRootView();


                        ViewGroup viewHolder = (ViewGroup) mMainViewParent.getParent();
                        if (null != viewHolder && null != root && root != viewHolder) {
                            viewHolder.removeView(mMainViewParent);

                            viewHolder = (ViewGroup) mMainView.getParent();
                            if (viewHolder != null)
                                viewHolder.removeView(mMainView);
                        }

                        if (null != root && null != activity && !activity.isFinishing()) {
                            if (null == mMainViewParent.getParent()) {
                                root.addView(mMainViewParent, createMatchParams());
                            }

                            if (null == mMainView.getParent()) {
                                mMainViewParent.addView(mMainView, mParams);
                            }

                            mMainView.requestLayout();
                            mMainViewParent.requestLayout();
                            mMainView.onResume();

                            try {
                                List<DefaultConferenceUser> users = VoxeetSdk.getInstance()
                                        .getConferenceService()
                                        .getConferenceUsers();

                                for (DefaultConferenceUser user : users) {
                                    Log.d(TAG, "run: view added user := " + user);
                                    mMainView.onConferenceUserJoined(user);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            mEventBus.post(new LoadLastSavedOverlayStateEvent());
                        }
                    }
                }
            }, 1000);
        }
    }

    public void removeView(final boolean should_release, final RemoveViewType from_type) {
        final AbstractVoxeetOverlayView view = mMainView;
        final FrameLayout viewParent = mMainViewParent;
        final boolean release = RemoveViewType.FROM_HUD.equals(from_type) || !isEnabled() || !isViewRetainedOnLeave();

        final boolean statement_release = should_release && release;

        //releasing the hold on the view
        if (statement_release) {
            mSavedOverlayState = null;

            mMainView = null;
            mMainViewParent = null;
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (view != null && release) {
                    ViewGroup viewHolder = (ViewGroup) view.getParent();
                    if (viewHolder != null)
                        viewHolder.removeView(view);

                    if (view == mMainView || statement_release) {
                        viewHolder = (ViewGroup) viewParent.getParent();
                        if (viewHolder != null)
                            viewHolder.removeView(viewParent);
                    }

                    view.onStop();

                    if (statement_release) {
                        //restore the saved state
                        mSavedOverlayState = null;

                        Log.d(TAG, "run: AbstractConferenceToolkitController should release view " + view.getClass().getSimpleName());
                        view.onDestroy();
                        //if we still have the main view displayed
                        //but wanted to clear it
                        if (view == mMainView) {
                            mMainView = null;
                            mMainViewParent = null;
                        }
                    }
                }
            }
        };

        //long after = should_release && mMainView != null ? mMainView.getCloseTimeoutInMilliseconds() : 0;

        //mHandler.postDelayed(runnable, after);
        mHandler.post(runnable);
    }

    /**
     * @param activity
     */
    public void onActivityResumed(Activity activity) {
        if (mMainView != null) {
            displayView();
        }
    }

    /**
     * When activity pause, remove the main view
     *
     * @param activity paused to
     */
    public void onActivityPaused(@NonNull Activity activity) {
        if (mMainView != null) {
            removeView(false, RemoveViewType.FROM_HUD);
        }
    }

    /**
     * Reset the state of the streams and conference users of this controller
     */
    private void reset() {
        //mMediaStreams = new HashMap<>();
        //mConferenceUsers = new ArrayList<>();
    }

    /**
     * @param overlay as the new default
     */
    public void setDefaultOverlayState(@NonNull OverlayState overlay) {
        mDefaultOverlayState = overlay;

        //set the new state to the view
        if (mMainView != null) {
            if (OverlayState.EXPANDED.equals(overlay)) {
                expand();
            } else {
                minimize();
            }
        }
    }

    /**
     * Access the overlay state showed by this controller
     *
     * @return the default state, expanded or minimized
     */
    public OverlayState getDefaultOverlayState() {
        return mDefaultOverlayState;
    }

    private void minimize() {
        if (null != mMainView) mMainView.minimize();
        mSavedOverlayState = OverlayState.MINIMIZED;
    }

    private void expand() {
        if (null != mMainView) mMainView.expand();
        mSavedOverlayState = OverlayState.EXPANDED;
    }

    /**
     * Change the state of this controller
     *
     * @param state the new state of the controller
     */
    public void enable(boolean state) {
        mEnabled = state;

        //enable or disable depending
        if (mEnabled) register();
        else unregister();
    }

    /**
     * TODO check for retain state switch : quit in correct cases
     *
     * @param state the new state of the view
     */
    public void setViewRetainedOnLeave(boolean state) {
        mIsViewRetainedOnLeave = state;
    }

    /**
     * Check wether the view should still be up and running on quit conference
     */
    public boolean isViewRetainedOnLeave() {
        return mIsViewRetainedOnLeave;
    }


    /**
     * Check wether this controller can be called
     *
     * @return the activation state of this controller
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Getter of the main view
     *
     * @return the instance of the main view
     */
    @Nullable
    protected AbstractVoxeetOverlayView getMainView() {
        return mMainView;
    }


    protected Context getContext() {
        return mContext;
    }

    /**
     * Method set to filter specific conference from the given id
     *
     * @param conference the conference id to test against
     * @return return true if the given conference can be managed
     */
    protected abstract boolean validFilter(String conference);


    private void setParams() {
        mParams = new FrameLayout.LayoutParams(
                getContext().getResources().getDimensionPixelSize(R.dimen.dimen_100),
                getContext().getResources().getDimensionPixelSize(R.dimen.dimen_140));
        mParams.gravity = Gravity.END | Gravity.TOP;
        mParams.topMargin = ScreenHelper.actionBar(getContext()) + ScreenHelper.getStatusBarHeight(getContext());
    }


    private FrameLayout.LayoutParams createMatchParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        return params;
    }

    /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * Event Management - see EventBus field
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

    /**
     * Display the conference view when the user is creating a conference
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull ConferenceCreatingEvent event) {
        //TODO check for call ?
        //VoxeetSdk.getInstance().getAudioService().playSoundType(AudioType.RING);
        Activity activity = VoxeetToolkit.getInstance().getCurrentActivity();

        log("onEvent: " + event.getClass().getSimpleName()
                + " " + activity);
        if (activity != null) {
            if (mMainView == null) init();

            setParams();

            displayView();

            if (mMainView != null) {
                mMainView.onConferenceCreating();
            }
        }
    }

    /**
     * Display the conference view when the user is creating/joining a conference.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull ConferencePreJoinedEvent event) {
        VoxeetSdk.getInstance().getAudioService().playSoundType(AudioType.RING);
        Activity activity = VoxeetToolkit.getInstance().getCurrentActivity();

        log("onEvent: " + event.getClass().getSimpleName()
                + " " + validFilter(event.getConferenceId())
                + " " + activity);
        if (activity != null && validFilter(event.getConferenceId())) {
            if (mMainView == null) init();

            setParams();

            displayView();

            if (mMainView != null) {
                mMainView.onConferenceJoining(event.getConferenceId());
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserInvitedEvent event) {
        UserInvited invited = event.getEvent();
        List<DefaultUserProfile> profiles = invited.getParticipants();

        List<DefaultConferenceUser> users = getConferenceUsers();
        for (DefaultUserProfile profile : profiles) {
            DefaultConferenceUser user = new DefaultConferenceUser(profile);
            if (!users.contains(user)) {
                users.add(user);

                if (mMainView != null) {
                    mMainView.onConferenceUserUpdated(user);
                }
            }
        }

        if (mMainView != null) {
            mMainView.onConferenceUsersListUpdate(users);
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final InvitationReceived invitation) {
        InvitationReceivedEvent event = invitation.getEvent();
        if (null != event && null != event.getInvitations()) {
            List<DefaultConferenceUser> users = getConferenceUsers();
            for (DefaultInvitation invite : event.getInvitations()) {
                DefaultConferenceUser temp = new DefaultConferenceUser(invite.getProfile());

                if (mMainView != null) {
                    mMainView.onConferenceUserUpdated(temp);
                }

                if (!users.contains(temp)) {
                    users.add(temp);
                }
            }

            if (mMainView != null) {
                mMainView.onConferenceUsersListUpdate(users);
            }
        }
    }

    /**
     * On ConferenceJoinedSuccessEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull ConferenceJoinedSuccessEvent event) {
        if (validFilter(event.getConferenceId()) || validFilter(event.getAliasId())) {
            VoxeetSdk.getInstance().getConferenceService()
                    .setAudioRoute(AudioRoute.ROUTE_SPEAKER);

            displayView();

            List<DefaultConferenceUser> users = VoxeetSdk.getInstance().getConferenceService().getConferenceUsers();
            log("onEvent: ConferenceJoinedSuccessEvent");
            if (mMainView != null) {
                mMainView.onConferenceUsersListUpdate(users);
            }

            if (mMainView != null) {
                mMainView.onConferenceJoined(event.getConferenceId());
            }
        }
    }

    /**
     * On ConferenceCreationSuccess event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull ConferenceCreationSuccess event) {
        VoxeetSdk.getInstance().getAudioService().playSoundType(AudioType.RING);
        if (mMainView == null) init();

        if (validFilter(event.getConfId()) || validFilter(event.getConfAlias())) {
            mMainView.onConferenceCreation(event.getConfId());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceRefreshedEvent event) {
        DefaultConferenceUser user = event.getUser();
        /*if (user == null) {
            UserInfo profile = VoxeetToolkit.getInstance().getConferenceToolkit()
                    .getInvitedUserFromCache(event.getUserId());

            if (profile != null) {
                user = new DefaultConferenceUser(event.getUserId(), null, profile);
            }
        }*/

        List<DefaultConferenceUser> users = getConferenceUsers();
        if (null != user) {
            if (!users.contains(user)) {
                users.add(user);
            }

            if (mMainView != null) {
                mMainView.onConferenceUserUpdated(user);
            }
        }
        if (mMainView != null) {
            mMainView.onConferenceUsersListUpdate(users);
        }
    }

    /**
     * On ConferenceUserUpdatedEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull ConferenceUserUpdatedEvent event) {
        log("onEvent: ConferenceUserUpdatedEvent " + event.getUser());
        DefaultConferenceUser user = event.getUser();

        List<DefaultConferenceUser> users = getConferenceUsers();
        if (!users.contains(user)) {
            checkStopOutgoingCall();
            users.add(user);
            if (mMainView != null) {
                mMainView.onConferenceUsersListUpdate(users);
            }
        }

        //mMediaStreams.put(user.getUserId(), event.getMediaStream());

        if (mMainView != null) {
            if (!event.isScreenShare()) {
                mMainView.onScreenShareMediaStreamUpdated(user.getUserId(), mScreenShareMediaStreams);
            } else {
                mMainView.onMediaStreamUpdated(user.getUserId(), mMediaStreams);
            }

            mMainView.onConferenceUserUpdated(user);
        }
    }

    /**
     * On ConferenceUserJoinedEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final ConferenceUserJoinedEvent event) {
        checkStopOutgoingCall();

        log("onEvent: ConferenceUserJoinedEvent " + event);
        DefaultConferenceUser user = event.getUser();


        List<DefaultConferenceUser> users = getConferenceUsers();
        if (!users.contains(user)) {
            users.add(user);
        }

        if (mMainView != null) {
            mMainView.onConferenceUsersListUpdate(users);
        }

        //mMediaStreams.put(user.getUserId(), event.getMediaStream());

        if (null != mMainView) {
            mMainView.onMediaStreamUpdated(user.getUserId(), mMediaStreams);

            mMainView.onConferenceUserJoined(user);
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ScreenStreamAddedEvent event) {
        MediaStream mediaStream = event.getMediaStream();
        Log.d(TAG, "onEvent: event " + mediaStream.isScreenShare() + " "
                + (mediaStream.videoTracks().size() > 0));
        //mScreenShareMediaStreams.put(event.getPeer(), event.getMediaStream());


        if (null != mMainView) {
            mMainView.onScreenShareMediaStreamUpdated(event.getPeer(),
                    VoxeetSdk.getInstance().getConferenceService().getMapOfScreenShareStreams());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ScreenStreamRemovedEvent event) {
        String peer = event.getPeer();
        //if (mScreenShareMediaStreams.containsKey(peer)) {
        //    mScreenShareMediaStreams.remove(peer);
        //}

        if (null != mMainView) {
            mMainView.onScreenShareMediaStreamUpdated(peer,
                    VoxeetSdk.getInstance().getConferenceService().getMapOfScreenShareStreams());
        }
    }

    /**
     * On ConferenceUserLeftEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final ConferenceUserLeftEvent event) {
        if (null != mMainView) {
            DefaultConferenceUser user = event.getUser();
            List<DefaultConferenceUser> users = getConferenceUsers();

            if (users.contains(user)) {
                users.remove(user);
                mMainView.onConferenceUsersListUpdate(users);
            }

            mMainView.onConferenceUserLeft(user);
        }
    }

    /**
     * On User Declined call event
     * <p>
     * Logic is quite different from the onEvent(ConferenceUserLeftEvent)
     * since we do not have a direct object but the user's
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final ConferenceUserCallDeclinedEvent event) {
        if (null != mMainView) {
            int i = 0;
            DefaultConferenceUser user = null;
            List<DefaultConferenceUser> users = getConferenceUsers();
            while (i < users.size()) {
                user = users.get(i);
                if (user.getUserId() != null && user.getUserId().equals(event.getUserId())) {
                    users.remove(i);
                    mMainView.onConferenceUsersListUpdate(users);
                } else {
                    i++;
                }
            }
            mMainView.onConferenceUserDeclined(event.getUserId());
        }
    }

    /**
     * On ConferenceLeftSuccessEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceLeftSuccessEvent event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        VoxeetSdk.getInstance().getAudioService().stop();

        if (null != mMainView) {
            reset();
            mMainView.onConferenceLeft();

            removeView(true, RemoveViewType.FROM_EVENT);
        }
    }

    /**
     * On ConferenceLeftSuccessEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceLeftError event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        VoxeetSdk.getInstance().getAudioService().stop();

        if (null != mMainView) {
            reset();
            mMainView.onConferenceLeft();

            removeView(true, RemoveViewType.FROM_EVENT);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceCreatedError event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        VoxeetSdk.getInstance().getAudioService().stop();

        if (null != mMainView) {
            reset();
            mMainView.onConferenceDestroyed();

            removeView(true, RemoveViewType.FROM_EVENT);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceJoinedError event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        VoxeetSdk.getInstance().getAudioService().stop();

        if (null != mMainView) {
            reset();
            mMainView.onConferenceDestroyed();

            removeView(true, RemoveViewType.FROM_EVENT);
        }
    }

    /**
     * On event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceDestroyedPush event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        if (null != VoxeetSdk.getInstance()) {
            VoxeetSdk.getInstance().getAudioService().stop();
        }

        reset();
        if (null != mMainView) {
            mMainView.onConferenceDestroyed();
        }

        removeView(true, RemoveViewType.FROM_EVENT);
    }

    /**
     * On ConferenceEndedEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConferenceEndedEvent event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        VoxeetSdk.getInstance().getAudioService().stop();

        reset();
        if (null != mMainView) {
            mMainView.onConferenceDestroyed();
        }

        removeView(true, RemoveViewType.FROM_EVENT);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ReplayConferenceErrorEvent event) {
        Log.d("SoundPool", "onEvent: " + event.getClass().getSimpleName());
        VoxeetSdk.getInstance().getAudioService().stop();

        reset();
        //TODO error message
        if (null != mMainView) {
            mMainView.onConferenceDestroyed();
        }

        removeView(true, RemoveViewType.FROM_EVENT);
    }

    /**
     * On RecordingStatusUpdateEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull RecordingStatusUpdateEvent event) {
        if (null != mMainView) {
            mMainView.onRecordingStatusUpdated(RecordingStatus.RECORDING.name().equalsIgnoreCase(event.getRecordingStatus()));
        }
    }

    /**
     * On ConferenceUpdatedEvent event.
     *
     * @param event the event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull ConferenceUpdatedEvent event) {
        if (null != mMainView) {
            mMainView.onConferenceUpdated(event.getEvent().getParticipants());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull IncomingCallEvent event) {
        if (null != mMainView) {
            mMainView.minimize();
            mSavedOverlayState = OverlayState.MINIMIZED;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@NonNull LoadLastSavedOverlayStateEvent event) {
        if (null != mMainView) {
            OverlayState state = mSavedOverlayState;
            if (null == state) state = getDefaultOverlayState();

            if (OverlayState.EXPANDED.equals(state)) {
                expand();
            } else {
                minimize();
            }
        }
    }

    private void log(@NonNull String value) {
        Log.d(TAG, value);
    }

    private void mergeConferenceUsers(@NonNull List<DefaultConferenceUser> users) {
        List<DefaultConferenceUser> current_users = getConferenceUsers();
        if (users != current_users) {
            for (DefaultConferenceUser user : users) {
                if (null != user && !current_users.contains(user)) {
                    log("init: adding " + user + " " + user.getUserInfo());
                    current_users.add(user);
                }
            }
        }
    }

    private void checkStopOutgoingCall() {
        boolean found = false;

        List<DefaultConferenceUser> users = VoxeetSdk.getInstance()
                .getConferenceService().getConferenceUsers();
        for (DefaultConferenceUser user : users) {
            if (null != user.getUserId() && !user.getUserId().equals(VoxeetPreferences.id())
                    && ConferenceUserStatus.ON_AIR.equals(user.getConferenceStatus())) {
                found = true;
            }
        }

        if (found) {
            Log.d("SoundPool", " checkOutgoingCall");
            VoxeetSdk.getInstance().getAudioService().stop();
        }
    }


    private List<DefaultConferenceUser> getConferenceUsers() {
        if (null != VoxeetSdk.getInstance()) {
            return VoxeetSdk.getInstance().getConferenceService().getConferenceUsers();
        }
        return new ArrayList<>();
    }
}
