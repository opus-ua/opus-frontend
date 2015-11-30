package org.opus.beacon;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import android.location.LocationManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class MapActivity extends FragmentActivity
    implements LocationListener,
    ActivityCompat.OnRequestPermissionsResultCallback,
    GoogleMap.OnMarkerClickListener,
    GoogleMap.OnCameraChangeListener {

    private GoogleMap mMap;
    private LocationManager mLocationManager;
    private Context context;

    private float MAX_ZOOM = 18.0f;
    private float MIN_ZOOM = 3.0f;

    private float MAX_TILT = 60.0f;
    private float MIN_TILT = 0.0f;

    private int ACCESS_FINE_LOCATION_TAG = 125;

    private BeaconRestClient mClient;
    private Auth mAuth;

    private HashMap<Marker, BeaconThumb> mMarkerHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        mMarkerHash = new HashMap<Marker, BeaconThumb>();
        setContentView(R.layout.activity_map);
        setUpMapIfNeeded();

        try {
            mAuth = new Auth(this);
            mClient = new BeaconRestClient(mAuth.getId(), mAuth.getSecret());
        } catch (Exception e) {
            toastError(e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.setOnMarkerClickListener(this);
        mMap.setOnCameraChangeListener(this);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setTiltGesturesEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);

        // start at center of contiguous United States
        // this location should probably be a project-level property
        CameraUpdate center = CameraUpdateFactory.newLatLngZoom(new LatLng(39.8282f, -98.5795f), MIN_ZOOM);
        mMap.moveCamera(center);

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    ACCESS_FINE_LOCATION_TAG);
        } else {
            requestGPSUpdates();
        }
    }

    private void requestGPSUpdates() {
        mLocationManager.requestLocationUpdates(mLocationManager.GPS_PROVIDER, 1000, 1, this);
    }

    public void toastError(String err) {
        Toast toast = Toast.makeText(context, err, Toast.LENGTH_SHORT);
        TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
        v.setTextColor(Color.WHITE);
        v.setBackgroundColor(0x00000000);
        toast.show();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("Map View", "Accuracy: " + Float.toString(location.getAccuracy()));
        if (location.getAccuracy() > 200.0f)
            return;

        CameraPosition movement = new CameraPosition.Builder()
                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                .zoom(MAX_ZOOM)
                .tilt(MAX_TILT)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(movement), 2000, null);
        mLocationManager.removeUpdates(this);
        new GetLocalBeacons().execute(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode != ACCESS_FINE_LOCATION_TAG)
            return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestGPSUpdates();
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        BeaconThumb thumb = mMarkerHash.get(marker);
        Intent launchThreadView = new Intent(this, ThreadView.class);
        launchThreadView.putExtra("beaconID", thumb.getId());
        startActivity(launchThreadView);
        return false;
    }

    @Override
    public void onCameraChange(CameraPosition position) {
        mCurrentCamera = position;
        mCameraHandler.removeCallbacks(mCameraCheckRunnable);
        checkCameraAnimating();
    }

    public void onCameraAnimationEnd() {
        float curZoom = mCurrentCamera.zoom;
        LatLng location = mCurrentCamera.target;
        float curTilt;

        if (curZoom > MAX_ZOOM) {
            curTilt = MAX_TILT;
        } else if (curZoom < MIN_ZOOM) {
            curTilt = MIN_TILT;
        } else {
            float m = (MAX_TILT - MIN_TILT) / (MAX_ZOOM - MIN_ZOOM);
            curTilt = m * (curZoom - MIN_ZOOM) + MIN_TILT;
        }

        final CameraPosition movement = new CameraPosition.Builder()
                .target(location)
                .zoom(curZoom)
                .tilt(curTilt)
                .build();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(movement));
            }
        });
    }

    private CameraPosition mPreviousCamera = null;
    private CameraPosition mCurrentCamera = null;
    private Handler mCameraHandler = new Handler();
    private boolean mAnimationEndHandled = false;
    private Runnable mCameraCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkCameraAnimating();
        }
    };

    public void checkCameraAnimating() {
        if (mPreviousCamera == null || animationInProgress(mCurrentCamera)) {
            mPreviousCamera = mCurrentCamera;
            if (mAnimationEndHandled)
            mAnimationEndHandled = false;
            mCameraHandler.postDelayed(mCameraCheckRunnable, 30);
        } else if (!mAnimationEndHandled) {
            mAnimationEndHandled = true;
            onCameraAnimationEnd();
        }
    }

    private boolean animationInProgress(CameraPosition pos) {
        if (mPreviousCamera.zoom != pos.zoom)
            return true;

        if (!mPreviousCamera.target.equals(pos.target))
            return true;

        return false;
    }

    private class GetLocalBeacons extends AsyncTask<Location, Void, RestException> {
        @Override
        protected RestException doInBackground(Location... params) {
            try {
                final ArrayList<BeaconThumb> thumbs = mClient.getLocalBeacons(params[0]);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (BeaconThumb thumb : thumbs) {
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(thumb.getLatitude(), thumb.getLongitude())));
                            mMarkerHash.put(marker, thumb);
                        }
                    }
                });
                return null;
            } catch (RestException e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(RestException err) {
            if(err != null && err.shouldInformUser()) {
                toastError(err.getMessage());
            }
        }
    }

    public void launchCameraView(View view) {
        Intent launchCamera  = new Intent(this, BeaconSubmissionView.class);
        startActivity(launchCamera);
    }
}
