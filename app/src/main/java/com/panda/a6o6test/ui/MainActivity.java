package com.panda.a6o6test.ui;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.panda.a6o6test.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportFragmentManager().beginTransaction().replace(R.id.video_container, new CameraFragment(), "boo")
                .commit();

        getSupportFragmentManager().beginTransaction().replace(R.id.panel_container, new PanelFragment(), "foo")
                .commit();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        //do nothing, app is in portrait in current version
    }

}