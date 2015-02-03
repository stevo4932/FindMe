package com.ikardwynne.scott.findme;

import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


//TODO: need reverse geocoding
//TODO: need settings menu
//TODO: Change layout for landscape view.

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback{

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 4832;
    private GoogleApiClient mClient;
    private Location location;
    private TextView locationText;
    private static final int INTERVAL = 1000;
    private static final int FAST_INTERVAL = INTERVAL/2;
    private boolean updateOn;
    private GoogleMap map;
    private Marker marker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //restore the values.
        if(savedInstanceState != null) {
            location = new Location("newLocation");
            location.setLongitude(savedInstanceState.getDouble("lng"));
            location.setLatitude(savedInstanceState.getDouble("lat"));
        }
        updateOn = false;
        mClient = buildGoogleApiClient();
        locationText = (TextView) findViewById(R.id.location);

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble("lat", location.getLatitude());
        outState.putDouble("lng", location.getLongitude());
    }

    @Override
    //Might need to change this to on resume.
    protected void onStart() {
        super.onStart();
        if(!(mClient.isConnected() || mClient.isConnecting()))
            mClient.connect();
    }

    @Override
    protected void onStop() {
        if(updateOn)
            stopLocationUpdates();
        mClient.disconnect();
        super.onStop();
    }

    //THIS IS NOT USEFULL FOR NOW. MAYBE LATER.
    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/

    private void setButtons(){
        Button send = (Button) findViewById(R.id.send_button);
        Button update = (Button) findViewById(R.id.update_button);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContact();
            }
        });

        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocationUpdates();
            }
        });
    }

    private void getContact() {
        Intent contact = new Intent(Intent.ACTION_PICK, Uri.parse("content://contacts"));
        contact.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(contact, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CODE && resultCode == RESULT_OK){
            //get contact from data and send text.
            if(data != null) {
                Cursor cursor = getContentResolver()
                .query(data.getData(), new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
                cursor.moveToFirst();

                // Retrieve the phone number from the NUMBER column
                int column = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = cursor.getString(column);
                sendTextMessage(number);
                cursor.close();
            }else
                Toast.makeText(this, "Error: Could not retrieve contact", Toast.LENGTH_SHORT).show();
        }
    }

    private String messageText(){
        if(location != null)
            //TODO: Location should be the string address not lat and lng.
            return  "My Location: "+location.toString()+"\n" +
                    "Map: http://maps.google.com/?q="+this.location.getLatitude()+","+this.location.getLongitude();
        return  "My Location: Unavalible";
    }

    private void sendTextMessage(String number){
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, messageText(), null, null);
            Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
        }catch(IllegalArgumentException e){
            Log.d(TAG, e.getMessage());
            Toast.makeText(this, "Error: Message not sent", Toast.LENGTH_SHORT).show();
        }
    }

    /** Set up google play services **/

    protected synchronized GoogleApiClient buildGoogleApiClient() {
        Log.i(TAG, "Building google client");
        return new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected!");
        if(location == null)
            location = LocationServices.FusedLocationApi.getLastLocation(mClient);
        if (location != null){
            displayLocation();
            setButtons();
            MapFragment mapFragment = (MapFragment) getFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection was Suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //TODO: should be pretty robust.
        Log.d(TAG, "Connection failed");
    }

    /**Start of Location update**/

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FAST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mClient, createLocationRequest(), this);
        updateOn = true;
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mClient, this);
        updateOn = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location updating");
        this.location = location;
        stopLocationUpdates();
        if(map != null){
            updateCamera();
            marker = setNewMarker();
        }
        displayLocation();
    }

    private void displayLocation(){
        //Todo: Should also be the human readable street address.
        locationText.setText("Location: "+location.getLatitude()+", "+location.getLongitude());
    }

    private Marker setNewMarker(){
        map.clear();
        return map.addMarker(new MarkerOptions()
                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                .draggable(true)
                .title("Your Location"));
    }

    private void updateCamera(){
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 17));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i(TAG, "new map being made");
        map = googleMap;
        googleMap.setIndoorEnabled(false);
        if(location != null) {
            updateCamera();
            if(marker == null) {
                marker = setNewMarker();
                map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        Location newLocation = new Location("newMarker");
                        newLocation.setLatitude(latLng.latitude);
                        newLocation.setLongitude(latLng.longitude);
                        location = newLocation;
                        setNewMarker();
                        displayLocation();
                    }
                });
            }
        }
    }
}
