package org.opus.beacon;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class BeaconSubmissionView extends Activity
        implements GoogleApiClient.ConnectionCallbacks,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "Beacon Submission View";
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private static ImageView beaconImage;
    private static Bitmap imageBitmap;
    private static EditText descriptionBox;
    private GoogleApiClient mGoogleApiClient;
    private  Context context;
    private static BeaconRestClient client;
    private static int userId;
    private static int newBeaconId;
    private static boolean pictureTaken;
    private static Uri mImageUri;

    private Location mLastLocation;
    private double mLatitude;
    private double mLongitude;

    private int ACCESS_CAMERA_TAG = 127;

    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.beacon_submission_view);
        Intent intent = getIntent();
        beaconImage = (ImageView) findViewById(R.id.thumbnail);
        descriptionBox = (EditText) findViewById(R.id.descrBox);
        pictureTaken = false;
        context = this;
        try {
            Auth auth = new Auth(context);
            userId = auth.getIntId();
            client = new BeaconRestClient(auth.getId(), auth.getSecret());
        } catch(Exception e) {
            finish();
            return;
        }
        buildGoogleApiClient();
        onTakePicture();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            mLatitude = mLastLocation.getLatitude();
            mLongitude = mLastLocation.getLongitude();

        } else {
            Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    public void onTakePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    ACCESS_CAMERA_TAG);
        } else {
            try {
                takePicture();
            }
            catch (Exception e){
                Toast toast = Toast.makeText(context, "Error taking picture", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    public void takePicture(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photo;
        try
        {
            photo = this.createTemporaryFile("picture", ".jpg");
            photo.delete();
        }
        catch(Exception e)
        {
            Log.v(TAG, "Can't create file to take picture!");
            Toast toast = Toast.makeText(context, "Please check SD card! Image shot is impossible!", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        mImageUri = Uri.fromFile(photo);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }

    private File createTemporaryFile(String part, String ext) throws Exception
    {
        File tempDir= Environment.getExternalStorageDirectory();
        tempDir=new File(tempDir.getAbsolutePath()+"/.temp/");
        if(!tempDir.exists())
        {
            tempDir.mkdir();
        }
        return File.createTempFile(part, ext, tempDir);
    }

    public void grabImage(ImageView imageView)
    {
        this.getContentResolver().notifyChange(mImageUri, null);
        ContentResolver cr = this.getContentResolver();
        Bitmap bitmap;
        try
        {
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(cr, mImageUri);
            imageBitmap = scalePic(bitmap);
            beaconImage.setImageBitmap(imageBitmap);
        }
        catch (Exception e)
        {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Failed to load", e);
        }
    }

    private Bitmap scalePic(Bitmap source){
        if (source.getWidth() >=2048 || source.getHeight() >= 2048){
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int targetWidth = metrics.widthPixels;
            int targetHeight = metrics.heightPixels;
            source = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        }
        return source;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                this.grabImage(beaconImage);
                pictureTaken = true;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode != ACCESS_CAMERA_TAG)
            return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                takePicture();
            }catch (Exception e) {

            }
        }
    }

    public void onSubmitBeacon(View v) {
        if (pictureTaken) {
            JsonMsg.PostBeaconRequest newBeacon = new JsonMsg.PostBeaconRequest(userId, descriptionBox.getText().toString(), (float) mLatitude, (float) mLongitude);
            new SubmitBeacon().execute(newBeacon);
        }
        else {
            Toast toast = Toast.makeText(context, "No picture taken",Toast.LENGTH_SHORT);
        }

    }

    private class SubmitBeacon extends AsyncTask <JsonMsg.PostBeaconRequest, Void, RestException> {
        @Override
        protected RestException doInBackground (JsonMsg.PostBeaconRequest... params){
            try {
                newBeaconId = client.postBeacon(params[0], imageBitmap);
                return null;
            } catch(final RestException e) {
                if (e.shouldInformUser()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT);
                            TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
                            v.setTextColor(Color.WHITE);
                            v.setBackgroundColor(0x00000000);
                            toast.show();
                        }
                    });
                    finish();
                }

                return e;
            }
        }

        @Override
        protected void onPostExecute (RestException e) {
            if(e != null && e.shouldInformUser()) {
                Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT);
                toast.show();
            }
            else {
                Intent launchThread = new Intent(context, ThreadView.class);
                launchThread.putExtra("beaconID", newBeaconId);
                startActivity(launchThread);
                finish();
            }
        }
    }

}