package com.cylonid.nativealpha;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebSettings;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.cylonid.nativealpha.browser.ReaderTranslation;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import static org.junit.Assert.assertTrue;

/** Explicit live comparison with Selah Translate c2da6ef; synthetic text only. */
@RunWith(AndroidJUnit4.class)
public class GoogleTranslationTransportDeviceTest {
    @Test public void compareNativeAndSelahGoogleRequests() throws Exception {
        String userAgent = WebSettings.getDefaultUserAgent(InstrumentationRegistry.getInstrumentation().getTargetContext());
        boolean success = false;
        String profiles = InstrumentationRegistry.getArguments().getString("profiles",
                "native,selah-params,selah-browser,browser-agent");
        for (String profile : profiles.split(",")) {
            String url = ReaderTranslation.requestUrl("Reading should be comfortable.", "zh-CN");
            if (profile.startsWith("selah")) url += "&strip=1&nonced=1";
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            try {
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                if (profile.equals("native") || profile.equals("selah-params") || profile.endsWith("json"))
                    connection.setRequestProperty("Accept", "application/json");
                if (profile.contains("browser")) connection.setRequestProperty("User-Agent", userAgent);
                int status = connection.getResponseCode();
                boolean translated = false;
                if (status == 200) {
                    try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[2048];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > 65536) throw new java.io.IOException("Response too large");
                            bytes.write(buffer, 0, count);
                        }
                        translated = !ReaderTranslation.parse(bytes.toString("UTF-8")).equals("Reading should be comfortable.");
                    }
                }
                String location = connection.getHeaderField("Location");
                String destination = location == null ? "none" : Uri.parse(location).getHost();
                Log.i("ReaderTransportProbe", profile + " status=" + status + " redirectHost=" + destination + " translated=" + translated);
                success |= translated;
            } catch (Exception error) {
                Log.i("ReaderTransportProbe", profile + " error=" + error.getClass().getSimpleName());
            } finally { connection.disconnect(); }
        }
        assertTrue("At least one direct Google request profile must return a translation", success);
    }
}
