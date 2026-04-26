import os

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)

write("build.gradle", """plugins {
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
""")

write("settings.gradle", """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SafeStreamAI"
include ':app'
""")

write("gradle.properties", """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""")

write("gradlew", """#!/bin/sh
APP_HOME=`dirname "$0"`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
""")
os.chmod("gradlew", 0o755)

write("gradle/wrapper/gradle-wrapper.properties", r"""distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")

write("app/build.gradle", """plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'com.safestream.ai'
    compileSdk 34
    defaultConfig {
        applicationId "com.safestream.ai"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug { debuggable true }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
}
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
}
""")

write("app/proguard-rules.pro", """-keep class com.safestream.ai.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
""")

write("app/src/main/AndroidManifest.xml", """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.safestream.ai">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions"/>
    <application
        android:allowBackup="true"
        android:label="SafeStream AI"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.SafeStream">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity android:name=".SettingsActivity" android:exported="false"/>
        <activity android:name=".BlockOverlayActivity" android:exported="false"
            android:launchMode="singleInstance" android:excludeFromRecents="true"
            android:theme="@style/Theme.SafeStream.BlockOverlay"/>
        <receiver android:name=".BootReceiver" android:enabled="true" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>
        <service android:name=".SafeAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.view.accessibility.AccessibilityService"/>
            </intent-filter>
            <meta-data android:name="android.view.accessibility.accessibilityservice"
                android:resource="@xml/accessibility_service_config"/>
        </service>
    </application>
</manifest>
""")

write("app/src/main/res/values/colors.xml", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="safe_green">#00C48C</color>
    <color name="safe_green_dark">#00A87A</color>
    <color name="danger_red">#FF3D57</color>
    <color name="amber">#FFB400</color>
    <color name="background_dark">#0B1622</color>
    <color name="surface_dark">#131F2E</color>
    <color name="surface_border">#1D3048</color>
    <color name="block_overlay_bg">#CC1A0A1F</color>
    <color name="white">#FFFFFF</color>
    <color name="text_muted">#607080</color>
    <color name="colorPrimary">@color/safe_green</color>
    <color name="colorPrimaryVariant">@color/safe_green_dark</color>
    <color name="colorOnPrimary">@color/white</color>
    <color name="colorSecondary">@color/amber</color>
    <color name="colorOnSecondary">@color/background_dark</color>
    <color name="colorSurface">@color/surface_dark</color>
    <color name="colorOnSurface">@color/white</color>
    <color name="colorBackground">@color/background_dark</color>
    <color name="colorOnBackground">@color/white</color>
    <color name="colorError">@color/danger_red</color>
</resources>
""")

write("app/src/main/res/values/strings.xml", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">SafeStream AI</string>
    <string name="accessibility_service_label">SafeStream AI</string>
    <string name="accessibility_service_description">SafeStream AI monitors YouTube in real time to protect your child. Only video titles and channel names are read.</string>
    <string name="status_active">AI Monitoring: ACTIVE</string>
    <string name="status_inactive">AI Monitoring: INACTIVE</string>
    <string name="dialog_enable_title">Enable Monitoring</string>
    <string name="dialog_disable_title">Disable Protection?</string>
    <string name="dialog_disable_message">This will stop the AI from filtering YouTube content.</string>
    <string name="toast_api_key_saved">API key saved</string>
    <string name="toast_name_saved">Name saved</string>
    <string name="toast_time_saved">Time limit updated</string>
    <string name="toast_log_cleared">History cleared</string>
    <string name="toast_threshold_updated">Filter set to %d%%</string>
</resources>
""")

write("app/src/main/res/values/themes.xml", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SafeStream" parent="Theme.Material3.DayNight">
        <item name="colorPrimary">@color/safe_green</item>
        <item name="colorPrimaryVariant">@color/safe_green_dark</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/amber</item>
        <item name="colorOnSecondary">@color/background_dark</item>
        <item name="colorSurface">@color/surface_dark</item>
        <item name="colorOnSurface">@color/white</item>
        <item name="android:colorBackground">@color/background_dark</item>
        <item name="colorError">@color/danger_red</item>
        <item name="android:statusBarColor">@color/surface_dark</item>
        <item name="android:navigationBarColor">@color/background_dark</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
    <style name="Theme.SafeStream.BlockOverlay" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/block_overlay_bg</item>
        <item name="android:statusBarColor">@color/block_overlay_bg</item>
        <item name="android:navigationBarColor">@color/block_overlay_bg</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
""")

