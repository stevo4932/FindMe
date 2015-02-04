package com.ikardwynne.scott.findme;

import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO: need settings menu
//TODO: Change layout for landscape view.

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback{

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 4832;
    private GoogleApiClient mClient;
    private Location location;
    private TextView locationText;
    private AddressResultReceiver mResultReceiver;
    private static final int INTERVAL = 1000;
    private static final int FAST_INTERVAL = INTERVAL/2;
    private boolean updateOn;
    private GoogleMap map;
    private Marker marker;
    private EditText phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mResultReceiver = new AddressResultReceiver(new Handler());
        //restore the values.
        if(savedInstanceState != null) {
            location = new Location("newLocation");
            location.setLongitude(savedInstanceState.getDouble("lng"));
            location.setLatitude(savedInstanceState.getDouble("lat"));
        }
        updateOn = false;
        mClient = buildGoogleApiClient();
        locationText = (TextView) findViewById(R.id.location);
        phoneNumber = (EditText) findViewById(R.id.phone_number);

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(location != null) {
            outState.putDouble("la t", location.getLatitude());
            outState.putDouble("lng", location.getLongitude());
        }
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
        Button contacts = (Button) findViewById(R.id.contact_button);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String number = phoneNumber.getText().toString();
                if(number != null && !number.isEmpty())
                    sendTextMessage(number);
                else
                    Toast.makeText(MainActivity.this, "Error: No phone number", Toast.LENGTH_SHORT).show();
            }
        });

        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocationUpdates();
            }
        });

        contacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContact();
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
                if(cursor.getCount() > 0) {
                    cursor.moveToFirst();

                    // Retrieve the phone number from the NUMBER column
                    int column = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    String number = cursor.getString(column);
                    phoneNumber.setText(number);
                }else{
                    Toast.makeText(this, "Error: Could not load phone number", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Cursor is not greater than 0");
                }
                //sendTextMessage(number);
                cursor.close();
            }else
                Toast.makeText(this, "Error: Could not retrieve contact", Toast.LENGTH_SHORT).show();
        }
    }

    private String messageText(){
        if(location != null)
            return  locationText.getText().toString()+"\n" +
                    "\nMap: http://maps.google.com/?q="+this.location.getLatitude()+","+this.location.getLongitude();
        return  "My Location: Unavalible";
    }

    private boolean isPhoneNumber(String number){
        Pattern pattern1 = Pattern.compile("\\(\\d{3}\\) \\d{3}-\\d{4}");
        Pattern pattern2 = Pattern.compile("\\d{10}");
        Matcher matcher1 = pattern1.matcher(number);
        Matcher matcher2 = pattern2.matcher(number);

        if (matcher1.matches() || matcher2.matches())
           return true;
        return false;
    }

    private void sendTextMessage(String number){
        if(isPhoneNumber(number)) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                //TODO: should use the pending intents to see if message was actually sent.
                smsManager.sendTextMessage(number, null, messageText(), null, null);
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                Log.d(TAG, e.getMessage());
                Toast.makeText(this, "Error: Message not sent", Toast.LENGTH_SHORT).show();
            }
        }else
            Toast.makeText(this, "Error: phone number is not valid", Toast.LENGTH_SHORT).show();
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
            startIntentService();
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
        startIntentService();
    }

    private void displayLocation(String address){
        if(address != null)
            locationText.setText("Your Location:\n"+address);
        else
            locationText.setText("Your Location: \n"+location.getLatitude()+", "+location.getLongitude());
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(FetchAddressIntentService.Constants.RECEIVER, mResultReceiver);
        intent.putExtra(FetchAddressIntentService.Constants.LOCATION_DATA_EXTRA, location);
        startService(intent);
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
                        startIntentService();

                    }
                });
            }
        }
    }

    class AddressResultReceiver extends ResultReceiver {

        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            String mAddress = resultData.getString(FetchAddressIntentService.Constants.RESULT_DATA_KEY);
            if(resultCode == FetchAddressIntentService.Constants.SUCCESS_RESULT)
                displayLocation(mAddress);
            else {
                displayLocation(null);
                Toast.makeText(MainActivity.this, mAddress, Toast.LENGTH_SHORT).show();
            }

        }
    }
}
