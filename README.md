# Voxeet Android SDK

The SDK is a Java library allowing users to:

  - Create demo/normal conferences
  - Join conferences
  - Change sounds angle and direction for each conference user
  - Broadcast messages to other participants
  - Mute users/conferences
  - Enable/disable camera
  - Screen share
  - Record conferences
  - Replay recorded conferences
  - If you use External login like O365, LDAP, or custom login to retrieve contact details, it is now possible to also add your contact ID with the display name and the photo url avatar.
   This allows you to ask guest users to introduce themselves and provide their display name and for your authenticated users in your enterprise or for your clients the ID that can be retrieved from O365 (name, department, etc).

## Installing the Android SDK using Gradle

To install the SDK directly into your Android project using the Grade build system and an IDE like Android Studio, add the following entry to your build.gradle file as shown below:

```gradle
dependencies {
  compile ('com.voxeet.sdk:toolkit:1.4.7') {
    transitive = true
  }
}
```

The current logic-only (no UI) sdk is available as well: [public-sdk](https://github.com/voxeet/android-sdk)

## Migrating from 0.X to 1.X

 - Most calls to the SDK are now using Promises to resolve and manage error
 - it is mandatory to use the following workflow on pre-used methods :
```
SDK.method.call()
.then(<PromiseExec>)
.error(<ErrorPromise>);
```

A complete documentation about the Promise implementation is available on this [Github](https://github.com/codlab/android_promise)

### What's New ?
v1.4 :
  - improve headsets management (internal)
  - fix issues with non firebase projects
  - fix VideoView UI behaviour
  - capability to recover from egl internal crashes

v1.3 :
  - fix various network connectivity
  - improve socket management
  - fix issue in oauth management
  - fix screen recording calls

v1.1.8.32 :
  - add localstats with support of auto management and report event

v1.1.8.28/31 :
  - fix default toolkit behaviour
  - add local stats management

v1.1.8.27 :
  - fix incoming calls issues
  - improve integration
  - lighter sample application to work with

v1.1.8.26 :
  - fix crash on peer vu meter

v1.1.8.24 : 
  - add sound in incoming calls

v1.1.8.23 : 
  - improves the overall SDK experience
  - fix workflows
  - improve video management
  - sync experience with the iOS implementation

v1.1.7.1.1 :
  - fix a crash when SDK is not initialized and using `VoxeetAppCompatActivity`

v1.1.7.1 :
  - fix overlay behaviour

v1.1.6 :
  - new Media management

v1.1.5 :
  - from previous vversion, Media.AudioRoute is now AudioRoute
  - Audio related APIs are now in `VoxeetSdk.getInstance().getAudioService()`
  - fix issues with ids from the SDK
  - add VideoPresentation api
  - sample app : integration of the api and fix with butterknife

v1.1.0 :
  - various fixes (issue with speaker button)
  - add screenshare capabilities
  - easier integration
  - dual initialization mode (keys or oauth)

v1.0.4 :
  - upgrade api calls
  - fix issue with answers
  - fix conference alias in history

v1.0.3 :
  - initialize Promises during the Voxeet initialization

v1.0.2 :
  - fix CTA
  - fix issue with crash on same calls
  - fix controllers behaviour

v1.0 :
  - complete rework of most internal method
  - File Presentation management (start, stop, update)
  - event on QualityIndicators with MOS


## Usage

### Recommended settings for API compatibility:

```gradle
apply plugin: 'com.android.application'

android {
    compileSdkVersion 26+
    defaultConfig {
        minSdkVersion 16
        targetSdkVersion 26+
    }
}
```

### Consumer Key & Secret

Add your keys into your ~/.gradle/gradle.properties file

```gradle
PROD_CONSUMER_KEY=your_key_prod
PROD_CONSUMER_SECRET=your_key_prod_staging
```

And use them in the app using BuildConfig.CONSUMER_KEY and BuildConfig.CONSUMER_SECRET (as shown in the app/build.gradle file)

### Permissions

Add the following permissions to your Android Manifest file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest>
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  <uses-permission android:name="android.permission.BLUETOOTH" />
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-permission android:name="android.permission.INTERACT_ACROSS_USERS_FULL" />
  <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
  <uses-permission android:name="android.permission.READ_PHONE_STATE" />
  <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
  <uses-permission android:name="android.permission.CAMERA" />

  // Used to change audio routes
  <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
</manifest>
```

In order to target Android API level 21 or later, you will need to ensure that your application requests runtime permissions for microphone and camera access. To do this, perform the following step:

Request microphone and camera permissions from within your activity/fragment :

```java
ActivityCompat.requestPermissions(this,
    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST_CODE);
}
```

```java
ActivityCompat.requestPermissions(this,
    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
}
```

You can also request both at the same time:

```java
ActivityCompat.requestPermissions(this,
    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
}
```

See the [Official Android Documentation] for more details.

### Application initialization

To help you integrate the SDK, we provide the `VoxeetApplication`, this class ensures that you'll initialize the SDK and have a fully integrated experience as developer to enrich your UX.

This class makes mandatory to return a `Promise` which will `resolve` as soon as the SDK is initialized. A simple implementation is :
This method will then be internally used by the various components of the SDK.

```
    @NonNull
    @Override
    public Promise<Boolean> uniqueInitializeSDK() {
        return new Promise<>(new PromiseSolver<Boolean>() {
            @Override
            public void onCall(@NonNull Solver<Boolean> solver) {
                VoxeetSdk.initialize(SampleApplication.this,
                        BuildConfig.CONSUMER_KEY,
                        BuildConfig.CONSUMER_SECRET,
                        _current_user);

                solver.resolve(true);
            }
        });
    }
```

Make sure to use this method to initialize the sdk (.then.error or .execute)

### Activity management

Every activities extending the `VoxeetAppCompatActivity` will manage the incoming call invitation and the accepted calls.

### Incoming call management

The sample provides a way to easily deal with the incoming calls. The SDK can be configured to reflect the configuration.
To help integrate with the below FCM configuration, it's also possible to set default values into the AndroidManifest.

Add 2 *<meta-data />* values in the Manifest to reflect the :
  - incoming call activity
  - default activity to start when the call is accepted

The value of each of those *<meta-data />* needs to be the fully qualified name of those. For instance :

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- if a push notification is received from a killed-state app, the accept/declined calls will arrive there -->
        <!-- Note : any override in the code will replace this metadata -->
        <meta-data
            android:name="voxeet_incoming_class"
            android:value="com.voxeet.toolkit.activities.notification.DefaultIncomingCallActivity" />

        <!-- if a push notification is received from killed-state app, accepted calls will arrive there // possible override in code -->
        <!-- Note : any VoxeetAppCompat activity started will override this metadata -->
        <meta-data
            android:name="voxeet_incoming_accepted_class"
            android:value="fr.voxeet.sdk.sample.activities.MainActivity" />

    </application>

</manifest>
```


### FCM

Please notice the following steps are required only if you plan on using fcm.
To enable Voxeet notifications (getting a new call, conference ended and so on...) on your applications:
  1. Send us the application fcm token
  2. Add the google.json file to your project
  2. Add this to your Android Manifest:

```xml

<?xml version="1.0" encoding="utf-8"?>
<manifest>
  <application>
    <service android:name="voxeet.com.sdk.firebase.VoxeetFirebaseMessagingService">
      <intent-filter android:priority="999">
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
      </intent-filter>
    </service>
    <service android:name="voxeet.com.sdk.firebase.VoxeetFirebaseInstanceIDService">
      <intent-filter android:priority="999">
        <action android:name="com.google.firebase.INSTANCE_ID_EVENT" />
      </intent-filter>
    </service>
  </application>
</manifest>
```

### Logger

A logger has been added to the SDK allowing users to track events more easily. 3 different levels for 3 different types of informations:

  1. DEBUG for every event dispatched through the eventbus.
  2. INFO to display methods results when calling a SDK method.
  3. ERROR when an error occurs.

Please also note that WebRTC has its own logger for WebRTC related events.

## Online documentation

You can check our documentation directly on our [developer portal](https://developer.voxeet.com/reference/android/reference-Android/)

## Conference event flow

1. ConferenceCreatedEvent (if you're the one creating the conference, joining it is automatic)

2. ConferenceJoinedSuccessEvent or ConferenceJoinedErrorEvent after joining it

3.  a. ConferenceUserJoinedEvent when someone joins the conf

    b. ConferenceUserUpdatedEvent when someone starts/stop streaming

    c. ConferenceUserLeftEvent when someone left

4. ConferenceLeftSuccessEvent or ConferenceLeftErrorEvent after leaving the conference

5. ConferenceEndedEvent if a conference has ended such a replay ending

6. ConferenceDestroyedEvent when the conference is destroyed

## Best practice regarding conferences

Only one instance of a conference is allowed to be live. Leaving the current conference before creating or joining another one is mandatory. Otherwise, a IllegalStateException will be thrown.

You can check for the current status directly using : 

```
VoxeetSdk.getInstance().getConferenceService().isLive()
```

## Conference stats

It is possible to retrieve the conference information using 2 solutions.

### Pull the conference information from the local WebRTC instance

The documentation concerning the Local Stats are available in the [Stats.md](Stats.md) file

- usage
- result
- behaviour 

### Get (pull) the conference information from the server

This method will make a network call to get the information. The method `getConferenceStatus` available in the `ConferenceService`.
The obtained promise resolves a `GetConferenceStatusEvent`

### Subscribe to conference information

This method will post regularly the information about the conference. It is mandatory to use this method when the implementation needs the Audio/Video statistics of the conference. This method is available via the `subscribe` in the `ConferenceService`.

Any registered objects will then receive at regular intervals the following event `ConferenceStatsEvent`. This event gives access to a `ConferenceStats` object which has various accessors.

#### Event

```
@Subscribe
public void onEvent(ConferenceStatsEvent event) {
  ConferenceStats stats = event.getEvent();
  
  //... manage the stats here -> reposting, saving, etc...
}
```

#### Structure

The `ConferenceStats` object gives access to `Infos`. Each `Infos` corresponds to a specific `userId`. The `Infos` contains an array of `Stats`

```
public class Stats {
    public String media;
    public float score;
    public long ssrc;
    public String lastPkt;
    public String firstPkt;
    public double jitter;
    public long lostPkt;
    public long fracLostPkt;
    public long recvBitrate;
    public long recvPkt;
    public long recvOct;
    public int audioLevel;
}
```

 - `media`: Audio or Video
 - `score`: the overall score (higher is better)
 - `ssrc`: the current Ssrc of this user/media
 - `lastPkt`: the last network packet received from this user
 - `firstPkt`: the first network package received from this user
 - `jitter`: the current jitter
 - `lostPkt`: count of lost packets
 - `fracLostPkt`: ratio of lost packets
 - `recvBitrate`: received bitrate from this user
 - `recvPkt`: count of packets received from this user
 - `recvOct`: count of bytes received from this user
 - `audioLevel`: the average 'current' user audio level (if audio)

## Network management

### Local management

The Voxeet SDK is made to automatically manage network reconnection and network errors. Any attempt from the SDK to reconnect will follow this lifecycle :

- "CLOSED" (initial state)
- CONNECTING
- CONNECTED (if ok)
- CLOSING
- CLOSED

Those states are available through the `SocketStateChangeEvent`
Note that any connectivity success will also trigger the `SocketConnectEvent`

It is possible to check for the current connectivity via the SDK directly :
```
VoxeetSdk.getInstance().isSocketOpen()
```

### Remote management

If any remote users leave a conference "safely", the corresponding event will be fired (see the Conference lifecycle).

In the case of network failure of any remote user, the server will try to reconnect this userwith a `40s` timeout delay. After that moment, a `ConferenceUserLeftEvent` is fired.


## Version


public-sdk: 1.4.7
toolkit: 1.4.7

## Tech

The Voxeet Android SDK uses a number of open source projects to work properly:

* [Retrofit2] - A type-safe HTTP client for Android and Java.
* [GreenRobot/EventBus] - Android optimized event bus.
* [Jackson] - Jackson is a suite of data-processing tools for Java.
* [Butterknife] - Bind Android views and callbacks to fields and methods.
* [Picasso] - A powerful image downloading and caching library for Android.
* [Recyclerview] - An android support library.
* [Apache Commons] - Collection of open source reusable Java components from the Apache/Jakarta community.
* [RxAndroid] - RxJava is a Java VM implementation of Reactive Extensions: a library for composing asynchronous and event-based programs by using observable sequences.
* [SimplePromise] - A low footprint simple Promise implementation for Android: easy and reliable Promises with chaining and resolution

## Sample Application

A sample application is available on this [public repository][sample] on GitHub.

© Voxeet, 2018

   [Official Android Documentation]: <http://developer.android.com/training/permissions/requesting.html>
   [sample]: <https://github.com/voxeet/android-sdk-sample.git>
   [GreenRobot/EventBus]: <https://github.com/greenrobot/EventBus>
   [Jackson]: <https://github.com/FasterXML/jackson>
   [Picasso]: <http://square.github.io/picasso>
   [Recyclerview]: <https://developer.android.com/reference/android/support/v7/widget/RecyclerView.html>
   [Butterknife]: <http://jakewharton.github.io/butterknife>
   [Apache Commons]: <https://commons.apache.org>
   [RxAndroid]: <https://github.com/ReactiveX/RxAndroid>
   [Retrofit2]: <http://square.github.io/retrofit/>
   [SimplePromise]: <https://github.com/codlab/android_promise>
