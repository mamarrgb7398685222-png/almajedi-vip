package com.almajedi.vip;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.os.Build;import com.google.android.gms.location.*;
class Fence {
 static PendingIntent intent(Context c){return PendingIntent.getBroadcast(c,1,new Intent(c,FenceReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));}
 static boolean allowed(Context c){return c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT<29||c.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED);}
 static void start(Context c,Runnable ok,java.util.function.Consumer<Exception> error){if(!allowed(c)){error.accept(new SecurityException("Location permission"));return;} Geofence f=new Geofence.Builder().setRequestId("almajedi").setCircularRegion(12.755830,44.889946,200).setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).build(); LocationServices.getGeofencingClient(c).addGeofences(new GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(f).build(),intent(c)).addOnSuccessListener(v->ok.run()).addOnFailureListener(error::accept);}
}