write("app/src/main/res/xml/accessibility_service_config.xml", """<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:packageNames="com.google.android.youtube"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:settingsActivity=".SettingsActivity"/>
""")

write("app/src/main/res/drawable/circle_dot_red.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/danger_red"/>
    <size android:width="12dp" android:height="12dp"/>
</shape>
""")

write("app/src/main/res/drawable/circle_dot_green.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/safe_green"/>
    <size android:width="12dp" android:height="12dp"/>
</shape>
""")

write("app/src/main/res/drawable/bg_pill_green.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#1A00C48C"/>
    <corners android:radius="10dp"/>
</shape>
""")

write("app/src/main/res/layout/activity_main.xml", """<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_dark">
    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:background="@color/surface_dark" app:elevation="0dp">
        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar" android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="SafeStream AI" app:subtitle="Parent Dashboard"
            app:titleTextColor="@color/white" app:subtitleTextColor="@color/text_muted"/>
    </com.google.android.material.appbar.AppBarLayout>
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent" android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"
        android:padding="16dp" android:clipToPadding="false">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical">
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="18dp" app:strokeWidth="0dp">
                <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:orientation="horizontal" android:padding="18dp" android:gravity="center_vertical">
                    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content"
                        android:layout_weight="1" android:orientation="vertical">
                        <TextView android:id="@+id/tv_service_status"
                            android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="AI Monitoring: INACTIVE" android:textSize="15sp"
                            android:textStyle="bold" android:textColor="@color/danger_red"/>
                        <TextView android:id="@+id/tv_last_blocked"
                            android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="No blocks today" android:textSize="12sp"
                            android:textColor="@color/text_muted" android:layout_marginTop="4dp"/>
                    </LinearLayout>
                    <View android:id="@+id/view_status_dot" android:layout_width="12dp"
                        android:layout_height="12dp" android:layout_marginEnd="14dp"
                        android:background="@drawable/circle_dot_red"/>
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btn_toggle_service" android:layout_width="wrap_content"
                        android:layout_height="wrap_content" android:text="Enable"
                        android:textSize="12sp" app:cornerRadius="10dp"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="horizontal" android:layout_marginBottom="12dp">
                <com.google.android.material.card.MaterialCardView
                    android:layout_width="0dp" android:layout_height="wrap_content"
                    android:layout_weight="1" android:layout_marginEnd="6dp"
                    app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
                    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:orientation="vertical" android:padding="16dp">
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="Blocked" android:textSize="11sp" android:textColor="@color/text_muted"/>
                        <TextView android:id="@+id/tv_block_count" android:layout_width="wrap_content"
                            android:layout_height="wrap_content" android:text="0" android:textSize="28sp"
                            android:textStyle="bold" android:textColor="@color/danger_red"/>
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="today" android:textSize="11sp" android:textColor="@color/text_muted"/>
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
                <com.google.android.material.card.MaterialCardView
                    android:layout_width="0dp" android:layout_height="wrap_content"
                    android:layout_weight="1" android:layout_marginStart="6dp"
                    app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
                    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:orientation="vertical" android:padding="16dp">
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="YouTube" android:textSize="11sp" android:textColor="@color/text_muted"/>
                        <TextView android:id="@+id/tv_screen_time" android:layout_width="wrap_content"
                            android:layout_height="wrap_content" android:text="0m" android:textSize="28sp"
                            android:textStyle="bold" android:textColor="@color/amber"/>
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="today" android:textSize="11sp" android:textColor="@color/text_muted"/>
                    </LinearLayout>
                </com.google.android.material.card.MaterialCardView>
            </LinearLayout>
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="18dp" app:strokeWidth="0dp">
                <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                    android:orientation="vertical" android:padding="18dp">
                    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginBottom="8dp">
                        <TextView android:layout_width="0dp" android:layout_height="wrap_content"
                            android:layout_weight="1" android:text="AI Filter Strictness"
                            android:textSize="14sp" android:textStyle="bold" android:textColor="@color/white"/>
                        <TextView android:id="@+id/tv_threshold_value" android:layout_width="wrap_content"
                            android:layout_height="wrap_content" android:text="75" android:textSize="22sp"
                            android:textStyle="bold" android:textColor="@color/safe_green"/>
                    </LinearLayout>
                    <SeekBar android:id="@+id/seek_threshold" android:layout_width="match_parent"
                        android:layout_height="wrap_content" android:max="95" android:progress="75"
                        android:progressTint="@color/safe_green" android:thumbTint="@color/safe_green"/>
                    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:orientation="horizontal" android:layout_marginTop="4dp">
                        <TextView android:layout_width="0dp" android:layout_height="wrap_content"
                            android:layout_weight="1" android:text="Relaxed"
                            android:textSize="10sp" android:textColor="@color/text_muted"/>
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                            android:text="Strict" android:textSize="10sp" android:textColor="@color/text_muted"/>
                    </LinearLayout>
                    <TextView android:id="@+id/tv_threshold_label" android:layout_width="match_parent"
                        android:layout_height="wrap_content" android:layout_marginTop="12dp"
                        android:background="@drawable/bg_pill_green" android:padding="10dp"
                        android:text="Balanced" android:textSize="12sp"
                        android:textStyle="bold" android:textColor="@color/safe_green"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginBottom="8dp">
                <TextView android:layout_width="0dp" android:layout_height="wrap_content"
                    android:layout_weight="1" android:text="Block History"
                    android:textSize="15sp" android:textStyle="bold" android:textColor="@color/white"/>
                <com.google.android.material.button.MaterialButton android:id="@+id/btn_clear_log"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:text="Clear" android:textColor="@color/text_muted" android:textSize="12sp"/>
            </LinearLayout>
            <androidx.recyclerview.widget.RecyclerView android:id="@+id/rv_block_log"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:nestedScrollingEnabled="false"/>
            <TextView android:id="@+id/tv_empty_log" android:layout_width="match_parent"
                android:layout_height="wrap_content" android:text="No blocks yet. SafeStream is watching."
                android:textSize="13sp" android:textColor="@color/text_muted"
                android:textAlignment="center" android:padding="24dp" android:visibility="gone"/>
            <com.google.android.material.button.MaterialButton android:id="@+id/btn_settings"
                android:layout_width="match_parent" android:layout_height="56dp"
                android:layout_marginTop="8dp" android:layout_marginBottom="24dp"
                android:text="Settings" app:cornerRadius="14dp"/>
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
""")

write("app/src/main/res/layout/activity_block_overlay.xml", """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:gravity="center"
    android:padding="32dp" android:background="@color/block_overlay_bg">
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="Video Blocked" android:textSize="28sp"
        android:textStyle="bold" android:textColor="@color/white" android:layout_marginBottom="10dp"/>
    <TextView android:id="@+id/tv_blocked_title" android:layout_width="match_parent"
        android:layout_height="wrap_content" android:textSize="15sp"
        android:textColor="@color/white" android:textAlignment="center" android:layout_marginBottom="6dp"/>
    <com.google.android.material.card.MaterialCardView android:layout_width="match_parent"
        android:layout_height="wrap_content" android:layout_marginTop="8dp" android:layout_marginBottom="24dp"
        app:cardBackgroundColor="#33FFFFFF" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" android:padding="16dp">
            <TextView android:id="@+id/tv_blocked_reason" android:layout_width="match_parent"
                android:layout_height="wrap_content" android:textSize="13sp"
                android:textColor="@color/white" android:textAlignment="center"/>
            <TextView android:id="@+id/tv_blocked_score" android:layout_width="match_parent"
                android:layout_height="wrap_content" android:textSize="12sp"
                android:textColor="@color/white" android:textAlignment="center"
                android:alpha="0.6" android:layout_marginTop="6dp"/>
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
    <com.google.android.material.button.MaterialButton android:id="@+id/btn_got_it"
        android:layout_width="match_parent" android:layout_height="56dp"
        android:layout_marginBottom="12dp" android:text="Got it"
        android:textSize="16sp" android:textStyle="bold" android:textColor="@color/block_overlay_bg"
        app:backgroundTint="@color/white" app:cornerRadius="16dp"/>
    <com.google.android.material.button.MaterialButton android:id="@+id/btn_safe_videos"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent" android:layout_height="56dp"
        android:text="Try YouTube Kids" android:textSize="14sp" android:textColor="@color/white"
        app:strokeColor="@color/white" app:strokeWidth="1dp" app:cornerRadius="16dp"/>
    <TextView android:id="@+id/tv_countdown" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:text="Auto-closing in 6s"
        android:textSize="12sp" android:textColor="@color/white"
        android:alpha="0.5" android:layout_marginTop="20dp"/>
</LinearLayout>
""")

write("app/src/main/res/layout/activity_settings.xml", """<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@color/background_dark" android:padding="16dp" android:clipToPadding="false">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical">
        <com.google.android.material.card.MaterialCardView android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginBottom="16dp" android:layout_marginTop="8dp"
            app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="vertical" android:padding="16dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:text="Anthropic API Key" android:textSize="13sp" android:textStyle="bold"
                    android:textColor="@color/white" android:layout_marginBottom="4dp"/>
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:text="Get yours at console.anthropic.com"
                    android:textSize="11sp" android:textColor="@color/text_muted" android:layout_marginBottom="10dp"/>
                <com.google.android.material.textfield.TextInputLayout android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:hint="sk-ant-api03-..."
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    app:boxStrokeColor="@color/safe_green" app:hintTextColor="@color/text_muted">
                    <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_api_key"
                        android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:inputType="textPassword" android:textColor="@color/white"/>
                </com.google.android.material.textfield.TextInputLayout>
                <com.google.android.material.button.MaterialButton android:id="@+id/btn_save_api_key"
                    android:layout_width="match_parent" android:layout_height="48dp"
                    android:layout_marginTop="10dp" android:text="Save API Key" app:cornerRadius="12dp"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
        <com.google.android.material.card.MaterialCardView android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginBottom="16dp"
            app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="vertical" android:padding="16dp">
                <com.google.android.material.textfield.TextInputLayout android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:hint="Child name"
                    android:layout_marginBottom="10dp"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    app:boxStrokeColor="@color/safe_green" app:hintTextColor="@color/text_muted">
                    <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_child_name"
                        android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:inputType="textPersonName" android:textColor="@color/white"/>
                </com.google.android.material.textfield.TextInputLayout>
                <com.google.android.material.button.MaterialButton android:id="@+id/btn_save_name"
                    android:layout_width="match_parent" android:layout_height="48dp"
                    android:text="Save Name" app:cornerRadius="12dp"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
        <com.google.android.material.card.MaterialCardView android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginBottom="16dp"
            app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="vertical" android:padding="16dp">
                <com.google.android.material.textfield.TextInputLayout android:layout_width="match_parent"
                    android:layout_height="wrap_content" android:hint="Daily limit (minutes)"
                    android:layout_marginBottom="10dp"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
                    app:boxStrokeColor="@color/amber" app:hintTextColor="@color/text_muted">
                    <com.google.android.material.textfield.TextInputEditText android:id="@+id/et_time_limit"
                        android:layout_width="match_parent" android:layout_height="wrap_content"
                        android:inputType="number" android:textColor="@color/white"/>
                </com.google.android.material.textfield.TextInputLayout>
                <com.google.android.material.button.MaterialButton android:id="@+id/btn_save_time"
                    android:layout_width="match_parent" android:layout_height="48dp"
                    android:text="Save Limit" app:cornerRadius="12dp" app:backgroundTint="@color/amber"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
        <com.google.android.material.card.MaterialCardView android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginBottom="32dp"
            app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="16dp" app:strokeWidth="0dp">
            <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:orientation="vertical" android:padding="16dp">
                <com.google.android.material.button.MaterialButton android:id="@+id/btn_clear_log"
                    android:layout_width="match_parent" android:layout_height="48dp"
                    android:text="Clear Block Log" app:cornerRadius="12dp" app:backgroundTint="@color/danger_red"/>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
""")

write("app/src/main/res/layout/item_block_log.xml", """<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardBackgroundColor="@color/surface_dark" app:cardCornerRadius="12dp"
    app:strokeWidth="1dp" app:strokeColor="@color/surface_border">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="12dp">
        <TextView android:id="@+id/tv_log_title" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textColor="@color/white"
            android:textStyle="bold" android:maxLines="2" android:ellipsize="end"/>
        <TextView android:id="@+id/tv_log_channel" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textColor="@color/text_muted"
            android:textSize="12sp" android:layout_marginTop="2dp"/>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:layout_marginTop="4dp">
            <TextView android:id="@+id/tv_log_reason" android:layout_width="0dp"
                android:layout_height="wrap_content" android:layout_weight="1"
                android:textColor="@color/danger_red" android:textSize="11sp"
                android:maxLines="1" android:ellipsize="end"/>
            <TextView android:id="@+id/tv_log_score" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:textColor="@color/safe_green" android:textSize="11sp"/>
            <TextView android:id="@+id/tv_log_time" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:layout_marginStart="8dp"
                android:textColor="@color/text_muted" android:textSize="11sp"/>
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
""")

# Kotlin source files
write("app/src/main/java/com/safestream/ai/BootReceiver.kt", """package com.safestream.ai
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED)
            Log.i("SafeStream", "Device booted")
    }
}
""")

write("app/src/main/java/com/safestream/ai/BlockEventLogger.kt", """package com.safestream.ai
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
object BlockEventLogger {
    private const val PREFS = "safestream_block_log"
    private const val KEY = "events"
    private const val MAX = 200
    private val FMT = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
    fun log(ctx: Context, ev: BlockEvent) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = load(p)
        val entry = JSONObject().apply {
            put("title", ev.title); put("channel", ev.channel)
            put("score", ev.score); put("reason", ev.reason)
            put("timestamp", ev.timestamp)
            put("time_str", FMT.format(Date(ev.timestamp)))
        }
        val updated = JSONArray().also { arr ->
            arr.put(entry)
            for (i in 0 until minOf(old.length(), MAX - 1)) arr.put(old.getJSONObject(i))
        }
        p.edit().putString(KEY, updated.toString()).apply()
    }
    fun getAll(ctx: Context): List<Map<String, Any>> {
        val arr = load(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            mapOf("title" to o.optString("title"), "channel" to o.optString("channel"),
                "score" to o.optInt("score"), "reason" to o.optString("reason"),
                "timestamp" to o.optLong("timestamp"), "time_str" to o.optString("time_str"))
        }
    }
    fun clear(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    private fun load(p: SharedPreferences): JSONArray =
        try { JSONArray(p.getString(KEY, null) ?: return JSONArray()) } catch (_: Exception) { JSONArray() }
}
""")

write("app/src/main/java/com/safestream/ai/ScreenTimeTracker.kt", """package com.safestream.ai
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar
object ScreenTimeTracker {
    private const val YT = "com.google.android.youtube"
    fun getYouTubeMinutesToday(ctx: Context): Int {
        if (!hasPermission(ctx)) return -1
        val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val stats = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
        val ms = stats?.filter { it.packageName == YT }?.sumOf { it.totalTimeInForeground } ?: 0L
        return (ms / 1000 / 60).toInt()
    }
    fun hasPermission(ctx: Context): Boolean {
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun openPermissionSettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    fun formatMinutes(mins: Int) = when { mins < 0 -> "?" ; mins < 60 -> "${mins}m" ; else -> "${mins/60}h ${mins%60}m" }
}
""")

write("app/src/main/java/com/safestream/ai/BlockOverlayActivity.kt", """package com.safestream.ai
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
class BlockOverlayActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val AUTO = 6_000L
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setContentView(R.layout.activity_block_overlay)
        val title = intent.getStringExtra("title") ?: "This video"
        val score = intent.getIntExtra("score", 0)
        val reason = intent.getStringExtra("reason") ?: "Not suitable for kids"
        findViewById<TextView>(R.id.tv_blocked_title).text = "\"${'$'}title\" was blocked"
        findViewById<TextView>(R.id.tv_blocked_reason).text = reason
        findViewById<TextView>(R.id.tv_blocked_score).text = "Safety score: ${'$'}score / 100"
        findViewById<Button>(R.id.btn_got_it).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_safe_videos).setOnClickListener {
            val ytk = packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.kids") ?: Intent(this, MainActivity::class.java)
            startActivity(ytk); finish()
        }
        h.postDelayed({ finish() }, AUTO)
        var n = (AUTO / 1000).toInt()
        val tv = findViewById<TextView>(R.id.tv_countdown)
        val r = object : Runnable { override fun run() { if (n <= 0) return; tv.text = "Auto-closing in ${n}s"; n--; h.postDelayed(this, 1000) } }
        h.post(r)
    }
    override fun onDestroy() { super.onDestroy(); h.removeCallbacksAndMessages(null) }
    @Deprecated("") override fun onBackPressed() {}
}
""")

write("app/src/main/java/com/safestream/ai/SettingsActivity.kt", """package com.safestream.ai
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE) }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val etKey = findViewById<TextInputEditText>(R.id.et_api_key)
        val etName = findViewById<TextInputEditText>(R.id.et_child_name)
        val etTime = findViewById<TextInputEditText>(R.id.et_time_limit)
        etKey.setText(prefs.getString("claude_api_key", ""))
        etName.setText(prefs.getString("child_name", "Kiddo"))
        etTime.setText(prefs.getInt("time_limit_min", 60).toString())
        findViewById<MaterialButton>(R.id.btn_save_api_key).setOnClickListener { prefs.edit().putString("claude_api_key", etKey.text.toString().trim()).apply(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        findViewById<MaterialButton>(R.id.btn_save_name).setOnClickListener { prefs.edit().putString("child_name", etName.text.toString().trim()).apply(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        findViewById<MaterialButton>(R.id.btn_save_time).setOnClickListener { prefs.edit().putInt("time_limit_min", etTime.text.toString().toIntOrNull() ?: 60).apply(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        findViewById<MaterialButton>(R.id.btn_clear_log).setOnClickListener { BlockEventLogger.clear(this); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show() }
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
""")

write("app/src/main/java/com/safestream/ai/SafeAccessibilityService.kt", open("/dev/stdin").read() if False else """package com.safestream.ai
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
data class SafetyResult(val score: Int, val verdict: String, val ageRating: String, val flags: List<String>, val summary: String, val recommendation: String)
data class BlockEvent(val title: String, val channel: String, val score: Int, val reason: String, val timestamp: Long = System.currentTimeMillis())
private const val TAG = "SafeStreamAI"
private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
private val HARD_BLOCK = listOf("scary prank","prank on sister","prank on brother","extreme challenge","gone wrong","out of control","needle injection","dead bugs","gross challenge","killing","murder","horror","demon","stabbing","shooting")
private val BLOCKED_CHANNELS = setOf("KidsSuper777","GrossKidz","FamilyFunPacks","DadReacts")
class SafeAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private var lastTitle = ""; private var lastTime = 0L
    private val titleCache = mutableMapOf<String, SafetyResult>()
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(YOUTUBE_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        sendBroadcast(Intent("com.safestream.STATUS").putExtra("status", "RUNNING"))
    }
    override fun onDestroy() { super.onDestroy(); scope.cancel(); sendBroadcast(Intent("com.safestream.STATUS").putExtra("status", "STOPPED")) }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != YOUTUBE_PACKAGE) return
        val root = rootInActiveWindow ?: return
        val title = listOf("$YOUTUBE_PACKAGE:id/title","$YOUTUBE_PACKAGE:id/reel_title","$YOUTUBE_PACKAGE:id/mini_title")
            .flatMap { root.findAccessibilityNodeInfosByViewId(it) }
            .firstOrNull()?.text?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return
        val channel = root.findAccessibilityNodeInfosByViewId("$YOUTUBE_PACKAGE:id/channel_name").firstOrNull()?.text?.toString()?.trim() ?: "Unknown"
        val now = System.currentTimeMillis()
        if (title == lastTitle && now - lastTime < 5000) return
        lastTitle = title; lastTime = now
        sendBroadcast(Intent("com.safestream.DETECTION").putExtra("title", title).putExtra("channel", channel))
        if (BLOCKED_CHANNELS.any { channel.contains(it, ignoreCase = true) }) { block(title, channel, 0, "Channel blocked"); return }
        if (HARD_BLOCK.any { title.lowercase().contains(it) }) { block(title, channel, 5, "Blocked keyword"); return }
        titleCache[title]?.let { if (it.score < threshold() || it.verdict == "BLOCKED") block(title, channel, it.score, it.flags.firstOrNull() ?: "Previously blocked"); return }
        scope.launch {
            try {
                val key = getSharedPreferences("safestream_prefs", MODE_PRIVATE).getString("claude_api_key", "") ?: ""
                if (key.isBlank()) throw IOException("No API key")
                val prompt = "Child safety moderator for kids 2-12.\\nTitle: ${'$'}title\\nChannel: ${'$'}channel\\nReturn ONLY JSON: {\\\"safetyScore\\\":<0-100>,\\\"verdict\\\":\\\"SAFE\\\"|\\\"REVIEW\\\"|\\\"BLOCKED\\\",\\\"ageRating\\\":\\\"All Ages\\\",\\\"flags\\\":[],\\\"summary\\\":\\\"1 sentence\\\",\\\"recommendation\\\":\\\"1 sentence\\\"}"
                val body = JSONObject().apply { put("model","claude-sonnet-4-20250514"); put("max_tokens",400); put("messages", JSONArray().put(JSONObject().apply { put("role","user"); put("content",prompt) })) }.toString().toRequestBody("application/json".toMediaType())
                val resp = http.newCall(Request.Builder().url("https://api.anthropic.com/v1/messages").addHeader("x-api-key",key).addHeader("anthropic-version","2023-06-01").post(body).build()).execute()
                val raw = resp.body?.string() ?: throw IOException("Empty")
                if (!resp.isSuccessful) throw IOException("HTTP ${'$'}{resp.code}")
                val text = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text").replace("```json","").replace("```","").trim()
                val json = JSONObject(text)
                val flags = mutableListOf<String>().also { list -> json.optJSONArray("flags")?.let { for (i in 0 until it.length()) list.add(it.getString(i)) } }
                val result = SafetyResult(json.optInt("safetyScore",50),json.optString("verdict","REVIEW"),json.optString("ageRating","?"),flags,json.optString("summary",""),json.optString("recommendation",""))
                titleCache[title] = result
                if (result.score < threshold() || result.verdict == "BLOCKED") block(title, channel, result.score, result.flags.firstOrNull() ?: result.summary)
            } catch (e: Exception) { Log.w(TAG, "Claude failed: ${'$'}{e.message}"); block(title, channel, 0, "AI unavailable") }
        }
    }
    private fun block(title: String, channel: String, score: Int, reason: String) {
        mainHandler.post {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Toast.makeText(this, "SafeStream: Blocked", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, BlockOverlayActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK; putExtra("title",title); putExtra("channel",channel); putExtra("score",score); putExtra("reason",reason) })
        }
        BlockEventLogger.log(this, BlockEvent(title,channel,score,reason))
        sendBroadcast(Intent("com.safestream.BLOCK").apply { putExtra("title",title); putExtra("channel",channel); putExtra("score",score); putExtra("reason",reason); putExtra("timestamp",System.currentTimeMillis()) })
    }
    private fun threshold() = getSharedPreferences("safestream_prefs",MODE_PRIVATE).getInt("threshold",75)
}
""")

write("app/src/main/java/com/safestream/ai/MainActivity.kt", """package com.safestream.ai
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter
import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.provider.Settings; import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager; import android.widget.*
import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity; import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager; import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView; private lateinit var tvLast: TextView; private lateinit var tvCount: TextView
    private lateinit var tvTime: TextView; private lateinit var tvThreshVal: TextView; private lateinit var tvThreshLbl: TextView
    private lateinit var dot: View; private lateinit var btnToggle: MaterialButton; private lateinit var btnSettings: MaterialButton
    private lateinit var btnClear: MaterialButton; private lateinit var seek: SeekBar; private lateinit var rv: RecyclerView; private lateinit var tvEmpty: TextView
    private val prefs by lazy { getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val timeRunner = object : Runnable { override fun run() { refreshTime(); handler.postDelayed(this, 60_000) } }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                "com.safestream.STATUS" -> updateStatus(intent.getStringExtra("status") == "RUNNING")
                "com.safestream.BLOCK" -> { refreshLog(); tvLast.text = "Last: ${'$'}{intent.getStringExtra("title") ?: ""}" }
            }
        }
    }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); setContentView(R.layout.activity_main); setSupportActionBar(findViewById(R.id.toolbar))
        tvStatus=findViewById(R.id.tv_service_status); tvLast=findViewById(R.id.tv_last_blocked); tvCount=findViewById(R.id.tv_block_count)
        tvTime=findViewById(R.id.tv_screen_time); tvThreshVal=findViewById(R.id.tv_threshold_value); tvThreshLbl=findViewById(R.id.tv_threshold_label)
        dot=findViewById(R.id.view_status_dot); btnToggle=findViewById(R.id.btn_toggle_service); btnSettings=findViewById(R.id.btn_settings)
        btnClear=findViewById(R.id.btn_clear_log); seek=findViewById(R.id.seek_threshold); rv=findViewById(R.id.rv_block_log); tvEmpty=findViewById(R.id.tv_empty_log)
        rv.layoutManager=LinearLayoutManager(this)
        seek.progress=prefs.getInt("threshold",75); applyLabel(seek.progress)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(sb:SeekBar,v:Int,f:Boolean){applyLabel(v)}
            override fun onStartTrackingTouch(sb:SeekBar){}
            override fun onStopTrackingTouch(sb:SeekBar){prefs.edit().putInt("threshold",sb.progress).apply(); Toast.makeText(this@MainActivity,getString(R.string.toast_threshold_updated,sb.progress),Toast.LENGTH_SHORT).show()}
        })
        btnToggle.setOnClickListener{if(isEnabled())showDisable() else showEnable()}
        btnSettings.setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))}
        btnClear.setOnClickListener{BlockEventLogger.clear(this);refreshLog();Toast.makeText(this,getString(R.string.toast_log_cleared),Toast.LENGTH_SHORT).show()}
        refreshLog(); refreshTime(); updateStatus(isEnabled())
        val f=IntentFilter().apply{addAction("com.safestream.STATUS");addAction("com.safestream.BLOCK")}
        ContextCompat.registerReceiver(this,receiver,f,ContextCompat.RECEIVER_NOT_EXPORTED)
        if(!isEnabled()) showEnable()
        if(!ScreenTimeTracker.hasPermission(this)) AlertDialog.Builder(this).setTitle("Screen Time").setMessage("Enable Usage Access for SafeStream AI in Settings.").setPositiveButton("Open"){_,_->ScreenTimeTracker.openPermissionSettings(this)}.setNegativeButton("Skip",null).show()
    }
    override fun onResume(){super.onResume();updateStatus(isEnabled());refreshLog();handler.post(timeRunner)}
    override fun onPause(){super.onPause();handler.removeCallbacks(timeRunner)}
    override fun onDestroy(){super.onDestroy();try{unregisterReceiver(receiver)}catch(_:Exception){}}
    private fun applyLabel(v:Int){tvThreshVal.text="${'$'}v";tvThreshLbl.text=when{v>=85->"Strict";v>=65->"Balanced";else->"Relaxed"}}
    private fun refreshLog(){val ev=BlockEventLogger.getAll(this);tvCount.text="${'$'}{ev.size}";if(ev.isEmpty()){rv.visibility=View.GONE;tvEmpty.visibility=View.VISIBLE}else{rv.visibility=View.VISIBLE;tvEmpty.visibility=View.GONE;rv.adapter=BlockLogAdapter(ev)}}
    private fun refreshTime(){val m=ScreenTimeTracker.getYouTubeMinutesToday(this);val limit=prefs.getInt("time_limit_min",60);tvTime.text=ScreenTimeTracker.formatMinutes(m);tvTime.setTextColor(ContextCompat.getColor(this,if(m>=0&&m>=limit)R.color.danger_red else R.color.amber))}
    private fun updateStatus(running:Boolean){tvStatus.text=getString(if(running)R.string.status_active else R.string.status_inactive);tvStatus.setTextColor(ContextCompat.getColor(this,if(running)R.color.safe_green else R.color.danger_red));dot.setBackgroundResource(if(running)R.drawable.circle_dot_green else R.drawable.circle_dot_red);btnToggle.text=if(running)"Disable" else "Enable"}
    private fun isEnabled():Boolean{val am=getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager;return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.resolveInfo.serviceInfo.packageName==packageName&&it.resolveInfo.serviceInfo.name==SafeAccessibilityService::class.java.name}}
    private fun showEnable(){AlertDialog.Builder(this).setTitle(getString(R.string.dialog_enable_title)).setMessage("1. Tap SafeStream AI\\n2. Toggle ON\\n3. Tap Allow").setPositiveButton("Open Settings"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("Not Now",null).show()}
    private fun showDisable(){AlertDialog.Builder(this).setTitle(getString(R.string.dialog_disable_title)).setMessage(getString(R.string.dialog_disable_message)).setPositiveButton("Open Settings"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("Cancel",null).show()}
}
class BlockLogAdapter(private val ev:List<Map<String,Any>>):RecyclerView.Adapter<BlockLogAdapter.VH>(){
    class VH(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.tv_log_title);val channel:TextView=v.findViewById(R.id.tv_log_channel);val score:TextView=v.findViewById(R.id.tv_log_score);val time:TextView=v.findViewById(R.id.tv_log_time);val reason:TextView=v.findViewById(R.id.tv_log_reason)}
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=VH(LayoutInflater.from(p.context).inflate(R.layout.item_block_log,p,false))
    override fun getItemCount()=ev.size
    override fun onBindViewHolder(h:VH,i:Int){val e=ev[i];h.title.text=e["title"] as? String?:"";h.channel.text=e["channel"] as? String?:"";h.score.text="Score: ${'$'}{e["score"]}";h.time.text=e["time_str"] as? String?:"";h.reason.text=e["reason"] as? String?:""}
}
""")

print("All files created successfully!")
