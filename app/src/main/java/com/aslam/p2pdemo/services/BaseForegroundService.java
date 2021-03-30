package com.aslam.p2pdemo.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public abstract class BaseForegroundService extends Service {

    protected int REQUEST_CODE, NOTIFICATION_ID = 3000;
    protected String CHANNEL_ID = "ForegroundService_ID";
    protected String CHANNEL_NAME = "ForegroundService Channel";
    protected final IBinder mBinder = new LocalBinder();

    protected NotificationManager mNotificationManager;
    protected NotificationCompat.Builder mNotificationBuilder;

    public class LocalBinder extends Binder {
        public com.aslam.p2pdemo.services.BaseForegroundService getService() {
            return com.aslam.p2pdemo.services.BaseForegroundService.this;
        }
    }

    protected abstract Notification serviceNotification();

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        startForeground(NOTIFICATION_ID, serviceNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    protected Notification createNotification(String title, String message, int smallIcon, int bigIcon, Class<?> intentClass) {

        mNotificationBuilder.setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true);

        if (smallIcon != 0) {
            mNotificationBuilder.setSmallIcon(smallIcon);
        }

        if (bigIcon != 0) {
            mNotificationBuilder.setLargeIcon(Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), bigIcon), 128, 128, true));
        }

        if (intentClass != null) {
            Intent notificationIntent = new Intent(this, intentClass);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, REQUEST_CODE, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mNotificationBuilder.setContentIntent(pendingIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setImportance(NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(channel);
        }

        return mNotificationBuilder.build();
    }

    public static void start(Context context, Class<? extends BaseForegroundService> serviceClass) {
        ContextCompat.startForegroundService(context, new Intent(context, serviceClass));
    }

    public static void stop(Context context, Class<? extends BaseForegroundService> serviceClass) {
        context.stopService(new Intent(context, serviceClass));
    }
}