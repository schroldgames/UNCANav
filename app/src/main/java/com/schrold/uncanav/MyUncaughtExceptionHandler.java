package com.schrold.uncanav;

import androidx.annotation.NonNull;

public class MyUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        System.out.println("UNCAUGHT EXCEPTION");
        System.out.println(e.toString());
    }
}
