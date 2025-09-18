package com.example.calculator;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class CalculatorApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Enable automatic day/night theme switching based on system settings
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}