package br.com.luisleao.things.vendingmachine;

import android.app.Activity;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.List;

public class HomeActivity extends Activity {
    private static final String TAG = "HomeActivity";

    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    private static final String RELAY_PIN_NAME = "BCM17"; // GPIO port wired to the RELAY
    private static final String ARDUINO_PIN_NAME = "BCM27"; // GPIO port wired to the ARDUINO
    private Handler mHandler = new Handler();




    private Gpio mRelayGpio;
    private Gpio mArduinoGpio;

    private FirebaseDatabase database;
    private DatabaseReference activateRef;
    private DatabaseReference releaseIntervalRef;

    private long releaseInterval = 0;

    String email = "[YOUR_EMAIL]+things@gmail.com";
    String password = "[YOUR_FAKE_PASSWORD]";


    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;




    private void signIn() {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithEmail:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            createUser();
                        }
                    }
                });
    }

    private void createUser(){

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "createUserWithEmail:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            //LOGIN
                        }

                        // ...
                    }
                });
    }


    private void initializeDatabase() {

        database = FirebaseDatabase.getInstance();
        releaseIntervalRef = database.getReference("releaseInterval");


        //myRef.setValue("Hello, World!");

        activateRef = database.getReference("activate");
        DatabaseReference releasedRef = database.getReference("released");


        releaseIntervalRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                releaseInterval = dataSnapshot.getValue(long.class);
                Log.i("RELEASE INTERVAL ===>", "" + releaseInterval);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        activateRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                String key = dataSnapshot.getKey();

                //Log.i("onChildAdded", "" + s);
                Log.i("KEY =======> ", key);

                try {
                    mRelayGpio.setValue(true);
                    mArduinoGpio.setValue(true);
                    Thread.sleep(releaseInterval);
                    mRelayGpio.setValue(false);
                    mArduinoGpio.setValue(false);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                activateRef.child(key).removeValue();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) { }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) { }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) { }

            @Override
            public void onCancelled(DatabaseError databaseError) { }
        });

        try {
            mArduinoGpio.setValue(true);
            Thread.sleep(releaseInterval);
            mArduinoGpio.setValue(false);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid() + " - " + user.getEmail());
                    initializeDatabase();

                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    //createUser();
                    signIn();
                }
            }
        };





        // Step 1. Create GPIO connection.
        PeripheralManagerService service = new PeripheralManagerService();
        Log.d(TAG, "Available GPIO: " + service.getGpioList());
        try {
            mRelayGpio = service.openGpio(RELAY_PIN_NAME);
            mRelayGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
        try {
            mArduinoGpio = service.openGpio(ARDUINO_PIN_NAME);
            // Step 2. Configure as an output.
            mArduinoGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }

        PeripheralManagerService manager = new PeripheralManagerService();
        List<String> deviceList = manager.getI2cBusList();
        if (deviceList.isEmpty()) {
            Log.i(TAG, "No I2C bus available on this device.");
        } else {
            Log.i(TAG, "List of available devices: " + deviceList);
        }



    }




    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }
    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Step 5. Close the resource.
        if (mRelayGpio  != null) {
            try {
                mRelayGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

        // Step 5. Close the resource.
        if (mArduinoGpio  != null) {
            try {
                mArduinoGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

    }






}




