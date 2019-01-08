
package com.reactlibrary;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.launchdarkly.android.FeatureFlagChangeListener;
import com.launchdarkly.android.LDClient;
import com.launchdarkly.android.LDConfig;
import com.launchdarkly.android.LDUser;
import com.launchdarkly.android.LaunchDarklyException;

import java.util.Collections;
import java.util.Map;

public class RNLaunchDarklyModule extends ReactContextBaseJavaModule {

  private LDClient ldClient;
  private LDUser user;
  private Application application;
  private static final int START_WAIT_SECONDS = 5; 

  public RNLaunchDarklyModule(Application application, ReactApplicationContext reactContext) {
    super(reactContext);
    this.application = application;
  }

  @Override
  public String getName() {
    return "RNLaunchDarkly";
  }

  @ReactMethod
  public void configure(String apiKey, ReadableMap options) {
    LDUser.Builder userBuilder = new LDUser.Builder(options.getString("key"));

    if (options.hasKey("email")) {
      userBuilder = userBuilder.email(options.getString("email"));
    }

    if (options.hasKey("firstName")) {
      userBuilder = userBuilder.firstName(options.getString("firstName"));
    }

    if (options.hasKey("lastName")) {
      userBuilder = userBuilder.lastName(options.getString("lastName"));
    }

    if (options.hasKey("isAnonymous")) {
      userBuilder = userBuilder.anonymous(options.getBoolean("isAnonymous"));
    }

    if (options.hasKey("organization")) {
      userBuilder = userBuilder.custom("organization", options.getString("organization"));
    }

    if (options.hasKey("custom") && options.getMap("custom") instanceof  ReadableNativeMap) {
      for (Map.Entry<String, Object> entry : options.getMap("custom").toHashMap().entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (value instanceof Number) {
          userBuilder = userBuilder.custom(key, (Number)value);
        } else if (value instanceof String) {
          userBuilder = userBuilder.custom(key, (String)value);
        }
      }
    }

    user = userBuilder.build();

    if (ldClient != null) {
      ldClient.identify(user);
      return;
    }

    LDConfig ldConfig = new LDConfig.Builder()
            .setMobileKey(apiKey)
            .build();

    ldClient = LDClient.init(this.application, ldConfig, user, START_WAIT_SECONDS);
  }

  @ReactMethod
  public void addFeatureFlagChangeListener (String flagName) {
    FeatureFlagChangeListener listener = new FeatureFlagChangeListener() {
      @Override
      public void onFeatureFlagChange(String flagKey) {
        WritableMap result = Arguments.createMap();
        result.putString("flagName", flagKey);

        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("FeatureFlagChanged", result);
      }
    };

    try {
      LDClient.get().registerFeatureFlagListener(flagName, listener);
    } catch (LaunchDarklyException e) {
      Log.d("RNLaunchDarklyModule", e.getMessage());
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void boolVariation(String flagName, Boolean fallback, Callback callback) {
    Boolean variationResult = ldClient.boolVariation(flagName, fallback);
    callback.invoke(variationResult);
  }

  @ReactMethod
  public void stringVariation(String flagName, String fallback, Callback callback) {
    String variationResult = ldClient.stringVariation(flagName, fallback);
    callback.invoke(variationResult);
  }
}
