/*
 * Copyright (c) 2016, 2025, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.helloandroid;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class DalvikPushNotificationsService {

    static final String LAUNCH_PUSH_NOTIFICATION_KEY = "Launch.PushNotification";
    private static final String TAG = Util.TAG;
    private static final boolean debug = Util.isDebug();

    private static Activity activity;
    private static boolean active;
    static int badgeNumber = 0;

    public DalvikPushNotificationsService(Activity activity) {
        DalvikPushNotificationsService.this.activity = activity;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notificationsEnabled = Util.verifyPermissions(Manifest.permission.POST_NOTIFICATIONS);
            if (!notificationsEnabled) {
                Log.v(TAG, "Post notifications disabled. POST_NOTIFICATIONS permission is required");
            }
        }

        active = true;
        Util.setLifecycleEventHandler(new LifecycleEventHandler() {
            @Override
            public void lifecycleEvent(String event) {
                if (event != null && !event.isEmpty()) {
                    switch (event) {
                        case "pause":
                            active = false;
                            break;
                        case "resume":
                            active = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        });
    }

    static Activity getActivity() {
        return activity;
    }

    static boolean isActive() {
        return active;
    }

    public String getPackageName() {
        return activity.getPackageName();
    }

    public int isGooglePlayServicesAvailable() {
        if (debug) {
            Log.d(TAG, "Checking if Google Play Services is available on device");
        }

        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.w(TAG, String.format("Google Play Services Error: %s", apiAvailability.getErrorString(resultCode)));
        } else {
            if (debug) {
                Log.d(TAG, "Google Play Services found");
            }
        }
        return resultCode;
    }

    public String getErrorString(int resultCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return apiAvailability.getErrorString(resultCode);
    }

    public void initializeFirebase(String applicationId, String projectNumber, String projectId, String apiKey) {
        if (debug) {
            Log.d(TAG, "Initializing Firebase for application: " + applicationId +
                    ", project number: " + projectNumber + ", project id: " + projectId +
                    ", and api key: " + apiKey);
        }

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(activity, new FirebaseOptions.Builder()
                .setApplicationId(applicationId)
                .setGcmSenderId(projectNumber)
                .setProjectId(projectId)
                .setApiKey(apiKey)
                .build());

        Log.i(TAG, "FirebaseApp initialized succesfully: " + firebaseApp);

        // schedule a job that triggers every hour and only prints out a single line
        // this is a way to allow the app to keep receiving push notifications, even
        // in case the app was closed forcibly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobInfo jobInfo = new JobInfo.Builder(PushNotificationJobService.JOB_ID, new ComponentName(activity, PushNotificationJobService.class))
                    .setPersisted(true)
                    .setPeriodic(1000 * 60 * 60) // every hour
                    .build();

            JobScheduler scheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            scheduler.schedule(jobInfo);
        }

        // retrieve token, as it might have been updated
        PushFcmMessagingService.retrieveToken();
    }

    public void setBadgeNumber(int number) {
        DalvikPushNotificationsService.badgeNumber = number;
    }

    public void removeAllNotifications() {
        NotificationManager notificationManager =
                (NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    static void sendRuntimeArgs(String value) {
        processRuntimeArgs(LAUNCH_PUSH_NOTIFICATION_KEY, value);
    }

    private native static void processRuntimeArgs(String key, String value);
}
