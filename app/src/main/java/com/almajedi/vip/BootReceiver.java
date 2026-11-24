package com.almajedi.vip;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {public void onReceive(Context c,Intent i){if(!c.getSharedPreferences("vip",0).getBoolean("enabled",false))return;PendingResult r=goAsync();Fence.start(c,()->r.finish(),e->r.finish());}}
